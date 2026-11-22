package com.sofatype.pinyin

import java.io.InputStream

data class Candidate(val word: String, val freq: Double)

class PinyinEngine private constructor(
    private val data: DictData,
    private val segmenter: PinyinSegmenter,
) {

    private val quanpins: Array<String> = Array(data.entryCount) { i ->
        val e = data.entries[i]
        buildString(e.syllableIds.size * 4) {
            for (id in e.syllableIds) append(data.syllables[id])
        }
    }

    private val initials: Array<String> = Array(data.entryCount) { i ->
        val e = data.entries[i]
        buildString(e.syllableIds.size) {
            for (id in e.syllableIds) append(data.syllables[id][0])
        }
    }

    private val buckets: HashMap<String, IntArray>

    private val initialBuckets: HashMap<Char, IntArray>

    init {
        val grouped = HashMap<String, MutableList<Int>>()
        val byInitial = HashMap<Char, MutableList<Int>>()
        for (i in 0 until data.entryCount) {
            val first = data.syllables[data.entries[i].syllableIds[0]]
            grouped.getOrPut(first) { ArrayList() }.add(i)
            byInitial.getOrPut(initials[i][0]) { ArrayList() }.add(i)
        }
        buckets = HashMap(grouped.size)
        for ((k, v) in grouped) buckets[k] = v.toIntArray()
        initialBuckets = HashMap(byInitial.size)
        for ((k, v) in byInitial) initialBuckets[k] = v.toIntArray()
    }

    fun query(input: String, limit: Int = DEFAULT_LIMIT): List<Candidate> {
        if (limit <= 0 || input.length > MAX_INPUT_LEN) return emptyList()
        if (!PinyinSegmenter.isValidInput(input)) return emptyList()

        val full = rankedFull(input)
        val abbr = rankedAbbr(input)

        val merged = when {
            input.length == 1 -> abbr
            else -> {
                val seen = HashSet<Int>(full.size * 2 + abbr.size * 2)
                for ((wordId, _) in full) seen.add(wordId)
                val abbrOnly = ArrayList<Pair<Int, Double>>(abbr.size)
                for (hit in abbr) if (seen.add(hit.first)) abbrOnly.add(hit)
                val mixed = rankedMixed(input, seen)
                full + abbrOnly + mixed
            }
        }
        return merged.take(limit).map { (wordId, freq) -> Candidate(data.words[wordId], freq) }
    }

    private fun rankedFull(input: String): List<Pair<Int, Double>> {
        val heads = segmenter.firstSyllables(input)
        if (heads.isEmpty()) return emptyList()
        val matches = HashMap<Int, Pair<Double, Int>>()
        for (head in heads) {
            val bucket = buckets[head] ?: continue
            for (i in bucket) {

                if (!quanpins[i].startsWith(input)) continue
                accumulateMatch(matches, data.entries[i], i)
            }
        }
        return sortMatches(matches)
    }

    private fun rankedAbbr(input: String): List<Pair<Int, Double>> {
        if (input.isEmpty()) return emptyList()
        val bucket = initialBuckets[input[0]] ?: return emptyList()
        val matches = HashMap<Int, Pair<Double, Int>>()
        for (i in bucket) {
            if (!initials[i].startsWith(input)) continue
            accumulateMatch(matches, data.entries[i], i)
        }
        return sortMatches(matches)
    }

    private fun rankedMixed(input: String, exclude: HashSet<Int>): List<Pair<Int, Double>> {
        if (input.isEmpty()) return emptyList()
        val bucket = initialBuckets[input[0]] ?: return emptyList()
        val matches = HashMap<Int, Pair<Double, Int>>()
        for (i in bucket) {
            val entry = data.entries[i]
            if (entry.wordId in exclude) continue
            if (!mixedSegmentMatch(input, entry)) continue
            accumulateMatch(matches, data.entries[i], i)
        }
        return sortMatches(matches)
    }

    private fun mixedSegmentMatch(input: String, entry: DictData.Entry): Boolean {
        val n = entry.syllableIds.size
        val len = input.length
        val reach = Array(len + 1) { BooleanArray(n) }
        reach[0][0] = true
        var matched = false
        for (p in 0 until len) {
            for (i in 0 until n) {
                if (!reach[p][i]) continue
                val syl = data.syllables[entry.syllableIds[i]]

                if (len - p >= syl.length && input.regionMatches(p, syl, 0, syl.length)) {
                    val next = p + syl.length
                    if (next == len) matched = true else if (i + 1 < n) reach[next][i + 1] = true
                }

                if (syl.length >= 2 && isRetroflexInitial(syl) &&
                    len - p >= 2 && input.regionMatches(p, syl, 0, 2)
                ) {
                    val next = p + 2
                    if (next == len) matched = true else if (i + 1 < n) reach[next][i + 1] = true
                }

                if (input[p] == syl[0]) {
                    val next = p + 1
                    if (next == len) matched = true else if (i + 1 < n) reach[next][i + 1] = true
                }

                if (len - p < syl.length && syl.regionMatches(0, input, p, len - p)) matched = true
            }
        }
        return matched
    }

    private fun isRetroflexInitial(syllable: String): Boolean =
        syllable[1] == 'h' && (syllable[0] == 'z' || syllable[0] == 'c' || syllable[0] == 's')

    private fun accumulateMatch(matches: HashMap<Int, Pair<Double, Int>>, entry: DictData.Entry, entryIndex: Int) {
        val prev = matches[entry.wordId]
        if (prev == null || entry.freq > prev.first || (entry.freq == prev.first && entryIndex < prev.second)) {
            matches[entry.wordId] = entry.freq to entryIndex
        }
    }

    private fun sortMatches(matches: HashMap<Int, Pair<Double, Int>>): List<Pair<Int, Double>> {
        if (matches.isEmpty()) return emptyList()
        return matches.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Pair<Double, Int>>> { it.value.first }.thenBy { it.value.second })
            .map { it.key to it.value.first }
    }

    val entryCount: Int get() = data.entryCount

    val syllableCount: Int get() = data.syllables.size

    companion object {

        const val DEFAULT_LIMIT: Int = 20

        const val MAX_INPUT_LEN: Int = 20

        fun load(input: InputStream): PinyinEngine = fromData(DictFormat.read(input))

        fun fromData(data: DictData): PinyinEngine =
            PinyinEngine(data, PinyinSegmenter(data.syllables.toSet()))
    }
}
