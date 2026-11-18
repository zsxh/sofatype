package com.sofatype.pinyin

import java.io.InputStream

/** 单条候选：词条汉字串 + 词库内建频次 */
data class Candidate(val word: String, val freq: Double)

/**
 * 拼音查询引擎（T9 / T83，FR-A8 / FR-E1 / 决策 28 / PRD_20261118 决策 D1-D2）。
 *
 * 查询口径（双路并行，FR-E1）：
 * - 全拼路：输入为全拼小写字母串；先切分出全部可能的首音节，再从对应词条桶做字母级前缀匹配；
 *   命中 = 词条全拼与输入相等（整词命中），或输入是词条全拼的真前缀（组合态增量命中）；
 * - 简拼路：词条首字母串（各音节首字母拼接，如 [zhong,guo] → "zg"）与输入做前缀匹配；
 * - 合并：单字母输入取简拼路结果（该输入下简拼路为全拼路超集，按频次统一排序）；
 *   ≥2 字母输入全拼命中排前、简拼命中按内建频次排后（词 id 去重）；
 * - 词组与单字均为词库词条，单字词兜底由词库自然覆盖（源库含 17 037 条单字词条）；
 * - 排序：各路词库内建频次降序；频次相同按源词库行序稳定排列；
 * - 同词多读音（多音字，源库 658 词多词条）去重为一个候选，频次取最高读音。
 */
class PinyinEngine private constructor(
    private val data: DictData,
    private val segmenter: PinyinSegmenter,
) {

    /** 词条全拼串（词条音节拼接，如 [ke,ting] → "keting"），下标与词条序一致 */
    private val quanpins: Array<String> = Array(data.entryCount) { i ->
        val e = data.entries[i]
        buildString(e.syllableIds.size * 4) {
            for (id in e.syllableIds) append(data.syllables[id])
        }
    }

    /** 词条首字母串（各音节首字母拼接，如 [zhong,guo] → "zg"），简拼路匹配键（FR-E1），下标与词条序一致 */
    private val initials: Array<String> = Array(data.entryCount) { i ->
        val e = data.entries[i]
        buildString(e.syllableIds.size) {
            for (id in e.syllableIds) append(data.syllables[id][0])
        }
    }

    /** 首音节倒排桶：首音节 → 词条下标数组（词条序升序；各桶按首音节互斥，无跨桶重复） */
    private val buckets: HashMap<String, IntArray>

    /** 首字母倒排桶：首字母 → 词条下标数组（词条序升序；简拼路索引，FR-E1） */
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

    /**
     * 查询候选。输入非法（空 / 含非小写字母 / 超长）时返回空列表；无命中时返回空列表。
     * [limit] 限制返回条数（<=0 时返回空列表）。
     */
    fun query(input: String, limit: Int = DEFAULT_LIMIT): List<Candidate> {
        if (limit <= 0 || input.length > MAX_INPUT_LEN) return emptyList()
        if (!PinyinSegmenter.isValidInput(input)) return emptyList()

        val full = rankedFull(input)
        val abbr = rankedAbbr(input)
        // 合并口径（FR-E1）：单字母取简拼路（该输入下为全拼路超集，频次统一排序）；
        // ≥2 字母全拼命中排前，简拼命中剔除与全拼重复的词后按频次排后
        val merged = when {
            input.length == 1 || full.isEmpty() -> abbr
            abbr.isEmpty() -> full
            else -> {
                val seen = HashSet<Int>(full.size * 2)
                for ((wordId, _) in full) seen.add(wordId)
                full + abbr.filter { (wordId, _) -> seen.add(wordId) }
            }
        }
        return merged.take(limit).map { (wordId, freq) -> Candidate(data.words[wordId], freq) }
    }

    /** 全拼路（FR-A8 现行口径）：首音节桶内「词条全拼以输入为前缀」的命中，去重排序 */
    private fun rankedFull(input: String): List<Pair<Int, Double>> {
        val heads = segmenter.firstSyllables(input)
        if (heads.isEmpty()) return emptyList()
        val matches = HashMap<Int, Pair<Double, Int>>()
        for (head in heads) {
            val bucket = buckets[head] ?: continue
            for (i in bucket) {
                // 命中：词条全拼以输入为前缀 —— 覆盖整词相等与组合态增量前缀两种情形
                if (!quanpins[i].startsWith(input)) continue
                accumulateMatch(matches, data.entries[i], i)
            }
        }
        return sortMatches(matches)
    }

    /** 简拼路（FR-E1）：首字母桶内「词条首字母串以输入为前缀」的命中，去重排序 */
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

    /**
     * 命中累计：同词多读音（同词 id 多词条）去重为一条，频次取最高读音；
     * 频次相同取词条序更小者，保证稳定排序。
     */
    private fun accumulateMatch(matches: HashMap<Int, Pair<Double, Int>>, entry: DictData.Entry, entryIndex: Int) {
        val prev = matches[entry.wordId]
        if (prev == null || entry.freq > prev.first || (entry.freq == prev.first && entryIndex < prev.second)) {
            matches[entry.wordId] = entry.freq to entryIndex
        }
    }

    /** 命中排序：主序内建频次降序，次序词条序升序（稳定），输出 (词 id, 频次) 列表 */
    private fun sortMatches(matches: HashMap<Int, Pair<Double, Int>>): List<Pair<Int, Double>> {
        if (matches.isEmpty()) return emptyList()
        return matches.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Pair<Double, Int>>> { it.value.first }.thenBy { it.value.second })
            .map { it.key to it.value.first }
    }

    /** 词库词条总数 */
    val entryCount: Int get() = data.entryCount

    /** 词库音节总数 */
    val syllableCount: Int get() = data.syllables.size

    companion object {
        /** 默认候选上限 */
        const val DEFAULT_LIMIT: Int = 20

        /** 查询输入最大字母数（防御上限；遥控逐键组合态远低于该值） */
        const val MAX_INPUT_LEN: Int = 20

        /** 从 SPD1 格式流加载引擎（调用方负责关闭流） */
        fun load(input: InputStream): PinyinEngine = fromData(DictFormat.read(input))

        /** 从内存词库数据构建引擎（测试与转换后校验复用） */
        internal fun fromData(data: DictData): PinyinEngine =
            PinyinEngine(data, PinyinSegmenter(data.syllables.toSet()))
    }
}
