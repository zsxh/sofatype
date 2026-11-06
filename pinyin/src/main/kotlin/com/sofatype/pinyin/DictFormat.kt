package com.sofatype.pinyin

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * SPD1（SofaType Pinyin Dict v1）自有精简格式的读写（T9，决策 28 构建期转换产物）。
 *
 * 二进制布局（全部小端由 Java DataOutputStream 的 big-endian 写出，见下方逐字段说明）：
 * ```
 * 魔数       4 字节  "SPD1"
 * 版本       int32   1
 * 音节数     int32   N
 *   音节表   N × (uint8 字节长 + UTF-8 字节串)          （音节最长 6 字母）
 * 词数       int32   M
 *   词表     M × (uint16 字节长 + UTF-8 字节串)
 * 词条数     int32   E
 *   词条     E × {
 *     uint8   音节个数 n（与词字数一致，1..6）
 *     n × uint16 音节 id（音节表下标；源库 416 个音节，id 超出单字节范围，必须双字节）
 *     uint16  词 id（词表下标，无符号读取）
 *     double  词库内建频次（浮点原样保留，保证排序序无损）
 *   }
 * ```
 * 顺序口径：词条保持源词库行序写入；频次相同的词条在候选排序中按该行序稳定排列。
 */
object DictFormat {

    /** 魔数 */
    private val MAGIC = byteArrayOf('S'.code.toByte(), 'P'.code.toByte(), 'D'.code.toByte(), '1'.code.toByte())

    /** 当前格式版本 */
    const val VERSION: Int = 1

    /** 单音节最大字母数（拼音全拼音节最长 6，如 zhuang） */
    private const val MAX_SYLLABLE_BYTES = 6

    /** 单词条最大字数（源词库实测最长 4 字，防御上限放宽到 8） */
    private const val MAX_WORD_BYTES = 64

    /** 把 [data] 以 SPD1 格式写入 [output]（调用方负责关闭流） */
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
                // 音节 id 走 uint16：源库音节池 416 个，单字节存不下，避免高位静默截断
                out.writeShort(id)
            }
            require(e.wordId in 0 until data.words.size) { "词 id 越界：${e.wordId}" }
            out.writeShort(e.wordId)
            out.writeDouble(e.freq)
        }
        out.flush()
    }

    /** 从 [input] 读取 SPD1 格式（调用方负责关闭流） */
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
