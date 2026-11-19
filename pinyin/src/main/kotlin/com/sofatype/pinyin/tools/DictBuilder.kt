package com.sofatype.pinyin.tools

import com.sofatype.pinyin.DictData

object DictBuilder {

    class Stats(
        val lineCount: Int,
        val entryCount: Int,
        val skippedLineCount: Int,
        val syllableCount: Int,
        val wordCount: Int,
    )

    fun build(lines: Sequence<String>): Pair<DictData, Stats> {
        var lineCount = 0
        var skipped = 0

        val syllableSet = HashSet<String>(512)
        val wordIndex = HashMap<String, Int>(65_105)
        val words = ArrayList<String>(65_105)
        val entrySyllableTexts = ArrayList<List<String>>(65_105)
        val entryWordIds = ArrayList<Int>(65_105)
        val entryFreqs = ArrayList<Double>(65_105)

        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            lineCount++
            val t = line.split(' ').filter { it.isNotEmpty() }
            if (t.size < 4) { skipped++; continue }
            val word = t[0]
            val freq = t[1].toDoubleOrNull()
            if (freq == null) { skipped++; continue }
            val sylTexts = t.subList(3, t.size)
            if (sylTexts.size != word.length) { skipped++; continue }
            if (sylTexts.any { s -> s.isEmpty() || s.any { c -> c !in 'a'..'z' } }) { skipped++; continue }

            val wordId = wordIndex.getOrPut(word) {
                words.add(word)
                words.size - 1
            }
            syllableSet.addAll(sylTexts)
            entrySyllableTexts.add(sylTexts.toList())
            entryWordIds.add(wordId)
            entryFreqs.add(freq)
        }

        val syllables = syllableSet.sorted()
        val syllableId = HashMap<String, Int>(syllables.size)
        syllables.forEachIndexed { i, s -> syllableId[s] = i }

        val entries = ArrayList<DictData.Entry>(entrySyllableTexts.size)
        for (i in entrySyllableTexts.indices) {
            val texts = entrySyllableTexts[i]
            val ids = IntArray(texts.size) { j -> syllableId[texts[j]]!! }
            entries.add(DictData.Entry(ids, entryWordIds[i], entryFreqs[i]))
        }

        return Pair(
            DictData(syllables, words, entries),
            Stats(lineCount, entries.size, skipped, syllables.size, words.size),
        )
    }
}
