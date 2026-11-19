package com.sofatype.pinyin

fun DictData.topFreqSubset(limit: Int): DictData {
    require(limit > 0) { "子集词条数必须为正：$limit" }
    val picked = entries.withIndex()
        .sortedWith(compareByDescending<IndexedValue<DictData.Entry>> { it.value.freq }.thenBy { it.index })
        .take(limit)
        .map { it.value }

    val wordRemap = HashMap<Int, Int>()
    val newWords = ArrayList<String>(picked.size)
    for (entry in picked) {
        if (wordRemap.putIfAbsent(entry.wordId, newWords.size) == null) {
            newWords.add(words[entry.wordId])
        }
    }
    val newEntries = picked.map { entry ->
        DictData.Entry(entry.syllableIds, wordRemap.getValue(entry.wordId), entry.freq)
    }
    return DictData(syllables, newWords, newEntries)
}
