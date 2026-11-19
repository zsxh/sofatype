package com.sofatype.pinyin.tools

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.charset.Charset

class ConvertDictTest {

    private fun writeSource(lines: List<String>): File {
        val file = File.createTempFile("rawdict_test", ".txt")
        file.deleteOnExit()
        file.writeText(lines.joinToString("\n") + "\n", Charset.forName("UTF-16"))
        return file
    }

    private fun readSource(file: File): List<String> =
        file.reader(Charset.forName("UTF-16")).readLines()

    @Test
    fun `正常行解析出词条与频次`() {
        val source = writeSource(
            listOf(
                "欧美国家 141.29744947 0 ou mei guo jia",
                "鼥 0.750684002197 1 ba",
                "钯 47.7250412134 0 ba",
                "钯 5.97806362819 0 pa",
            ),
        )
        val (data, stats) = DictBuilder.build(readSource(source).asSequence())

        assertEquals(4, stats.lineCount)
        assertEquals(4, stats.entryCount)
        assertEquals(0, stats.skippedLineCount)
        assertEquals(6, stats.syllableCount)
        assertEquals(3, stats.wordCount)

        val ba = data.entries[2]
        val pa = data.entries[3]
        assertEquals(ba.wordId, pa.wordId)
        assertEquals("钯", data.words[ba.wordId])
        assertEquals(47.7250412134, ba.freq, 0.0)
        assertEquals(5.97806362819, pa.freq, 0.0)
    }

    @Test
    fun `音节数与字数不符的行被跳过`() {
        val source = writeSource(
            listOf(
                "客厅 100.0 0 ke ting",
                "坏行 100.0 0 ke",
            ),
        )
        val (data, stats) = DictBuilder.build(readSource(source).asSequence())
        assertEquals(2, stats.lineCount)
        assertEquals(1, stats.entryCount)
        assertEquals(1, stats.skippedLineCount)
        assertEquals("客厅", data.words[data.entries[0].wordId])
    }

    @Test
    fun `频次非浮点的行被跳过`() {
        val source = writeSource(
            listOf(
                "客厅 100.0 0 ke ting",
                "坏行 abc 0 ke xing",
            ),
        )
        val (data, stats) = DictBuilder.build(readSource(source).asSequence())
        assertEquals(1, stats.entryCount)
        assertEquals(1, stats.skippedLineCount)
    }

    @Test
    fun `含非小写字母音节的行被跳过`() {
        val source = writeSource(
            listOf(
                "客厅 100.0 0 ke ting",
                "坏行 100.0 0 Ke Ting",
            ),
        )
        val (data, stats) = DictBuilder.build(readSource(source).asSequence())
        assertEquals(1, stats.entryCount)
        assertEquals(1, stats.skippedLineCount)
    }

    @Test
    fun `音节表去重排序且词条保持源行序`() {
        val source = writeSource(
            listOf(
                "丙词 10.0 0 zhi cai",
                "甲 20.0 0 an",
            ),
        )
        val (data, _) = DictBuilder.build(readSource(source).asSequence())
        assertEquals(listOf("an", "cai", "zhi"), data.syllables)
        assertEquals("丙词", data.words[data.entries[0].wordId])
        assertEquals("甲", data.words[data.entries[1].wordId])
        assertEquals(listOf("zhi", "cai"), data.entries[0].syllableIds.map { data.syllables[it] })
    }
}
