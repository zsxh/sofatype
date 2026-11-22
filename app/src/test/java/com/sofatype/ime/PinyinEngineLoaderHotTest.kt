package com.sofatype.ime

import com.sofatype.pinyin.DictData
import com.sofatype.pinyin.DictFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class PinyinEngineLoaderHotTest {

    private val syllables = listOf("da", "zi", "xian", "yan", "bao")

    private fun dictData(words: List<Triple<String, List<String>, Double>>): DictData {
        val wordList = words.map { it.first }
        val entries = words.map { (word, syls, freq) ->
            DictData.Entry(syls.map { syllables.indexOf(it) }.toIntArray(), wordList.indexOf(word), freq)
        }
        return DictData(syllables, wordList, entries)
    }

    private fun spd1Bytes(data: DictData): ByteArray =
        ByteArrayOutputStream().also { DictFormat.write(data, it) }.toByteArray()

    private fun baseBytes(): ByteArray = spd1Bytes(
        dictData(
            listOf(
                Triple("打字", listOf("da", "zi"), 190.0),
                Triple("大字", listOf("da", "zi"), 20.0),
            ),
        ),
    )

    private fun hotBytes(): ByteArray = spd1Bytes(
        dictData(listOf(Triple("显眼包", listOf("xian", "yan", "bao"), 5000.0))),
    )

    private fun mapOpenAsset(vararg entries: Pair<String, ByteArray>): (String) -> InputStream {
        val map = entries.toMap()
        return { path ->
            val bytes = map[path] ?: throw IOException("asset not found: $path")
            ByteArrayInputStream(bytes)
        }
    }

    private fun loader(
        openAsset: (String) -> InputStream,
        hotAssetPaths: List<String> = emptyList(),
        logged: ((String) -> Unit)? = null,
    ): PinyinEngineLoader =
        PinyinEngineLoader(
            openAsset = openAsset,
            fullAssetPath = "full.bin",
            highFreqAssetPath = "highfreq.bin",
            hotAssetPaths = hotAssetPaths,
            alwaysLog = logged ?: {},
        )

    private fun words(loader: PinyinEngineLoader, input: String): List<String> =
        loader.engineForQuery()?.query(input, limit = 20)?.map { it.word } ?: emptyList()

    @Test
    fun `预加载后内置热词并入全量段_整拼命中热词且主库词序保持`() {
        val testLoader = loader(
            mapOpenAsset(
                "full.bin" to baseBytes(),
                "highfreq.bin" to baseBytes(),
                "dict/hotwords.bin" to hotBytes(),
            ),
            hotAssetPaths = listOf("dict/hotwords.bin"),
        )
        testLoader.preloadSequence()

        assertEquals("显眼包", words(testLoader, "xianyanbao").firstOrNull())
        assertEquals("主库查询结果不受热词并入影响", "打字", words(testLoader, "dazi").firstOrNull())
        assertTrue("主库词仍在候选内", "大字" in words(testLoader, "dazi"))
        assertNull("显眼包 不应出现在 dazi 候选", words(testLoader, "dazi").firstOrNull { it == "显眼包" })
    }

    @Test
    fun `无热词资产_行为与纯主词库一致`() {
        val testLoader = loader(mapOpenAsset("full.bin" to baseBytes(), "highfreq.bin" to baseBytes()))
        testLoader.preloadSequence()

        assertNull(words(testLoader, "xianyanbao").firstOrNull())
        assertEquals(2, testLoader.engineForQuery()?.entryCount)
        assertTrue("打字" in words(testLoader, "dazi"))
    }

    @Test
    fun `热词资产缺失_回退纯主词库且按 hot 范围记失败`() {
        val logged = mutableListOf<String>()
        val testLoader = loader(
            mapOpenAsset("full.bin" to baseBytes(), "highfreq.bin" to baseBytes()),
            hotAssetPaths = listOf("dict/hotwords.bin"),
            logged = { line -> logged.add(line) },
        )
        testLoader.preloadSequence()

        assertEquals("显眼包不在候选", null, words(testLoader, "xianyanbao").firstOrNull())
        assertEquals(2, testLoader.engineForQuery()?.entryCount)
        assertEquals(1, logged.size)
        assertTrue(logged[0].contains("scope=${PinyinEngineLoader.SCOPE_HOT}"))
        assertTrue("引擎可用时不得落终态行", !logged.contains(PinyinEngineLoader.TERMINAL_FAILURE_MESSAGE))
    }

    @Test
    fun `热词资产损坏_回退纯主词库不崩`() {
        val testLoader = loader(
            mapOpenAsset(
                "full.bin" to baseBytes(),
                "highfreq.bin" to baseBytes(),
                "dict/hotwords.bin" to byteArrayOf(1, 2, 3),
            ),
            hotAssetPaths = listOf("dict/hotwords.bin"),
        )
        testLoader.preloadSequence()

        assertEquals(2, testLoader.engineForQuery()?.entryCount)
        assertTrue("打字" in words(testLoader, "dazi"))
    }

    @Test
    fun `多热词资产依序合并_任一失败其余仍生效`() {
        val zunSyllables = listOf("zun", "du", "jia")
        val zunDict = DictData(
            zunSyllables,
            listOf("尊嘟假嘟"),
            listOf(DictData.Entry(intArrayOf(0, 1, 2, 1), 0, 4000.0)),
        )
        val testLoader = loader(
            mapOpenAsset(
                "full.bin" to baseBytes(),
                "highfreq.bin" to baseBytes(),
                "dict/hotwords.bin" to hotBytes(),
                "dict/hotwords2.bin" to spd1Bytes(zunDict),
            ),
            hotAssetPaths = listOf("dict/hotwords.bin", "dict/hotwords-broken.bin", "dict/hotwords2.bin"),
        )
        testLoader.preloadSequence()

        assertEquals("显眼包", words(testLoader, "xianyanbao").firstOrNull())
        assertEquals("尊嘟假嘟", words(testLoader, "zundujiadu").firstOrNull())
    }

    @Test
    fun `重建后热词生效_主库词序不变`() {
        val testLoader = loader(
            mapOpenAsset(
                "full.bin" to baseBytes(),
                "highfreq.bin" to baseBytes(),
                "dict/hotwords.bin" to hotBytes(),
            ),
            hotAssetPaths = listOf("dict/hotwords.bin"),
        )
        testLoader.preloadSequence()

        assertEquals("显眼包", words(testLoader, "xianyanbao").firstOrNull())
        testLoader.rebuild()
        assertEquals("显眼包", words(testLoader, "xianyanbao").firstOrNull())
        assertEquals("打字", words(testLoader, "dazi").firstOrNull())
    }
}
