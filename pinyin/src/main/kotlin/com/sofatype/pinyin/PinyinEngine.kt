package com.sofatype.pinyin

import java.io.InputStream

/** 单条候选：词条汉字串 + 词库内建频次 */
data class Candidate(val word: String, val freq: Double)

/**
 * 拼音查询引擎（T9 / T83 / T84，FR-A8 / FR-E1 / 决策 28 / PRD_20261118 决策 D1-D2 / D6）。
 *
 * 查询口径（多路并行，FR-E1 + T84 混合缩写）：
 * - 全拼路：输入为全拼小写字母串；先切分出全部可能的首音节，再从对应词条桶做字母级前缀匹配；
 *   命中 = 词条全拼与输入相等（整词命中），或输入是词条全拼的真前缀（组合态增量命中）；
 * - 简拼路：词条首字母串（各音节首字母拼接，如 [zhong,guo] → "zg"）与输入做前缀匹配；
 * - 混合缩写路（T84 · 决策 D6）：逐音节「全拼 / 缩写」混合匹配——输入按音节位分段，每段可为
 *   整音节（全拼位）、首字母缩写（1 字母）或 zh/ch/sh 整声母缩写（2 字母），末段还允许为音节
 *   真前缀（组合态增量）；覆盖 zguo → 中国（缩写位 + 全拼位）、zhly 声母形态等一期两路之外的形态；
 * - 合并：单字母输入取简拼路结果（该输入下简拼路为全拼路超集，按频次统一排序）；
 *   ≥2 字母输入三层级合并：全拼命中排前 → 简拼命中剔重排后 → 混合缩写命中再剔重殿后
 *   （词 id 去重，同词只保留首个命中的层级；一期两路排序口径原样保留）；
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
        // 合并口径（FR-E1 + T84 决策 D6）：单字母取简拼路（该输入下为全拼路超集，频次统一排序；
        // 单字母下混合路与简拼路命中集合相同，不另跑路）；
        // ≥2 字母三层级：全拼命中排前，简拼命中剔除与全拼重复的词后按频次排后，
        // 混合缩写命中（T84 新增路）剔除两路已命中的词后殿后——一期两路排序原样保留
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
     * 混合缩写路（T84 · PRD_20261118 决策 D6）：逐音节「全拼 / 缩写」混合匹配。
     * 候选集扫描沿用首字母倒排桶（任何合法分段的首段首字母必同词条首音节首字母，与简拼路同桶）；
     * 逐词条做 [mixedSegmentMatch] 动态规划；[exclude] = 全拼路 + 简拼路已命中的词 id 并集，
     * 两路已命中的词不再进本路（保持三层级合并去重口径）。
     */
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

    /**
     * 逐音节双状态匹配（T84）：输入按位置推进的可达音节位 DP。
     * reach[p][i] = 消费到输入位置 p、已完成音节位 0..i-1（各以整音节或缩写收边界）时可达；
     * 音节位 i 的四类消费：
     * ① 整音节（全拼位）；② zh/ch/sh 整声母缩写（2 字母，声母简拼形态）；
     * ③ 首字母缩写（1 字母）；④ 音节真前缀恰好吃满输入（组合态增量，只允许末段）。
     * 状态数上界 = 输入长 × 音节数，无组合数爆炸（对应切分器 MAX_PLANS 防御口径）。
     */
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
                // ① 整音节（全拼位）
                if (len - p >= syl.length && input.regionMatches(p, syl, 0, syl.length)) {
                    val next = p + syl.length
                    if (next == len) matched = true else if (i + 1 < n) reach[next][i + 1] = true
                }
                // ② 声母缩写位：zh/ch/sh 整声母（词条音节表内该形态音节长 ≥2）
                if (syl.length >= 2 && isRetroflexInitial(syl) &&
                    len - p >= 2 && input.regionMatches(p, syl, 0, 2)
                ) {
                    val next = p + 2
                    if (next == len) matched = true else if (i + 1 < n) reach[next][i + 1] = true
                }
                // ③ 首字母缩写位
                if (input[p] == syl[0]) {
                    val next = p + 1
                    if (next == len) matched = true else if (i + 1 < n) reach[next][i + 1] = true
                }
                // ④ 组合态增量：末段为音节真前缀且恰好吃满输入（整音节情形已由 ① 覆盖）
                if (len - p < syl.length && syl.regionMatches(0, input, p, len - p)) matched = true
            }
        }
        return matched
    }

    /** 翘舌声母判定：音节以 zh / ch / sh 开头（声母简拼形态的缩写键，T84） */
    private fun isRetroflexInitial(syllable: String): Boolean =
        syllable[1] == 'h' && (syllable[0] == 'z' || syllable[0] == 'c' || syllable[0] == 's')

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
