package com.sofatype.pinyin

class PinyinSegmenter(syllableSet: Set<String>) {

    init {
        require(syllableSet.isNotEmpty()) { "音节表不能为空" }
        require(syllableSet.all { it.isNotEmpty() && it.all { c -> c in 'a'..'z' } }) {
            "音节表必须为非空小写字母串"
        }

        require(syllableSet.all { it.length <= MAX_SYLLABLE_LEN }) {
            "音节长度不能超过 $MAX_SYLLABLE_LEN 字母：${syllableSet.filter { it.length > MAX_SYLLABLE_LEN }}"
        }
    }

    private val byLength: Array<HashSet<String>> = Array(MAX_SYLLABLE_LEN + 1) { HashSet() }

    init {
        for (s in syllableSet) byLength[s.length].add(s)
    }

    fun firstSyllables(input: String): List<String> {
        if (!isValidInput(input)) return emptyList()
        val result = ArrayList<String>(2)
        for (len in MAX_SYLLABLE_LEN downTo 1) {
            if (input.length < len) continue
            if (byLength[len].contains(input.substring(0, len))) result.add(input.substring(0, len))
        }
        return result
    }

    fun segmentAll(input: String): List<List<String>> {
        if (!isValidInput(input)) return emptyList()
        val plans = ArrayList<List<String>>()
        val current = ArrayList<String>()
        dfs(input, 0, current, plans)
        return plans
    }

    private fun dfs(input: String, start: Int, current: MutableList<String>, plans: MutableList<List<String>>) {
        if (plans.size >= MAX_PLANS) return
        if (start == input.length) {
            plans.add(current.toList())
            return
        }
        val maxLen = minOf(MAX_SYLLABLE_LEN, input.length - start)
        for (len in maxLen downTo 1) {
            val token = input.substring(start, start + len)
            if (!byLength[len].contains(token)) continue
            current.add(token)
            dfs(input, start + len, current, plans)
            current.removeAt(current.size - 1)
        }
    }

    companion object {

        const val MAX_SYLLABLE_LEN: Int = 6

        const val MAX_PLANS: Int = 256

        fun isValidInput(input: String): Boolean =
            input.isNotEmpty() && input.all { it in 'a'..'z' }
    }
}
