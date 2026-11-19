package com.sofatype.pinyin

class DictData(

    val syllables: List<String>,

    val words: List<String>,

    val entries: List<Entry>,
) {

    class Entry(

        val syllableIds: IntArray,

        val wordId: Int,

        val freq: Double,
    )

    val entryCount: Int get() = entries.size
}
