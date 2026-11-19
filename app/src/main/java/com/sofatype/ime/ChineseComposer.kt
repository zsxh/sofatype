package com.sofatype.ime

import com.sofatype.pinyin.PinyinEngine

class ChineseComposer(private val queryCandidates: (String) -> List<String>) {

    val segmentMark: Char = '\''

    var composition: String = ""
        private set

    var candidates: List<String> = emptyList()
        private set

    val hasCandidates: Boolean get() = candidates.isNotEmpty()

    fun appendLetter(letter: Char) {
        if (composition.length >= PinyinEngine.MAX_INPUT_LEN) return
        composition += letter.lowercaseChar()
        refresh()
    }

    fun appendSegment(): Boolean {
        if (composition.isEmpty()) return false
        if (composition.length >= PinyinEngine.MAX_INPUT_LEN) return false
        composition += segmentMark
        refresh()
        return true
    }

    fun deleteLastLetter(): Boolean {
        if (composition.isEmpty()) return false
        composition = composition.dropLast(1)
        refresh()
        return true
    }

    fun firstCandidate(): String? = candidates.firstOrNull()

    fun reset() {
        composition = ""
        candidates = emptyList()
    }

    fun queryInput(): String = stripSegmentMarks(composition)

    fun committedText(): String = stripSegmentMarks(composition)

    private fun refresh() {
        candidates = if (composition.isEmpty()) emptyList() else queryCandidates(queryInput())
    }

    private fun stripSegmentMarks(source: String): String = source.replace(segmentMark.toString(), "")
}
