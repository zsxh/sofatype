package com.sofatype.pinyin

import java.io.InputStream

/** 单条候选：词条汉字串 + 词库内建频次 */
data class Candidate(val word: String, val freq: Double)

/**
 * 拼音查询引擎（T9，FR-A8 / 决策 28）。
 *
 * 查询口径：
 * - 输入为全拼小写字母串；先切分出全部可能的首音节，再从对应词条桶做字母级前缀匹配；
 * - 命中 = 词条全拼与输入相等（整词命中），或输入是词条全拼的真前缀（组合态增量命中）；
 * - 词组与单字均为词库词条，单字词兜底由词库自然覆盖（源库含 17 037 条单字词条）；
 * - 排序：词库内建频次降序；频次相同按源词库行序稳定排列；
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

    /** 首音节倒排桶：首音节 → 词条下标数组（词条序升序；各桶按首音节互斥，无跨桶重复） */
    private val buckets: HashMap<String, IntArray>

    init {
        val grouped = HashMap<String, MutableList<Int>>()
        for (i in 0 until data.entryCount) {
            val first = data.syllables[data.entries[i].syllableIds[0]]
            grouped.getOrPut(first) { ArrayList() }.add(i)
        }
        buckets = HashMap(grouped.size)
        for ((k, v) in grouped) buckets[k] = v.toIntArray()
    }

    /**
     * 查询候选。输入非法（空 / 含非小写字母 / 超长）时返回空列表；
     * 无合法切分或无命中时返回空列表。[limit] 限制返回条数（<=0 时返回空列表）。
     */
    fun query(input: String, limit: Int = DEFAULT_LIMIT): List<Candidate> {
        if (limit <= 0 || input.length > MAX_INPUT_LEN) return emptyList()
        val heads = segmenter.firstSyllables(input)
        if (heads.isEmpty()) return emptyList()

        // 聚合：词 id → 最高频次 + 该频次对应词条序。
        // 同词多读音去重为一条候选；频次相同取词条序更小者，保证稳定排序。
        val bestFreq = HashMap<Int, Double>()
        val bestOrder = HashMap<Int, Int>()
        for (head in heads) {
            val bucket = buckets[head] ?: continue
            for (i in bucket) {
                val qp = quanpins[i]
                // 命中：词条全拼以输入为前缀 —— 覆盖整词相等与组合态增量前缀两种情形
                if (qp.startsWith(input)) {
                    val wordId = data.entries[i].wordId
                    val freq = data.entries[i].freq
                    val prev = bestFreq[wordId]
                    if (prev == null || freq > prev) {
                        bestFreq[wordId] = freq
                        bestOrder[wordId] = i
                    } else if (freq == prev && i < bestOrder[wordId]!!) {
                        bestOrder[wordId] = i
                    }
                }
            }
        }
        if (bestFreq.isEmpty()) return emptyList()

        // 待排序项：(词 id, 频次, 词条序)。主序内建频次降序，次序词条序升序（稳定）。
        val items = ArrayList<Triple<Int, Double, Int>>(bestFreq.size)
        for ((wordId, freq) in bestFreq) items.add(Triple(wordId, freq, bestOrder[wordId] ?: 0))
        items.sortWith(compareByDescending<Triple<Int, Double, Int>> { it.second }.thenBy { it.third })

        val out = ArrayList<Candidate>(minOf(limit, items.size))
        for (item in items) {
            if (out.size == limit) break
            out.add(Candidate(data.words[item.first], item.second))
        }
        return out
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
