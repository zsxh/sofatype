package com.sofatype.pinyin.tools

import com.sofatype.pinyin.DictFormat
import java.io.File
import java.security.MessageDigest

object HotDictBuild {

    class MergeStats(
        val scelWordCount: Int,
        val scelSkippedOverlongCount: Int,
        val hotSourceLineCount: Int,
        val hotMainDictHitCount: Int,
        val hotDuplicateCount: Int,
        val hotKeptCount: Int,
        val hotSkippedOverlongCount: Int,
    )

    private const val MAX_ENTRY_SYLLABLES = 6
    private const val MAX_WORD_UTF16_BYTES = 64

    const val SHORT_WORD_FREQ: Double = 2500.0

    const val LONG_WORD_FREQ: Double = 300.0

    private const val SHORT_WORD_MAX_CHARS = 3

    fun tierFreq(word: String): Double =
        if (word.length <= SHORT_WORD_MAX_CHARS) SHORT_WORD_FREQ else LONG_WORD_FREQ

    private fun tieredLine(word: String, restColumns: List<String>): String =
        (listOf(word, tierFreq(word).toString()) + restColumns).joinToString(separator = " ")

    private fun overSpd1Limit(word: String, syllableCount: Int): Boolean =
        syllableCount > MAX_ENTRY_SYLLABLES || word.toByteArray(Charsets.UTF_16LE).size > MAX_WORD_UTF16_BYTES

    fun loadMainDictWords(lines: Sequence<String>): Set<String> =
        lines.mapNotNull { raw ->
            val line = raw.trim()
            line.split(' ').firstOrNull()?.takeIf { it.isNotEmpty() }
        }.toSet()

    fun mergeLines(
        scelWords: List<ScelDictParser.Word>,
        hotLines: List<String>,
        mainDictWords: Set<String>,
    ): Pair<List<String>, MergeStats> {
        val lines = ArrayList<String>(scelWords.size + hotLines.size)
        val seen = HashSet<String>(scelWords.size + hotLines.size)
        var scelSkipped = 0
        for (w in scelWords) {
            if (overSpd1Limit(w.word, w.syllables.size)) {
                scelSkipped++
                continue
            }
            lines.add(tieredLine(w.word, listOf("0") + w.syllables))
            seen.add(w.word)
        }
        var hotHit = 0
        var hotDup = 0
        var hotKept = 0
        var hotTotal = 0
        var hotOver = 0
        for (raw in hotLines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            hotTotal++
            val columns = line.split(' ').filter { it.isNotEmpty() }
            val word = columns.first()
            val syllableCount = columns.size - 3
            if (overSpd1Limit(word, syllableCount)) {
                hotOver++
                continue
            }
            if (word in mainDictWords) {
                hotHit++
                continue
            }
            if (!seen.add(word)) {
                hotDup++
                continue
            }
            lines.add(tieredLine(word, columns.drop(2)))
            hotKept++
        }
        return Pair(
            lines,
            MergeStats(scelWords.size, scelSkipped, hotTotal, hotHit, hotDup, hotKept, hotOver),
        )
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun run(packOutput: File, mainDictSource: File, materials: List<Pair<String, File>>) {
        require(mainDictSource.exists() && mainDictSource.length() > 0) {
            "主词库源文件不存在或为空：${mainDictSource.absolutePath}"
        }
        for ((kind, file) in materials) {
            require((kind == "scel" || kind == "hot")) {
                "原料参数非法（须 scel 或 hot）：$kind"
            }
            require(file.exists() && file.length() > 0) { "原料不存在或为空：${file.absolutePath}" }
            require(kind != "scel" || file.length() >= 0x1540 + 4) {
                "scel 原料过短（缺拼音表头）：${file.absolutePath}"
            }
        }

        val startAt = System.currentTimeMillis()
        val scelWords = ArrayList<ScelDictParser.Word>()
        val hotLines = ArrayList<String>()
        for ((kind, file) in materials) {
            when (kind) {
                "scel" -> {
                    val parsed = ScelDictParser.parse(file.inputStream())
                    println(
                        "[hotDictBuild] scel ${file.name}：格式 ${parsed.format}，词条 ${parsed.words.size}" +
                            "，块内跳过 ${parsed.skippedWordCount}，附加区未并入 ${parsed.trailingByteCount} 字节",
                    )
                    scelWords.addAll(parsed.words)
                }
                "hot" -> hotLines.addAll(file.readLines())
            }
        }

        val mainDictWords = mainDictSource.reader(Charsets.UTF_16).useLines { lines ->
            loadMainDictWords(lines)
        }
        val (mergedLines, mergeStats) = mergeLines(scelWords, hotLines, mainDictWords)
        val (data, stats) = DictBuilder.build(mergedLines.asSequence())

        packOutput.parentFile?.mkdirs()
        DictFormat.write(data, packOutput.outputStream())
        val elapsed = System.currentTimeMillis() - startAt

        println(
            "[hotDictBuild] scel 词条：${mergeStats.scelWordCount}" +
                "（超 SPD1 上限跳过 ${mergeStats.scelSkippedOverlongCount}）",
        )
        println(
            "[hotDictBuild] 热词源行：${mergeStats.hotSourceLineCount}" +
                "（主词库命中去重 ${mergeStats.hotMainDictHitCount}，重复去重 ${mergeStats.hotDuplicateCount}" +
                "，超 SPD1 上限跳过 ${mergeStats.hotSkippedOverlongCount}，保留 ${mergeStats.hotKeptCount}）",
        )
        println(
            "[hotDictBuild] 合并词条：${stats.entryCount}（源行跳过 ${stats.skippedLineCount}）" +
                "，音节 ${stats.syllableCount}，词数（去重后）${stats.wordCount}",
        )
        println("[hotDictBuild] SPD1 产物：${packOutput.absolutePath}（${packOutput.length()} 字节）")
        println("[hotDictBuild] 产物 sha256：${sha256Hex(packOutput)}")
        println("[hotDictBuild] 耗时：$elapsed ms")
    }
}

fun main(args: Array<String>) {
    require(args.size >= 2) {
        "用法：hotDictBuild <SPD1产物路径> <主词库源文件> [scel:<路径>|hot:<路径>]..."
    }
    val packOutput = File(args[0])
    val mainDictSource = File(args[1])
    val materials = args.drop(2).map { arg ->
        val prefix = arg.substringBefore(':')
        val path = arg.substringAfter(':', "")
        require((prefix == "scel" || prefix == "hot") && path.isNotEmpty()) {
            "原料参数非法（须 scel:<路径> 或 hot:<路径>）：$arg"
        }
        Pair(prefix, File(path))
    }
    HotDictBuild.run(packOutput, mainDictSource, materials)
}
