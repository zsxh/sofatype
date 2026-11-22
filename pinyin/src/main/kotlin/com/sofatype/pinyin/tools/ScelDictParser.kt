package com.sofatype.pinyin.tools

import java.io.InputStream
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction

object ScelDictParser {

    class Word(val word: String, val freq: Int, val syllables: List<String>)

    class Result(
        val format: String,
        val pinyinCount: Int,
        val words: List<Word>,
        val skippedWordCount: Int,
        val trailingByteCount: Int,
    )

    private val LEGACY_MAGIC = byteArrayOf(0x61, 0x43, 0x48, 0x31)
    private val MODERN_MAGIC_ECS = byteArrayOf(0x45, 0x43, 0x53, 0x01)
    private val MODERN_MAGIC_DCS = byteArrayOf(0x44, 0x43, 0x53, 0x01)
    private const val PINYIN_TABLE_OFFSET = 0x1540
    private const val MAX_PINYIN_COUNT = 0xFFFF
    private const val MAX_PINYIN_BYTES = 32
    private const val MAX_WORD_BYTES = 128
    private const val MAX_GROUP_SAME = 4096
    private const val MAX_EXT_BYTES = 256

    private val strictUtf16: CharsetDecoder = newStrictUtf16Decoder()

    private fun newStrictUtf16Decoder(): CharsetDecoder {
        val decoder = Charsets.UTF_16LE.newDecoder()
        decoder.onMalformedInput(CodingErrorAction.REPORT)
        decoder.onUnmappableCharacter(CodingErrorAction.REPORT)
        return decoder
    }

    fun parse(input: InputStream): Result {
        val data = input.readBytes()
        val format = detectFormat(data)
        require(data.size >= PINYIN_TABLE_OFFSET + 4) { "scel 文件过短（缺拼音表头）：${data.size} 字节" }

        val pinyinCount = readU32(data, PINYIN_TABLE_OFFSET).toInt()
        require(pinyinCount in 1..MAX_PINYIN_COUNT) { "拼音表数量非法：$pinyinCount" }
        var pos = PINYIN_TABLE_OFFSET + 4
        val table = HashMap<Int, String>(pinyinCount)
        repeat(pinyinCount) {
            require(pos + 4 <= data.size) { "拼音表越界（偏移 $pos）" }
            val index = readU16(data, pos).toInt()
            val len = readU16(data, pos + 2).toInt()
            require(len in 1..MAX_PINYIN_BYTES) { "拼音长度非法（$len，偏移 $pos）" }
            require(pos + 4 + len <= data.size) { "拼音表越界（偏移 $pos）" }
            table[index] = decodeUtf16(data, pos + 4, len)
            pos += 4 + len
        }

        val words = ArrayList<Word>()
        var skipped = 0
        while (pos + 4 <= data.size) {
            val same = readU16(data, pos).toInt()
            val wordBytes = readU16(data, pos + 2).toInt()
            val invalid = same <= 0 || same > MAX_GROUP_SAME ||
                wordBytes <= 0 || wordBytes % 2 != 0 || wordBytes > MAX_WORD_BYTES ||
                pos + 4 + wordBytes > data.size
            if (invalid) break
            val syllableCount = wordBytes / 2
            val syllableIds = IntArray(syllableCount) { i -> readU16(data, pos + 4 + i * 2).toInt() }
            if (syllableIds.any { it !in table }) break
            val syllables = syllableIds.map { table.getValue(it) }
            var groupPos = pos + 4 + wordBytes
            var groupBroken = false
            repeat(same) {
                if (groupBroken) return@repeat
                if (groupPos + 4 + wordBytes > data.size) { groupBroken = true; return@repeat }
                val wordLenAgain = readU16(data, groupPos).toInt()
                if (wordLenAgain != wordBytes) { groupBroken = true; return@repeat }
                val extLen = readU16(data, groupPos + 2 + wordBytes).toInt()
                if (extLen < 0 || extLen > MAX_EXT_BYTES || groupPos + 4 + wordBytes + extLen > data.size) {
                    groupBroken = true
                    return@repeat
                }
                val extStart = groupPos + 4 + wordBytes
                val freq = if (extLen >= 2) readU16(data, extStart).toInt() else 0
                val rawWord = data.decodeSlice(groupPos + 2, wordBytes)
                if (rawWord == null) {
                    skipped++
                } else if (rawWord.toByteArray(Charsets.UTF_16LE).size > 64) {
                    skipped++
                } else {
                    words.add(Word(rawWord, freq, syllables))
                }
                groupPos += 4 + wordBytes + extLen
            }
            if (groupBroken) break
            pos = groupPos
        }

        return Result(format, pinyinCount, words, skipped, data.size - pos)
    }

    private fun detectFormat(data: ByteArray): String = when {
        data.size >= 8 && readU32(data, 0) == 0x1540L && data.sliceArray(4..7).contentEquals(MODERN_MAGIC_ECS) -> "ECS"
        data.size >= 8 && readU32(data, 0) == 0x1540L && data.sliceArray(4..7).contentEquals(MODERN_MAGIC_DCS) -> "DCS"
        data.size >= 4 && data.sliceArray(0..3).contentEquals(LEGACY_MAGIC) -> "SCEL"
        else -> throw IllegalArgumentException("非 scel 词库文件（魔数不符）：${data.size} 字节")
    }

    private fun ByteArray.decodeSlice(offset: Int, length: Int): String? = try {
        strictUtf16.decode(java.nio.ByteBuffer.wrap(this, offset, length)).toString()
    } catch (e: Exception) {
        null
    }

    private fun decodeUtf16(data: ByteArray, offset: Int, length: Int): String =
        strictUtf16.decode(java.nio.ByteBuffer.wrap(data, offset, length)).toString()

    private fun readU16(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)

    private fun readU32(data: ByteArray, offset: Int): Long =
        (readU16(data, offset).toLong() and 0xFFFF) or ((readU16(data, offset + 2).toLong() and 0xFFFF) shl 16)
}
