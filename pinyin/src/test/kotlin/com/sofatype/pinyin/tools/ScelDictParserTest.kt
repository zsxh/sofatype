package com.sofatype.pinyin.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ScelDictParserTest {

    private val TAG_ECS_STRING = "ECS" + 0x01.toChar()

    private val TAG_DCS_STRING = "DCS" + 0x01.toChar()

    private fun utf16(text: String): ByteArray = text.toByteArray(Charsets.UTF_16LE)

    private fun u16(value: Int): ByteArray =
        byteArrayOf((value and 0xFF).toByte(), ((value shr 8) and 0xFF).toByte())

    private fun u32(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    private fun pinyinTable(syllables: List<String>): ByteArray {
        var all = u32(syllables.size.toLong())
        syllables.forEachIndexed { i, s ->
            all += u16(i) + u16(utf16(s).size) + utf16(s)
        }
        return all
    }

    private fun wordBlock(same: Int, syllableIds: List<Int>, words: List<Pair<String, Int>>): ByteArray {
        val wordBytes = syllableIds.size * 2
        var out = u16(same) + u16(wordBytes)
        for (id in syllableIds) out += u16(id)
        for ((word, freq) in words) {
            val bytes = utf16(word)
            assertEquals(wordBytes, bytes.size)
            val ext = ByteArray(10)
            u16(freq).copyInto(ext)
            out += u16(bytes.size) + bytes + u16(ext.size) + ext
        }
        return out
    }

    private fun scelFile(
        modernTag: String?,
        legacy: Boolean,
        pinyins: List<String>,
        blocks: List<ByteArray>,
        trailing: ByteArray = ByteArray(0),
    ): ByteArray {
        val header = ByteArray(0x1540)
        if (legacy) {
            byteArrayOf(0x61, 0x43, 0x48, 0x31).copyInto(header)
        } else {
            u32(0x1540L).copyInto(header)
            modernTag!!.toByteArray(Charsets.US_ASCII).copyInto(header, 4)
        }
        var all: ByteArray = header + pinyinTable(pinyins)
        for (b in blocks) all += b
        return all + trailing
    }

    private fun parse(bytes: ByteArray): ScelDictParser.Result =
        ScelDictParser.parse(ByteArrayInputStream(bytes))

    @Test
    fun `新格式 ECS 头解析词条拼音与词频`() {
        val bytes = scelFile(
            TAG_ECS_STRING,
            false,
            listOf("a", "bao", "bin"),
            listOf(
                wordBlock(1, listOf(0, 1), listOf("阿宝" to 500)),
                wordBlock(1, listOf(0, 2), listOf("阿宾" to 700)),
            ),
        )
        val result = parse(bytes)
        assertEquals("ECS", result.format)
        assertEquals(3, result.pinyinCount)
        assertEquals(2, result.words.size)
        assertEquals(0, result.skippedWordCount)
        assertEquals(0, result.trailingByteCount)
        assertEquals("阿宝", result.words[0].word)
        assertEquals(500, result.words[0].freq)
        assertEquals(listOf("a", "bao"), result.words[0].syllables)
        assertEquals("阿宾", result.words[1].word)
        assertEquals(listOf("a", "bin"), result.words[1].syllables)
    }

    @Test
    fun `DCS 头与同音词组 same 大于一解析`() {
        val bytes = scelFile(
            TAG_DCS_STRING,
            false,
            listOf("a", "ba"),
            listOf(wordBlock(2, listOf(0, 1), listOf("阿八" to 300, "阿爸" to 200))),
        )
        val result = parse(bytes)
        assertEquals("DCS", result.format)
        assertEquals(2, result.words.size)
        assertEquals("阿八", result.words[0].word)
        assertEquals(300, result.words[0].freq)
        assertEquals("阿爸", result.words[1].word)
        assertEquals(listOf("a", "ba"), result.words[1].syllables)
    }

    @Test
    fun `旧格式 SCEL 魔数按同块布局解析`() {
        val bytes = scelFile(
            null,
            true,
            listOf("de"),
            listOf(wordBlock(1, listOf(0), listOf("的" to 9000))),
        )
        val result = parse(bytes)
        assertEquals("SCEL", result.format)
        assertEquals(1, result.words.size)
        assertEquals("的", result.words[0].word)
        assertEquals(9000, result.words[0].freq)
    }

    @Test
    fun `词表后的附加区不并入并如实计数`() {
        val trailing = u16(33) + ByteArray(18)
        val bytes = scelFile(
            TAG_ECS_STRING,
            false,
            listOf("a"),
            listOf(wordBlock(1, listOf(0), listOf("啊" to 10))),
            trailing,
        )
        val result = parse(bytes)
        assertEquals(1, result.words.size)
        assertTrue(result.trailingByteCount >= trailing.size)
    }

    @Test
    fun `坏词条目跳过且不中断后续词`() {
        val badWord = u16(1) + u16(4) + u16(0) + u16(0) +
            u16(4) + byteArrayOf(0x00, 0xD9.toByte(), 0x61, 0x00) + u16(10) + ByteArray(10)
        val bytes = scelFile(
            TAG_ECS_STRING,
            false,
            listOf("a"),
            listOf(
                badWord,
                wordBlock(1, listOf(0), listOf("啊" to 10)),
            ),
        )
        val result = parse(bytes)
        assertEquals(1, result.skippedWordCount)
        assertEquals(1, result.words.size)
        assertEquals("啊", result.words[0].word)
    }

    @Test
    fun `魔数不符抛异常`() {
        val bytes = ByteArray(0x2000)
        try {
            parse(bytes)
            throw AssertionError("应抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("魔数"))
        }
    }

    @Test
    fun `文件过短抛异常`() {
        val bytes = "aCH1".toByteArray(Charsets.US_ASCII)
        try {
            parse(bytes)
            throw AssertionError("应抛出异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("过短"))
        }
    }
}
