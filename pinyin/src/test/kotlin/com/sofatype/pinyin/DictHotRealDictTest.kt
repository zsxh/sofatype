package com.sofatype.pinyin

import com.sofatype.pinyin.tools.HotDictBuild
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictHotRealDictTest {

    private fun loadRealData(): DictData =
        DictHotRealDictTest::class.java.getResourceAsStream("/dict/pinyin_dict.bin")
            ?.use { DictFormat.read(it) }
            ?: throw AssertionError("词库产物不存在（classpath /dict/pinyin_dict.bin）。先运行：./gradlew :pinyin:buildDict")

    private fun loadRealHot(): DictData =
        DictHotRealDictTest::class.java.getResourceAsStream("/dict/hotwords.bin")
            ?.use { DictFormat.read(it) }
            ?: throw AssertionError("热词产物不存在（classpath /dict/hotwords.bin）。先运行：./gradlew :pinyin:buildHotDict")

    private fun sampleHot(entries: Int): DictData {
        val syllables = mutableListOf<String>()
        val words = mutableListOf<String>()
        val hotEntries = mutableListOf<DictData.Entry>()
        var serial = 0
        while (hotEntries.size < entries) {
            serial++
            val sylTexts = List(2 + (serial % 3)) { i -> "z" + "aeiou"[(serial + i) % 5] }
            val word = serial.toString() + "热词测"
            for (s in sylTexts) {
                if (s !in syllables) syllables.add(s)
            }
            words.add(word)
            hotEntries.add(
                DictData.Entry(
                    sylTexts.map { syllables.indexOf(it) }.toIntArray(),
                    words.size - 1,
                    100.0 + serial,
                ),
            )
        }
        return DictData(syllables, words, hotEntries)
    }

    @Test
    fun `真实主库加热词合并后_热词命中主库词序不受损`() {
        val real = loadRealData()
        val merged = DictHotMerge.mergeBaseWithHot(real, listOf(sampleHot(entries = 2000)))
        val engine = PinyinEngine.fromData(merged)

        assertEquals("合并后词条数 = 主库 + 热词去重后", real.entryCount + 2000, engine.entryCount)
        assertEquals("合并后主库高频词「这个」仍居 zg 前列", "这个", engine.query("zg", limit = 5).firstOrNull()?.word)
        assertTrue("合并后主库「你好」仍可查", "你好" in engine.query("nihao", limit = 50).map { it.word })
    }

    @Test
    fun `真实热词包分档写入绝对频次且不含baidu-realtime词条`() {
        val hot = loadRealHot()

        val shortFreqs = hot.entries.map { it.freq }.filterIndexed { i, _ -> hot.words[hot.entries[i].wordId].length <= 3 }.toSet()
        val longFreqs = hot.entries.map { it.freq }.filterIndexed { i, _ -> hot.words[hot.entries[i].wordId].length >= 4 }.toSet()
        assertEquals("2~3 字档应同频次", setOf(HotDictBuild.SHORT_WORD_FREQ), shortFreqs)
        assertEquals("≥4 字档应同频次", setOf(HotDictBuild.LONG_WORD_FREQ), longFreqs)
        assertTrue("长尾档应低于短词档", HotDictBuild.LONG_WORD_FREQ < HotDictBuild.SHORT_WORD_FREQ)

        val hotWords = hot.words.toSet()
        for (realtimeWord in listOf("后劲太大", "金价跌势凌厉", "成功的会晤", "多地为何鼓励")) {
            assertTrue("baidu-realtime 词条不应出现在热词包：$realtimeWord", realtimeWord !in hotWords)
        }
    }

    @Test
    fun `真实词库合并后简拼排序_常用词回前排且长尾词不压常用词`() {
        val engine = PinyinEngine.fromData(
            DictHotMerge.mergeBaseWithHot(loadRealData(), listOf(loadRealHot())),
        )

        val dz = engine.query("dz", limit = 20).map { it.word }
        assertEquals("dz 首位应为常用词「地址」", "地址", dz.first())
        assertTrue("dz 前排应含「电子」", dz.indexOf("电子") in 1..9)
        assertTrue("dz 前排应含「动作」", dz.indexOf("动作") in 1..9)
        for (tail in listOf("侗族文化", "贵州从江警方")) {
            val pos = dz.indexOf(tail)
            if (pos >= 0) {
                assertTrue("长尾词「$tail」不得排在常用词之前", pos > dz.indexOf("动作"))
            }
        }

        val ll = engine.query("ll", limit = 20).map { it.word }
        assertEquals("ll 首位应为常用词「来了」", "来了", ll.first())
        assertTrue("ll 前排应含「理论」「力量」", ll.indexOf("理论") in 1..4 && ll.indexOf("力量") in 1..4)
        val lly = ll.indexOf("李勒优")
        if (lly >= 0) assertTrue("长尾人名「李勒优」不得排在常用词之前", lly > ll.indexOf("力量"))

        val jx = engine.query("jx", limit = 20).map { it.word }
        assertEquals("jx 首位应为常用词「进行」", "进行", jx.first())
        assertTrue("jx 前排应含「继续」", jx.indexOf("继续") in 1..2)
        val jxm = jx.indexOf("江小妹")
        if (jxm >= 0) assertTrue("长尾人名「江小妹」不得排在常用词之前", jxm > jx.indexOf("继续"))

        val dazi = engine.query("dazi", limit = 20).map { it.word }
        assertEquals("dazi 首位应为主库高频词「打字」", "打字", dazi.first())
        assertTrue("短热词「搭子」应在「打字」附近（第 2~3 位）", dazi.indexOf("搭子") in 1..3)

        assertTrue("xyb 简拼应保留「显眼包」可命中（前 5 位）", engine.query("xyb", limit = 5).map { it.word }.contains("显眼包"))

        assertEquals("全拼 xianyanbao 仍应命中「显眼包」", "显眼包", engine.query("xianyanbao", limit = 5).firstOrNull()?.word)
        assertEquals("全拼 zundujiadu 仍应命中「尊嘟假嘟」", "尊嘟假嘟", engine.query("zundujiadu", limit = 5).firstOrNull()?.word)
        assertEquals("全拼 dongzuwenhua 仍应命中「侗族文化」", "侗族文化", engine.query("dongzuwenhua", limit = 5).firstOrNull()?.word)
    }

    @Test
    fun `合并引擎查询性能量级佐证_单次查询不超过200毫秒`() {
        val real = loadRealData()
        val engine = PinyinEngine.fromData(
            DictHotMerge.mergeBaseWithHot(real, listOf(sampleHot(entries = 2000))),
        )
        val inputs = listOf(
            "k", "ke", "keti", "keting", "nihao", "xian", "zhongwen", "z", "zg", "nh",
            "zguo", "zhly", "zhli", "srf", "xianyanbao", "zundujiadu", "dazi",
        )

        repeat(30) { engine.query(inputs[it % inputs.size]) }

        var maxMs = 0.0
        var totalMs = 0.0
        var count = 0
        repeat(50) {
            for (input in inputs) {
                val t0 = System.nanoTime()
                engine.query(input)
                val ms = (System.nanoTime() - t0) / 1_000_000.0
                maxMs = maxOf(maxMs, ms)
                totalMs += ms
                count++
            }
        }
        println("热词合并引擎查询 ${count} 次，平均 ${"%.3f".format(totalMs / count)} ms，最大 ${"%.3f".format(maxMs)} ms")
        assertTrue("单次查询最大耗时 ${"%.3f".format(maxMs)} ms 超出 200 ms", maxMs < 200.0)
    }
}
