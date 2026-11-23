package com.sofatype.pinyin

object DictHotMerge {

    fun mergeBaseWithHot(
        base: DictData,
        hotDicts: List<DictData>,
    ): DictData {
        if (hotDicts.isEmpty()) return base

        val syllables = ArrayList<String>(base.syllables)
        val syllableId = HashMap<String, Int>(syllables.size * 2)
        syllables.forEachIndexed { index, s -> syllableId[s] = index }

        val words = ArrayList<String>(base.words)
        val wordId = HashMap<String, Int>(words.size * 2)
        words.forEachIndexed { index, w -> wordId[w] = index }

        val entries = ArrayList<DictData.Entry>(base.entryCount + hotDicts.sumOf { it.entryCount })
        val seenKeys = HashSet<String>(base.entryCount * 2)
        for (entry in base.entries) {
            entries.add(entry)
            seenKeys.add(entryKey(entry.syllableIds.map { syllables[it] }, words[entry.wordId]))
        }

        for (hot in hotDicts) {
            for (entry in hot.entries) {
                val texts = entry.syllableIds.map { raw ->
                    val text = hot.syllables[raw]
                    if (syllableId[text] == null) {
                        syllableId[text] = syllables.size
                        syllables.add(text)
                    }
                    text
                }
                val wordText = hot.words[entry.wordId]
                if (!seenKeys.add(entryKey(texts, wordText))) continue
                if (wordId[wordText] == null) {
                    wordId[wordText] = words.size
                    words.add(wordText)
                }
                require(entry.freq.isFinite()) { "热词频次非法：${entry.freq}" }
                entries.add(
                    DictData.Entry(
                        IntArray(texts.size) { i -> syllableId.getValue(texts[i]) },
                        wordId.getValue(wordText),
                        entry.freq,
                    ),
                )
            }
        }

        return DictData(syllables, words, entries)
    }

    private fun entryKey(syllables: List<String>, word: String): String =
        syllables.joinToString(separator = " ") + "|" + word
}
