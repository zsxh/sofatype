package com.sofatype.pinyin

object DictPackMerge {

    const val DEFAULT_PACK_FREQ_BOOST: Double = 10.0

    fun mergeBaseWithPacks(
        base: DictData,
        packs: List<DictData>,
        packFreqBoost: Double = DEFAULT_PACK_FREQ_BOOST,
    ): DictData {
        require(packFreqBoost.isFinite() && packFreqBoost > 0.0) { "词包频次加权系数非法：$packFreqBoost" }
        if (packs.isEmpty()) return base

        val syllables = ArrayList<String>(base.syllables)
        val syllableId = HashMap<String, Int>(syllables.size * 2)
        syllables.forEachIndexed { index, s -> syllableId[s] = index }

        val words = ArrayList<String>(base.words)
        val wordId = HashMap<String, Int>(words.size * 2)
        words.forEachIndexed { index, w -> wordId[w] = index }

        val entries = ArrayList<DictData.Entry>(base.entryCount + packs.sumOf { it.entryCount })
        val seenKeys = HashSet<String>(base.entryCount * 2)
        for (entry in base.entries) {
            entries.add(entry)
            seenKeys.add(entryKey(entry.syllableIds.map { syllables[it] }, words[entry.wordId]))
        }

        for (pack in packs) {
            for (entry in pack.entries) {
                val texts = entry.syllableIds.map { raw ->
                    val text = pack.syllables[raw]
                    if (syllableId.putIfAbsent(text, syllables.size) == null) {
                        syllables.add(text)
                    }
                    text
                }
                val wordText = pack.words[entry.wordId]
                if (!seenKeys.add(entryKey(texts, wordText))) continue
                val mappedWordId = wordId.putIfAbsent(wordText, words.size)
                if (mappedWordId == null) {
                    words.add(wordText)
                }
                val boosted = entry.freq * packFreqBoost
                require(boosted.isFinite()) { "词包频次加权后非法：${entry.freq} × $packFreqBoost" }
                entries.add(
                    DictData.Entry(
                        IntArray(texts.size) { i -> syllableId.getValue(texts[i]) },
                        wordId.getValue(wordText),
                        boosted,
                    ),
                )
            }
        }

        return DictData(syllables, words, entries)
    }

    private fun entryKey(syllables: List<String>, word: String): String =
        syllables.joinToString(separator = " ") + "|" + word
}
