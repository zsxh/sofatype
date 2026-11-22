package com.sofatype.pinyin.tools

import com.sofatype.pinyin.DictFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class WordPackBuildTest {

    private val TAG_ECS_STRING = "ECS" + 0x01.toChar()

    private fun scelWords(vararg triples: Triple<String, Int, List<String>>): List<ScelDictParser.Word> =
        triples.map { ScelDictParser.Word(it.first, it.second, it.third) }

    @Test
    fun `超 SPD1 上限词条两路各自跳过`() {
        val scel = scelWords(
            Triple("正常词", 100, listOf("zheng", "chang", "ci")),
            Triple("七字词七字词七字", 100, listOf("qi", "zi", "qi", "zi", "qi", "zi", "qi", "zi")),
        )
        val hot = listOf(
            "七字热搜词七字热搜词七 3000 0 qi zi re sou ci qi zi re sou ci qi",
            "正常热搜词 3000 0 zheng chang re sou ci",
        )
        val (merged, stats) = WordPackBuild.mergeLines(scel, hot, emptySet())
        assertEquals(2, merged.size)
        assertEquals(1, stats.scelSkippedOverlongCount)
        assertEquals(1, stats.hotSkippedOverlongCount)
        assertEquals(1, stats.hotKeptCount)
    }

    @Test
    fun `主词库词表按首列加载`() {
        val words = WordPackBuild.loadMainDictWords(
            listOf("电视 141.0 0 dian shi", "手机 100.0 0 shou ji", "", "   ").asSequence(),
        )
        assertEquals(setOf("电视", "手机"), words)
    }

    @Test
    fun `热搜词按主词库命中与重复去重且保留顺序`() {
        val scel = scelWords(Triple("搭子", 3000, listOf("da", "zi")))
        val hot = listOf(
            "电视 3000 0 dian shi",
            "显眼包 3000 0 xian yan bao",
            "搭子 3000 0 da zi",
            "尊嘟假嘟 4000 0 zun du jia du",
            "显眼包 3000 0 xian yan bao",
        )
        val (merged, stats) = WordPackBuild.mergeLines(scel, hot, setOf("电视", "手机"))
        assertEquals(3, merged.size)
        assertEquals("搭子 3000 0 da zi", merged[0])
        assertEquals("尊嘟假嘟 4000 0 zun du jia du", merged[2])
        assertEquals(5, stats.hotSourceLineCount)
        assertEquals(1, stats.hotMainDictHitCount)
        assertEquals(2, stats.hotDuplicateCount)
        assertEquals(2, stats.hotKeptCount)
        assertEquals(1, stats.scelWordCount)
    }

    @Test
    fun `合并词条构建 SPD1 可读回且双跑字节一致`() {
        val scel = scelWords(Triple("搭子", 3000, listOf("da", "zi")))
        val hot = listOf("显眼包 3000 0 xian yan bao")
        val (merged, _) = WordPackBuild.mergeLines(scel, hot, emptySet())
        val (data, stats) = DictBuilder.build(merged.asSequence())
        assertEquals(2, stats.entryCount)
        val first = ByteArrayOutputStream()
        DictFormat.write(data, first)
        val second = ByteArrayOutputStream()
        DictFormat.write(data, second)
        assertTrue(first.toByteArray().contentEquals(second.toByteArray()))
        val readBack = DictFormat.read(ByteArrayInputStream(first.toByteArray()))
        assertEquals(listOf("搭子", "显眼包"), readBack.words)
    }

    @Test
    fun `manifest 字段齐全 url 留空且双跑一致`() {
        val tmp = File.createTempFile("wordpack_manifest_test", ".bin")
        tmp.deleteOnExit()
        tmp.writeBytes(byteArrayOf(1, 2, 3))
        val json = WordPackBuild.manifestJson("20261122.1", "热词包", tmp)
        assertTrue(json.contains("\"name\": \"热词包\""))
        assertTrue(json.contains("\"version\": \"20261122.1\""))
        assertTrue(json.contains("\"sha256\": \"${WordPackBuild.sha256Hex(tmp)}\""))
        assertTrue(json.contains("\"bytes\": 3"))
        assertTrue(json.contains("\"url\": \"\""))
        val again = WordPackBuild.manifestJson("20261122.1", "热词包", tmp)
        assertEquals(json, again)
    }

    @Test
    fun `manifest 名称含引号被转义`() {
        val tmp = File.createTempFile("wordpack_manifest_test", ".bin")
        tmp.deleteOnExit()
        tmp.writeBytes(byteArrayOf(1))
        val json = WordPackBuild.manifestJson("20261122.1", "带\"引号\"词包", tmp)
        assertTrue(json.contains("\"name\": \"带\\\"引号\\\"词包\""))
    }

    @Test
    fun `manifest 版本号格式非法被拒`() {
        val tmp = File.createTempFile("wordpack_manifest_test", ".bin")
        tmp.deleteOnExit()
        tmp.writeBytes(byteArrayOf(1))
        try {
            WordPackBuild.manifestJson("1.0", "热词包", tmp)
            throw AssertionError("应抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("版本号"))
        }
    }

    @Test
    fun `scel 与热搜两路经 mergeLines 汇合构建词条`() {
        val bytes = java.io.ByteArrayOutputStream()
        val header = ByteArray(0x1540)
        byteArrayOf(0x40, 0x15, 0x00, 0x00).copyInto(header)
        TAG_ECS_STRING.toByteArray(Charsets.US_ASCII).copyInto(header, 4)
        bytes.write(header)
        val utf16 = { s: String -> s.toByteArray(Charsets.UTF_16LE) }
        val u16 = { v: Int ->
            byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
        }
        val pinyins = listOf("da", "zi")
        bytes.write(u16(pinyins.size))
        bytes.write(u16(0))
        pinyins.forEachIndexed { i, s ->
            bytes.write(u16(i))
            val b = utf16(s)
            bytes.write(u16(b.size))
            bytes.write(b)
        }
        val ext = ByteArray(10)
        u16(3000).copyInto(ext)
        bytes.write(u16(1))
        bytes.write(u16(4))
        bytes.write(u16(0))
        bytes.write(u16(1))
        bytes.write(u16(4))
        bytes.write(utf16("搭子"))
        bytes.write(u16(10))
        bytes.write(ext)
        val parsed = ScelDictParser.parse(ByteArrayInputStream(bytes.toByteArray()))
        assertEquals(1, parsed.words.size)
        val (merged, _) = WordPackBuild.mergeLines(
            parsed.words,
            listOf("搭子 5000 0 da zi", "破防 3000 0 po fang"),
            setOf("电视"),
        )
        assertEquals(2, merged.size)
        val (data, stats) = DictBuilder.build(merged.asSequence())
        assertEquals(2, stats.entryCount)
        assertEquals("搭子", data.words[data.entries[0].wordId])
        assertEquals(3000.0, data.entries[0].freq, 0.0)
        assertEquals("破防", data.words[data.entries[1].wordId])
    }
}
