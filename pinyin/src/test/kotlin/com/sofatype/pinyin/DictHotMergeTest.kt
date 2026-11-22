package com.sofatype.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DictHotMergeTest {

    private fun buildData(syllables: List<String>, words: List<Triple<String, List<String>, Double>>): DictData {
        val wordList = words.map { it.first }
        val entries = words.map { (word, syls, freq) ->
            DictData.Entry(syls.map { syllables.indexOf(it) }.toIntArray(), wordList.indexOf(word), freq)
        }
        return DictData(syllables, wordList, entries)
    }

    private fun spd1(data: DictData): ByteArray =
        ByteArrayOutputStream().also { DictFormat.write(data, it) }.toByteArray()

    private fun base(): DictData = buildData(
        syllables = listOf("da", "zi", "xian", "yan", "bao"),
        words = listOf(
            Triple("打字", listOf("da", "zi"), 190.0),
            Triple("大字", listOf("da", "zi"), 20.0),
        ),
    )

    private fun hot(words: List<Triple<String, List<String>, Double>>): DictData = buildData(
        syllables = listOf("da", "zi", "xian", "yan", "bao", "tai", "ku", "la", "zun", "du", "jia"),
        words = words,
    )

    @Test
    fun `热词经SPD1读写后与主库合并_整拼简拼混合缩写均命中热词`() {
        val hotBytes = spd1(
            hot(
                listOf(
                    Triple("显眼包", listOf("xian", "yan", "bao"), 5000.0),
                    Triple("搭子", listOf("da", "zi"), 3000.0),
                ),
            ),
        )
        val hotDict = DictFormat.read(ByteArrayInputStream(hotBytes))
        val engine = PinyinEngine.fromData(DictHotMerge.mergeBaseWithHot(base(), listOf(hotDict)))

        assertEquals("整拼应命中热词居首", "显眼包", engine.query("xianyanbao", limit = 5).firstOrNull()?.word)
        assertEquals("整拼加权后热词应排在主库高频词前", "搭子", engine.query("dazi", limit = 5).firstOrNull()?.word)
        assertTrue("简拼 dz 应命中热词「搭子」", "搭子" in engine.query("dz", limit = 10).map { it.word })
        assertTrue("混合缩写 dazi 形态应命中「搭子」", "搭子" in engine.query("dazi", limit = 10).map { it.word })
        assertTrue("主库词「打字」在热词并入后仍在候选内", "打字" in engine.query("dazi", limit = 10).map { it.word })
    }

    @Test
    fun `空热词列表合并结果与主库完全一致`() {
        val baseData = base()
        val pure = PinyinEngine.fromData(baseData)
        val merged = DictHotMerge.mergeBaseWithHot(baseData, emptyList())
        assertTrue("空热词应原样返回主库数据", merged === baseData)

        val mergedEngine = PinyinEngine.fromData(merged)
        for (input in listOf("da", "dazi", "dz")) {
            assertEquals(
                "空热词查询结果应与现状一致：$input",
                pure.query(input, limit = 20).map { it.word },
                mergedEngine.query(input, limit = 20).map { it.word },
            )
        }
    }

    @Test
    fun `跨热词包同形词条去重_同词同音节只保留一条`() {
        val hotA = hot(listOf(Triple("显眼包", listOf("xian", "yan", "bao"), 5000.0)))
        val hotB = hot(listOf(Triple("显眼包", listOf("xian", "yan", "bao"), 6000.0)))
        val merged = DictHotMerge.mergeBaseWithHot(base(), listOf(hotA, hotB))

        assertEquals("跨包同形词条应去重为一条", 1, merged.entries.count { merged.words[it.wordId] == "显眼包" })

        val engine = PinyinEngine.fromData(merged)
        val words = engine.query("xianyanbao", limit = 20).map { it.word }
        assertEquals("候选中「显眼包」只出现一次", 1, words.count { it == "显眼包" })
    }

    @Test
    fun `跨热词包同词不同音节形态_查询层按词去重不重复出词`() {
        val hotA = hot(listOf(Triple("显眼包", listOf("xian", "yan", "bao"), 5000.0)))
        val syllablesC = listOf("xian", "yan", "bo")
        val hotC = DictData(
            syllablesC,
            listOf("显眼包"),
            listOf(DictData.Entry(intArrayOf(0, 1, 2), 0, 4000.0)),
        )
        val engine = PinyinEngine.fromData(DictHotMerge.mergeBaseWithHot(base(), listOf(hotA, hotC)))

        assertEquals("xianyanbao 整拼应命中「显眼包」", "显眼包", engine.query("xianyanbao", limit = 10).firstOrNull()?.word)
        val xybWords = engine.query("xyb", limit = 20).map { it.word }
        assertEquals("简拼 xyb 候选「显眼包」不重复出词", 1, xybWords.count { it == "显眼包" })
    }

    @Test
    fun `频次加权排序_热词词频乘系数后进入候选前列`() {
        val hotDict = hot(listOf(Triple("泰裤辣", listOf("tai", "ku", "la"), 100.0)))
        val engine = PinyinEngine.fromData(DictHotMerge.mergeBaseWithHot(base(), listOf(hotDict)))

        assertEquals("泰裤辣", engine.query("taikula", limit = 5).firstOrNull()?.word)
        assertEquals(
            "加权频次应等于原频 × 系数",
            100.0 * DictHotMerge.DEFAULT_HOT_FREQ_BOOST,
            engine.query("taikula", limit = 5).first().freq,
            1e-9,
        )
    }

    @Test
    fun `自定义加权系数生效且非法系数被拒`() {
        val hotDict = hot(listOf(Triple("显眼包", listOf("xian", "yan", "bao"), 10.0)))
        val boosted = DictHotMerge.mergeBaseWithHot(base(), listOf(hotDict), hotFreqBoost = 3.0)
        val engine = PinyinEngine.fromData(boosted)
        assertEquals(30.0, engine.query("xianyanbao").first().freq, 1e-9)

        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            try {
                DictHotMerge.mergeBaseWithHot(base(), listOf(hotDict), hotFreqBoost = bad)
                throw AssertionError("系数 $bad 应被拒绝")
            } catch (expected: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun `热词新音节并入音节表_主库既有词条不受影响`() {
        val hotDict = hot(listOf(Triple("尊嘟假嘟", listOf("zun", "du", "jia", "du"), 4000.0)))
        val merged = DictHotMerge.mergeBaseWithHot(base(), listOf(hotDict))

        assertEquals("音节表应并入热词新音节", base().syllables.size + 3, merged.syllables.size)
        assertTrue(merged.syllables.containsAll(listOf("zun", "du", "jia")))
        assertEquals("主库词表保持在前且不变", base().words, merged.words.take(base().words.size))
        val engine = PinyinEngine.fromData(merged)
        assertTrue("主库「打字」仍可查", "打字" in engine.query("dazi", limit = 50).map { it.word })
        assertTrue("主库「大字」仍可查", "大字" in engine.query("dazi", limit = 50).map { it.word })
        assertEquals("zun du jia du 应命中热词", "尊嘟假嘟", engine.query("zundujiadu", limit = 5).firstOrNull()?.word)
    }
}
