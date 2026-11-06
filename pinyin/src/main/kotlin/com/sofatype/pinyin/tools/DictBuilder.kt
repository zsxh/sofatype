package com.sofatype.pinyin.tools

import com.sofatype.pinyin.DictData

/**
 * AOSP 谷歌拼音词库源文本解析（T9，决策 28）。
 *
 * 源文件格式（UTF-16 文本，每行一条，空格分隔）：
 * ```
 * 词 内建频次(浮点) 标志字段 逐字拼音音节1 音节2 ...
 * ```
 * 实例：`欧美国家 141.29744947 0 ou mei guo jia`
 *
 * 已验证事实（2026-11-06，65 105 行全量核对）：
 * - 音节 token 数与词条字数一一对应；音节全为无调小写字母，共 416 个不同音节；
 * - 频次字段全为可解析浮点；每字一音（无单行多音），多音字体现为同词多行（658 词）；
 * - 第 3 列标志字段语义无官方文档确认，转换时忽略（词 / 频次 / 拼音三要素不受影响）。
 */
object DictBuilder {

    /** 解析统计 */
    class Stats(
        val lineCount: Int,
        val entryCount: Int,
        val skippedLineCount: Int,
        val syllableCount: Int,
        val wordCount: Int,
    )

    /**
     * 把源文本行解析为词库数据；不合规行跳过并计数。
     * 音节表去重排序、词条保持源行序，同一份源文件产出字节级可复现的产物。
     */
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

            // 词池：按首次出现行序入池，多音字词复用同一词 id
            val wordId = wordIndex.getOrPut(word) {
                words.add(word)
                words.size - 1
            }
            syllableSet.addAll(sylTexts)
            entrySyllableTexts.add(sylTexts.toList())
            entryWordIds.add(wordId)
            entryFreqs.add(freq)
        }

        // 音节表定序：去重排序
        val syllables = syllableSet.sorted()
        val syllableId = HashMap<String, Int>(syllables.size)
        syllables.forEachIndexed { i, s -> syllableId[s] = i }

        // 回填音节 id，构造词条
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
