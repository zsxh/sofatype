package com.sofatype.ime

import com.sofatype.pinyin.DictData
import com.sofatype.pinyin.DictFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PinyinEngineLoaderTest {

    private fun dictData(words: List<Pair<String, Double>>): DictData {

        val syllables = listOf("ni", "hao", "ke", "ting", "dian", "shi")
        val wordList = words.map { it.first }
        var entryIndex = 0
        val entries = words.map { (_, freq) ->
            val s0 = syllables[entryIndex % syllables.size]
            val s1 = syllables[(entryIndex + 1) % syllables.size]
            val e = DictData.Entry(intArrayOf(syllables.indexOf(s0), syllables.indexOf(s1)), entryIndex, freq)
            entryIndex++
            e
        }
        return DictData(syllables, wordList, entries)
    }

    private fun spd1Bytes(data: DictData): ByteArray =
        ByteArrayOutputStream().also { DictFormat.write(data, it) }.toByteArray()

    private fun fullBytes(): ByteArray {
        val data = dictData(
            listOf(
                "你" to 900.0,
                "你好" to 800.0,
                "好" to 700.0,
                "客厅" to 100.0,
                "电视" to 50.0,
            ),
        )
        return spd1Bytes(data)
    }

    private fun highFreqBytes(): ByteArray =
        spd1Bytes(dictData(listOf("你" to 900.0, "你好" to 800.0, "好" to 700.0)))

    private fun mapOpenAsset(vararg entries: Pair<String, ByteArray>): (String) -> InputStream {
        val map = entries.toMap()
        return { path ->
            val bytes = map[path] ?: throw IOException("asset not found: $path")
            ByteArrayInputStream(bytes)
        }
    }

    private fun loader(
        openAsset: (String) -> InputStream,
        ready: ((String, Int) -> Unit)? = null,
        failed: ((String, Throwable) -> Unit)? = null,
        logged: ((String) -> Unit)? = null,
    ): PinyinEngineLoader =
        PinyinEngineLoader(
            openAsset = openAsset,
            fullAssetPath = "full.bin",
            highFreqAssetPath = "highfreq.bin",
            onEngineReady = ready ?: { _, _ -> },
            onLoadFailure = failed ?: { _, _ -> },
            alwaysLog = logged ?: {},
        )

    private fun queryWords(loader: PinyinEngineLoader, input: String): List<String> =
        loader.engineForQuery()?.query(input, limit = 10)?.map { it.word } ?: emptyList()

    @Test
    fun `预加载序列_子集先就绪_全量后整体替换`() {
        val scopes = mutableListOf<String>()
        val testLoader = loader(
            mapOpenAsset("full.bin" to fullBytes(), "highfreq.bin" to highFreqBytes()),
            ready = { scope, _ -> scopes.add(scope) },
        )
        testLoader.preloadSequence()

        assertEquals(listOf(PinyinEngineLoader.SCOPE_HIGH_FREQ, PinyinEngineLoader.SCOPE_FULL), scopes)

        assertTrue("全量就绪后引擎应为全量段", testLoader.engineForQuery() != null)
        assertEquals(5, testLoader.engineForQuery()?.entryCount)
    }

    @Test
    fun `子集加载失败_全量仍生效_失败恒记一次无终态行`() {
        val failures = mutableListOf<String>()
        val logged = mutableListOf<String>()
        val testLoader = loader(
            openAsset = { path ->
                if (path == "highfreq.bin") throw IOException("subset broken")
                ByteArrayInputStream(fullBytes())
            },
            failed = { scope, _ -> failures.add(scope) },
            logged = { line -> logged.add(line) },
        )
        testLoader.preloadSequence()
        assertEquals(listOf(PinyinEngineLoader.SCOPE_HIGH_FREQ), failures)
        assertEquals(5, testLoader.engineForQuery()?.entryCount)

        assertEquals(1, logged.size)
        assertTrue(logged[0].contains("scope=${PinyinEngineLoader.SCOPE_HIGH_FREQ}"))
        assertTrue(logged[0].contains("reason=IOException"))
        assertTrue(logged[0].contains("subset broken"))
        assertTrue("引擎可用时不得落终态行", !logged.contains(PinyinEngineLoader.TERMINAL_FAILURE_MESSAGE))
    }

    @Test
    fun `全量加载失败_子集保持活动_失败恒记一次无终态行`() {
        val logged = mutableListOf<String>()
        val testLoader = loader(
            openAsset = { path ->
                if (path == "full.bin") throw IOException("full broken")
                ByteArrayInputStream(highFreqBytes())
            },
            logged = { line -> logged.add(line) },
        )
        testLoader.preloadSequence()
        assertEquals(3, testLoader.engineForQuery()?.entryCount)
        assertTrue("全量缺失时活动引擎保持子集（不置空不崩溃）", testLoader.engineForQuery() != null)

        assertEquals(1, logged.size)
        assertTrue(logged[0].contains("scope=${PinyinEngineLoader.SCOPE_FULL}"))
        assertTrue(logged[0].contains("reason=IOException"))
        assertTrue("引擎可用时不得落终态行", !logged.contains(PinyinEngineLoader.TERMINAL_FAILURE_MESSAGE))
    }

    @Test
    fun `双段失败_退化为无候选不崩溃_两条段失败恒记加一条终态恒记`() {
        val failures = mutableListOf<String>()
        val logged = mutableListOf<String>()
        val testLoader = loader(
            openAsset = { throw IOException("all broken") },
            failed = { scope, _ -> failures.add(scope) },
            logged = { line -> logged.add(line) },
        )
        testLoader.preloadSequence()

        assertEquals(
            listOf(PinyinEngineLoader.SCOPE_HIGH_FREQ, PinyinEngineLoader.SCOPE_FULL),
            failures,
        )
        assertNull("双段失败后引擎应为 null（既有降级口径）", testLoader.engineForQuery())
        assertEquals(emptyList<String>(), queryWords(testLoader, "ni"))

        assertEquals(3, logged.size)
        assertTrue(logged[0].contains("scope=${PinyinEngineLoader.SCOPE_HIGH_FREQ}"))
        assertTrue(logged[1].contains("scope=${PinyinEngineLoader.SCOPE_FULL}"))
        assertEquals(PinyinEngineLoader.TERMINAL_FAILURE_MESSAGE, logged[2])
    }

    @Test
    fun `成功路径不恒记`() {
        val logged = mutableListOf<String>()
        val testLoader = loader(
            mapOpenAsset("full.bin" to fullBytes(), "highfreq.bin" to highFreqBytes()),
            logged = { line -> logged.add(line) },
        )
        testLoader.preloadSequence()
        assertTrue("双段成功后引擎应就绪", testLoader.engineForQuery() != null)

        assertEquals("成功路径不得有任何恒记日志", emptyList<String>(), logged)
    }

    @Test
    fun `恒记通道源码零输入内容面引用`() {

        val source = File("src/main/java/com/sofatype/ime/PinyinEngineLoader.kt").readText()
        val banned = listOf(
            "composer",
            "candidate",
            "remoteText",
            "deviceName",
            "test_input",
            "commitText",
            "editText",
        )
        for (identifier in banned) {
            assertFalse(
                "PinyinEngineLoader.kt 不得引用输入内容面标识 $identifier（隐私红线）",
                source.contains(identifier, ignoreCase = true),
            )
        }
    }

    @Test
    fun `查询路径零加载语义_未启动预加载即无引擎`() {

        val testLoader = loader(mapOpenAsset("full.bin" to fullBytes(), "highfreq.bin" to highFreqBytes()))
        assertNull(testLoader.engineForQuery())
        assertEquals(emptyList<String>(), queryWords(testLoader, "ni"))
    }

    @Test
    fun `真实后台线程发布可见性_onEngineReady到活动引擎可查`() {
        val testLoader = loader(mapOpenAsset("full.bin" to fullBytes(), "highfreq.bin" to highFreqBytes()))

        val latch = CountDownLatch(1)
        val asyncLoader = PinyinEngineLoader(
            openAsset = mapOpenAsset("full.bin" to fullBytes(), "highfreq.bin" to highFreqBytes()),
            fullAssetPath = "full.bin",
            highFreqAssetPath = "highfreq.bin",
            onEngineReady = { _, _ -> latch.countDown() },
        )
        asyncLoader.startPreload()
        assertTrue("预加载应在线程内完成发布（5s 限时）", latch.await(5, TimeUnit.SECONDS))

        assertTrue(asyncLoader.engineForQuery() != null)
    }

    @Test
    fun `迟到的子集不回退覆盖已就绪的全量`() {
        val testLoader = loader(mapOpenAsset("full.bin" to fullBytes(), "highfreq.bin" to highFreqBytes()))

        testLoader.loadAndActivate("full.bin", PinyinEngineLoader.SCOPE_FULL)
        testLoader.loadAndActivate("highfreq.bin", PinyinEngineLoader.SCOPE_HIGH_FREQ)
        assertEquals("活动引擎应保持全量段", 5, testLoader.engineForQuery()?.entryCount)
    }
}
