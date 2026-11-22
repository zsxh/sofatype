package com.sofatype.pinyin.tools

import com.sofatype.pinyin.DictFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class HotDictBuildTest {

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
        val (merged, stats) = HotDictBuild.mergeLines(scel, hot, emptySet())
        assertEquals(2, merged.size)
        assertEquals(1, stats.scelSkippedOverlongCount)
        assertEquals(1, stats.hotSkippedOverlongCount)
        assertEquals(1, stats.hotKeptCount)
    }

    @Test
    fun `主词库词表按首列加载`() {
        val words = HotDictBuild.loadMainDictWords(
            listOf("电视 141.0 0 dian shi", "手机 100.0 0 shou ji", "", "   ").asSequence(),
        )
        assertEquals(setOf("电视", "手机"), words)
    }

    @Test
    fun `热词源按主词库命中与重复去重且保留顺序`() {
        val scel = scelWords(Triple("搭子", 3000, listOf("da", "zi")))
        val hot = listOf(
            "电视 3000 0 dian shi",
            "显眼包 3000 0 xian yan bao",
            "搭子 3000 0 da zi",
            "尊嘟假嘟 4000 0 zun du jia du",
            "显眼包 3000 0 xian yan bao",
        )
        val (merged, stats) = HotDictBuild.mergeLines(scel, hot, setOf("电视", "手机"))
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
        val (merged, _) = HotDictBuild.mergeLines(scel, hot, emptySet())
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
    fun `scel 与热词源两路经 mergeLines 汇合构建词条`() {
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
        val (merged, _) = HotDictBuild.mergeLines(
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

    @Test
    fun `同源重跑产物 sha256 一致_确定性`() {
        val dir = createTempDir("hotdict_determinism")
        val scelFile = File(dir, "sample.scel").apply { writeBytes(selfBuiltScel()) }
        val hotFile = File(dir, "sample.txt").apply {
            writeText("破防 3000 0 po fang\n显眼包 3000 0 xian yan bao\n", Charsets.UTF_8)
        }
        val mainFile = File(dir, "main.txt").apply {
            writeText("电视 141.0 0 dian shi\n", Charsets.UTF_16)
        }
        val outA = File(dir, "out-a.bin")
        val outB = File(dir, "out-b.bin")
        buildTo(outA, mainFile, scelFile, hotFile)
        buildTo(outB, mainFile, scelFile, hotFile)
        assertEquals("同源两跑产物字节一致", outA.length(), outB.length())
        assertEquals(
            "同源两跑 sha256 一致",
            HotDictBuild.sha256Hex(outA),
            HotDictBuild.sha256Hex(outB),
        )
        val readBack = DictFormat.read(outA.inputStream())
        assertTrue(readBack.entryCount >= 3)
    }

    private fun selfBuiltScel(): ByteArray {
        val bytes = ByteArrayOutputStream()
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
        u16(1200).copyInto(ext)
        bytes.write(u16(1))
        bytes.write(u16(4))
        bytes.write(u16(0))
        bytes.write(u16(1))
        bytes.write(u16(4))
        bytes.write(utf16("搭子"))
        bytes.write(u16(10))
        bytes.write(ext)
        return bytes.toByteArray()
    }

    private fun buildTo(output: File, mainFile: File, vararg materials: File) {
        val mats = materials.map { m ->
            if (m.name.endsWith(".scel")) "scel" to m else "hot" to m
        }
        HotDictBuild.run(output, mainFile, mats)
    }

    private fun createTempDir(prefix: String): File {
        val dir = File.createTempFile(prefix, "")
        dir.delete()
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }
}
