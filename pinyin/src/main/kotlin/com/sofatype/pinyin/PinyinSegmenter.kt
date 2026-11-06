package com.sofatype.pinyin

/**
 * 拼音切分器（T9）。
 *
 * 音节表以词库数据为准（决策 28 口径）：由词库转换产物中的全部无调音节构建。
 * 输入为全拼小写字母串，输出合法切分方案。
 */
class PinyinSegmenter(syllableSet: Set<String>) {

    init {
        require(syllableSet.isNotEmpty()) { "音节表不能为空" }
        require(syllableSet.all { it.isNotEmpty() && it.all { c -> c in 'a'..'z' } }) {
            "音节表必须为非空小写字母串"
        }
        // 公开构造防御：音节超长会让 byLength[s.length] 越界（表长 = MAX_SYLLABLE_LEN + 1），
        // 实际路径由词库产物保证（音节最长 6 字母），此处显式校验给出明确报错
        require(syllableSet.all { it.length <= MAX_SYLLABLE_LEN }) {
            "音节长度不能超过 $MAX_SYLLABLE_LEN 字母：${syllableSet.filter { it.length > MAX_SYLLABLE_LEN }}"
        }
    }

    /** 按长度分组索引：音节最长 6 字母（如 zhuang），长度 1..6 各自一个集合（下标 0 弃用） */
    private val byLength: Array<HashSet<String>> = Array(MAX_SYLLABLE_LEN + 1) { HashSet() }

    init {
        for (s in syllableSet) byLength[s.length].add(s)
    }

    /**
     * 输入串从位置 0 开始的全部合法音节，长优先（如 "xian" → [xian, xi]）。
     * 输入非法或无合法音节时返回空列表。
     */
    fun firstSyllables(input: String): List<String> {
        if (!isValidInput(input)) return emptyList()
        val result = ArrayList<String>(2)
        for (len in MAX_SYLLABLE_LEN downTo 1) {
            if (input.length < len) continue
            if (byLength[len].contains(input.substring(0, len))) result.add(input.substring(0, len))
        }
        return result
    }

    /**
     * 输入串的全部切分方案（音节序列列表），各方案内按切分位置自然排列。
     * 输入非法时返回空列表。
     * 组合数有上限保护（[MAX_PLANS]），超限时只保留已枚举方案，不再扩展。
     */
    fun segmentAll(input: String): List<List<String>> {
        if (!isValidInput(input)) return emptyList()
        val plans = ArrayList<List<String>>()
        val current = ArrayList<String>()
        dfs(input, 0, current, plans)
        return plans
    }

    /** 深度优先枚举从 [start] 起的全部切分 */
    private fun dfs(input: String, start: Int, current: MutableList<String>, plans: MutableList<List<String>>) {
        if (plans.size >= MAX_PLANS) return // 组合数上限保护
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
        /** 拼音全拼音节最大字母数 */
        const val MAX_SYLLABLE_LEN: Int = 6

        /** 切分方案组合数上限（防御性；正常组合态输入远低于该值） */
        const val MAX_PLANS: Int = 256

        /** 输入合法性：非空且全部为小写字母 */
        fun isValidInput(input: String): Boolean =
            input.isNotEmpty() && input.all { it in 'a'..'z' }
    }
}
