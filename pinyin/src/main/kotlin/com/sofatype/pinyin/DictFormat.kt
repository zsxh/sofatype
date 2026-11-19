package com.sofatype.pinyin

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

object DictFormat {

    private val MAGIC = byteArrayOf('S'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), '1'.code.toByte())

    const val VERSION: Int = 1

    private const val MAX_SYLLABLE_BYTES = 6

    private const val MAX_WORD_BYTES = 64

    fun write(data: DictData, output: OutputStream) {
        val out = DataOutputStream(output.buffered())
        out.write(MAGIC)
        out.writeInt(VERSION)

        out.writeInt(data.syllables.size)
        for (s in data.syllables) {
            val bytes = s.toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_SYLLABLE_BYTES) { "音节超长：$s" }
            out.writeByte(bytes.size)
            out.write(bytes)
        }

        out.writeInt(data.words.size)
        for (w in data.words) {
            val bytes = w.toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_WORD_BYTES) { "词超长：$w" }
            out.writeShort(bytes.size)
            out.write(bytes)
        }

        out.writeInt(data.entries.size)
        for (e in data.entries) {
            require(e.syllableIds.size <= MAX_SYLLABLE_BYTES) { "词条音节数超限" }
            out.writeByte(e.syllableIds.size)
            for (id in e.syllableIds) {
                require(id in 0 until data.syllables.size) { "音节 id 越界：$id" }

                out.writeShort(id)
            }
            require(e.wordId in 0 until data.words.size) { "词 id 越界：${e.wordId}" }
            out.writeShort(e.wordId)
            out.writeDouble(e.freq)
        }
        out.flush()
    }

    fun read(input: InputStream): DictData {
        val ins = DataInputStream(input.buffered())

        val magic = ByteArray(MAGIC.size)
        ins.readFully(magic)
        require(magic.contentEquals(MAGIC)) { "非 SPD1 词库文件（魔数不符）" }

        val version = ins.readInt()
        require(version == VERSION) { "不支持的 SPD1 版本：$version" }

        val syllableCount = ins.readInt()
        require(syllableCount >= 0) { "音节数非法：$syllableCount" }
        val syllables = ArrayList<String>(syllableCount)
        repeat(syllableCount) {
            val len = ins.readUnsignedByte()
            require(len in 1..MAX_SYLLABLE_BYTES) { "音节字节长非法：$len" }
            val bytes = ByteArray(len)
            ins.readFully(bytes)
            syllables.add(String(bytes, Charsets.UTF_8))
        }

        val wordCount = ins.readInt()
        require(wordCount >= 0) { "词数非法：$wordCount" }
        val words = ArrayList<String>(wordCount)
        repeat(wordCount) {
            val len = ins.readUnsignedShort()
            require(len in 1..MAX_WORD_BYTES) { "词字节长非法：$len" }
            val bytes = ByteArray(len)
            ins.readFully(bytes)
            words.add(String(bytes, Charsets.UTF_8))
        }

        val entryCount = ins.readInt()
        require(entryCount >= 0) { "词条数非法：$entryCount" }
        val entries = ArrayList<DictData.Entry>(entryCount)
        repeat(entryCount) {
            val n = ins.readUnsignedByte()
            require(n in 1..MAX_SYLLABLE_BYTES) { "词条音节数非法：$n" }
            val ids = IntArray(n)
            for (i in 0 until n) {
                val id = ins.readUnsignedShort()
                require(id < syllableCount) { "音节 id 越界：$id" }
                ids[i] = id
            }
            val wordId = ins.readUnsignedShort()
            require(wordId < wordCount) { "词 id 越界：$wordId" }
            val freq = ins.readDouble()
            require(freq.isFinite()) { "频次非法：$freq" }
            entries.add(DictData.Entry(ids, wordId, freq))
        }

        return DictData(syllables, words, entries)
    }
}
