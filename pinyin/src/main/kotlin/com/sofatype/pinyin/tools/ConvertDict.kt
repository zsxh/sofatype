package com.sofatype.pinyin.tools

import com.sofatype.pinyin.DictFormat
import com.sofatype.pinyin.topFreqSubset
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

fun main(args: Array<String>) {
    require(args.size >= 2) { "用法：convertDict <源文件路径> <产物路径> [高频子集产物路径 <子集词条数>]" }
    require(args.size == 2 || args.size == 4) { "参数非法：子集输出须同时给出产物路径与词条数" }
    val source = File(args[0])
    require(source.exists() && source.length() > 0) { "源文件不存在或为空：${source.absolutePath}" }
    val output = File(args[1])
    output.parentFile?.mkdirs()

    val startAt = System.currentTimeMillis()
    val text = source.reader(Charset.forName("UTF-16")).useLines { lines ->

        lines.toList()
    }
    val (data, stats) = DictBuilder.build(text.asSequence())
    DictFormat.write(data, output.outputStream())
    val elapsed = System.currentTimeMillis() - startAt

    println("[convertDict] 读入行数：${stats.lineCount}")
    println("[convertDict] 有效词条：${stats.entryCount}")
    println("[convertDict] 跳过行数：${stats.skippedLineCount}")
    println("[convertDict] 音节数：${stats.syllableCount}")
    println("[convertDict] 词数（去重后）：${stats.wordCount}")
    println("[convertDict] 产物：${output.absolutePath}（${output.length()} 字节）")
    println("[convertDict] 耗时：${elapsed} ms")

    if (args.size == 4) {
        val subsetOutput = File(args[2])
        subsetOutput.parentFile?.mkdirs()
        val subsetLimit = args[3].toInt()
        require(subsetLimit > 0 && subsetLimit <= stats.entryCount) {
            "子集词条数非法：$subsetLimit（有效词条 ${stats.entryCount}）"
        }
        val subset = data.topFreqSubset(subsetLimit)
        DictFormat.write(subset, subsetOutput.outputStream())
        println("[convertDict] 高频子集词条：${subset.entryCount}")
        println("[convertDict] 高频子集词数：${subset.words.size}")
        println("[convertDict] 高频子集产物：${subsetOutput.absolutePath}（${subsetOutput.length()} 字节）")
    }
}
