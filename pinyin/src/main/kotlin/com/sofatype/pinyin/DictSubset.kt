package com.sofatype.pinyin

/**
 * 高频子集提取（T85 · 方向 C 高频分段加载）。
 *
 * 截断口径：按词条内建频次降序选取，频次并列按源词库行序（与引擎排序的稳定口径一致），
 * 取前 [limit] 条词条；音节表全量保留（切分器由音节表构建，必须与全量产物同语义）；
 * 词表紧缩重映射（只保留子集引用的词，新下标随选取序排列）。
 * 纯函数、确定性：同一输入恒产生相同输出。
 */
fun DictData.topFreqSubset(limit: Int): DictData {
    require(limit > 0) { "子集词条数必须为正：$limit" }
    val picked = entries.withIndex()
        .sortedWith(compareByDescending<IndexedValue<DictData.Entry>> { it.value.freq }.thenBy { it.index })
        .take(limit)
        .map { it.value }

    // 词重映射：子集引用词 → 新词 id（首见序 = 频次序），多音字同词多词条共享同一新 id
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
