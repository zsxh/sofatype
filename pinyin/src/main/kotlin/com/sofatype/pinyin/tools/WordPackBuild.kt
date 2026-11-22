package com.sofatype.pinyin.tools

import com.sofatype.pinyin.DictFormat
import java.io.File
import java.security.MessageDigest

object WordPackBuild {

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

    private fun overSpd1Limit(word: String, syllableCount: Int): Boolean =
        syllableCount > MAX_ENTRY_SYLLABLES || word.toByteArray(Charsets.UTF_16LE).size > MAX_WORD_UTF16_BYTES

    private val VERSION_PATTERN = Regex("^[0-9]{8}\\.[0-9]+$")

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
            lines.add("${w.word} ${w.freq} 0 ${w.syllables.joinToString(" ")}")
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
            val word = line.split(' ').first()
            val syllableCount = line.split(' ').filter { it.isNotEmpty() }.size - 3
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
            lines.add(line)
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

    fun manifestJson(version: String, packName: String, packFile: File): String {
        require(VERSION_PATTERN.matches(version)) { "版本号非法（须 YYYYMMDD.N）：$version" }
        require(packName.isNotBlank() && !packName.contains('/') && !packName.contains('\\') && !packName.contains("..")) {
            "词包名称非法：$packName"
        }
        require(packFile.isFile && packFile.length() > 0) { "SPD1 产物不存在或为空：${packFile.absolutePath}" }
        return "[" +
            "\n  {" +
            "\n    \"name\": ${quoteJson(packName)}," +
            "\n    \"version\": ${quoteJson(version)}," +
            "\n    \"sha256\": \"${sha256Hex(packFile)}\"," +
            "\n    \"bytes\": ${packFile.length()}," +
            "\n    \"url\": \"\"" +
            "\n  }" +
            "\n]\n"
    }

    private fun quoteJson(raw: String): String {
        val sb = StringBuilder("\"")
        for (c in raw) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}

fun main(args: Array<String>) {
    require(args.size >= 6) {
        "用法：wordPackBuild <SPD1产物路径> <manifest产物路径> <版本号YYYYMMDD.N> <词包名称> <主词库源文件> [scel:<路径>|hot:<路径>]..."
    }
    val packOutput = File(args[0])
    val manifestOutput = File(args[1])
    val version = args[2]
    val packName = args[3]
    val mainDictSource = File(args[4])
    require(mainDictSource.exists() && mainDictSource.length() > 0) {
        "主词库源文件不存在或为空：${mainDictSource.absolutePath}"
    }
    val materials = args.drop(5).map { arg ->
        val prefix = arg.substringBefore(':')
        val path = arg.substringAfter(':', "")
        require((prefix == "scel" || prefix == "hot") && path.isNotEmpty()) {
            "原料参数非法（须 scel:<路径> 或 hot:<路径>）：$arg"
        }
        Pair(prefix, File(path))
    }
    require(materials.any { it.first == "scel" }) { "缺少 scel 原料（scel:<路径>）" }
    require(materials.any { it.first == "hot" }) { "缺少热搜原料（hot:<路径>）" }

    val startAt = System.currentTimeMillis()
    val scelWords = ArrayList<ScelDictParser.Word>()
    val hotLines = ArrayList<String>()
    for ((kind, file) in materials) {
        require(file.exists() && file.length() > 0) { "原料不存在或为空：${file.absolutePath}" }
        when (kind) {
            "scel" -> {
                val parsed = ScelDictParser.parse(file.inputStream())
                println(
                    "[wordPackBuild] scel ${file.name}：格式 ${parsed.format}，词条 ${parsed.words.size}" +
                        "，块内跳过 ${parsed.skippedWordCount}，附加区未并入 ${parsed.trailingByteCount} 字节",
                )
                scelWords.addAll(parsed.words)
            }
            "hot" -> hotLines.addAll(file.readLines())
        }
    }
    require(scelWords.isNotEmpty()) { "scel 原料未解析出词条" }

    val mainDictWords = mainDictSource.reader(Charsets.UTF_16).useLines { lines ->
        WordPackBuild.loadMainDictWords(lines)
    }
    val (mergedLines, mergeStats) = WordPackBuild.mergeLines(scelWords, hotLines, mainDictWords)
    val (data, stats) = DictBuilder.build(mergedLines.asSequence())
    require(data.entryCount > 0) { "合并后无有效词条" }

    packOutput.parentFile?.mkdirs()
    DictFormat.write(data, packOutput.outputStream())
    manifestOutput.parentFile?.mkdirs()
    manifestOutput.writeText(WordPackBuild.manifestJson(version, packName, packOutput), Charsets.UTF_8)
    val elapsed = System.currentTimeMillis() - startAt

    println(
        "[wordPackBuild] scel 词条：${mergeStats.scelWordCount}" +
            "（超 SPD1 上限跳过 ${mergeStats.scelSkippedOverlongCount}）",
    )
    println(
        "[wordPackBuild] 热搜源行：${mergeStats.hotSourceLineCount}" +
            "（主词库命中去重 ${mergeStats.hotMainDictHitCount}，重复去重 ${mergeStats.hotDuplicateCount}" +
            "，超 SPD1 上限跳过 ${mergeStats.hotSkippedOverlongCount}，保留 ${mergeStats.hotKeptCount}）",
    )
    println(
        "[wordPackBuild] 合并词条：${stats.entryCount}（源行跳过 ${stats.skippedLineCount}）" +
            "，音节 ${stats.syllableCount}，词数（去重后）${stats.wordCount}",
    )
    println("[wordPackBuild] SPD1 产物：${packOutput.absolutePath}（${packOutput.length()} 字节）")
    println(
        "[wordPackBuild] manifest：${manifestOutput.absolutePath}（版本 $version，词包「$packName」" +
            "，url 字段留空 = 分发 URL 未配置）",
    )
    println("[wordPackBuild] 耗时：$elapsed ms")
}
