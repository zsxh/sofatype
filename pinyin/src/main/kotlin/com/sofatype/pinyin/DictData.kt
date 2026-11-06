package com.sofatype.pinyin

/**
 * 词库内存数据结构（T9 · 拼音引擎独立模块）。
 *
 * 数据来自 AOSP 谷歌拼音词库（决策 28）构建期转换产物，含三要素：
 * 词条汉字串、逐字拼音音节序列、词库内建频次（浮点，原样保留保排序序）。
 */
class DictData(
    /** 去重后的拼音音节表（无调小写形式，如 "ke"、"ting"），下标即音节 id */
    val syllables: List<String>,
    /** 去重后的词条汉字表，下标即词 id；多音字词只入池一次 */
    val words: List<String>,
    /** 词条列表，顺序 = 源词库行序（频次相同时按此顺序保持稳定） */
    val entries: List<Entry>,
) {
    /** 一条词条：音节 id 序列 + 词 id + 词库内建频次 */
    class Entry(
        /** 逐字拼音音节 id 序列，长度与词条字数一致 */
        val syllableIds: IntArray,
        /** 词表下标 */
        val wordId: Int,
        /** 词库内建频次（浮点原样保留） */
        val freq: Double,
    )

    /** 词条总数 */
    val entryCount: Int get() = entries.size
}
