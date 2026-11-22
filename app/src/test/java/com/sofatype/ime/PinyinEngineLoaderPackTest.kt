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

class PinyinEngineLoaderPackTest {

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

    private fun packBytes(): ByteArray = spd1Bytes(
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
        packProvider: (() -> List<DictData>)? = null,
        logged: ((String) -> Unit)? = null,
    ): PinyinEngineLoader =
        PinyinEngineLoader(
            openAsset = openAsset,
            fullAssetPath = "full.bin",
            highFreqAssetPath = "highfreq.bin",
            packDataProvider = packProvider,
            alwaysLog = logged ?: {},
        )

    private fun words(loader: PinyinEngineLoader, input: String): List<String> =
        loader.engineForQuery()?.query(input, limit = 20)?.map { it.word } ?: emptyList()

    private fun awaitWords(loader: PinyinEngineLoader, input: String, expect: (List<String>) -> Boolean): List<String> {
        val deadline = System.currentTimeMillis() + 5_000
        var latest = words(loader, input)
        while (!expect(latest) && System.currentTimeMillis() < deadline) {
            Thread.sleep(20)
            latest = words(loader, input)
        }
        return latest
    }

    @Test
    fun `预加载后词包并入全量段_整拼命中词包词且主库词序保持`() {
        val testLoader = loader(
            mapOpenAsset("full.bin" to baseBytes(), "highfreq.bin" to baseBytes()),
            packProvider = { listOf(DictFormat.read(ByteArrayInputStream(packBytes()))) },
        )
        testLoader.preloadSequence()

        assertEquals("显眼包", words(testLoader, "xianyanbao").firstOrNull())
        assertEquals("主库查询结果不受词包并入影响", "打字", words(testLoader, "dazi").firstOrNull())
        assertTrue("主库词仍在候选内", "大字" in words(testLoader, "dazi"))
        assertEquals("显眼包 不应出现在 dazi 候选", null, words(testLoader, "dazi").firstOrNull { it == "显眼包" })
    }

    @Test
    fun `无词包提供方_行为与现状一致`() {
        val testLoader = loader(mapOpenAsset("full.bin" to baseBytes(), "highfreq.bin" to baseBytes()))
        testLoader.preloadSequence()

        assertNull(words(testLoader, "xianyanbao").firstOrNull())
        assertEquals(2, testLoader.engineForQuery()?.entryCount)
        assertTrue("打字" in words(testLoader, "dazi"))
    }

    @Test
    fun `词包提供方整体失败_回退纯主词库并恒记一行`() {
        val logged = mutableListOf<String>()
        val testLoader = loader(
            mapOpenAsset("full.bin" to baseBytes(), "highfreq.bin" to baseBytes()),
            packProvider = { throw IOException("pack provider broken") },
            logged = { line -> logged.add(line) },
        )
        testLoader.preloadSequence()

        assertEquals("显眼包不在候选", null, words(testLoader, "xianyanbao").firstOrNull())
        assertEquals(2, testLoader.engineForQuery()?.entryCount)
        assertEquals(1, logged.size)
        assertTrue(logged[0].contains("scope=${PinyinEngineLoader.SCOPE_PACKS}"))
        assertTrue("引擎可用时不得落终态行", !logged.contains(PinyinEngineLoader.TERMINAL_FAILURE_MESSAGE))
    }

    @Test
    fun `勾选集合变化触发重建_重建后词包词生效_取消后回退`() {
        val enabled = mutableListOf<DictData>()
        val testLoader = loader(
            mapOpenAsset("full.bin" to baseBytes(), "highfreq.bin" to baseBytes()),
            packProvider = { enabled.toList() },
        )
        testLoader.preloadSequence()
        assertNull("初始无勾选词包", words(testLoader, "xianyanbao").firstOrNull())

        enabled.add(DictFormat.read(ByteArrayInputStream(packBytes())))
        testLoader.rebuild()
        val afterEnable = awaitWords(testLoader, "xianyanbao") { it.isNotEmpty() }
        assertEquals("勾选后重建应命中词包词", "显眼包", afterEnable.firstOrNull())

        enabled.clear()
        testLoader.rebuild()
        val afterDisable = awaitWords(testLoader, "xianyanbao") { it.isEmpty() }
        assertTrue("取消勾选后回退纯主词库", afterDisable.isEmpty())
        assertTrue("主库查询不受影响", "打字" in words(testLoader, "dazi"))
    }
}
