package com.sofatype.pinyin.tools

import com.sofatype.pinyin.DictFormat
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.Charset

/**
 * 构建期转换：AOSP 谷歌拼音词库源文本 → SPD1 精简格式产物（T9，决策 28）。
 *
 * 源文件为 UTF-16 编码（带 BOM），Java 的 "UTF-16" 字符集按 BOM 自动选择字节序。
 * 用法：main <源文件路径> <产物路径>
 */
fun main(args: Array<String>) {
    require(args.size >= 2) { "用法：convertDict <源文件路径> <产物路径>" }
    val source = File(args[0])
    require(source.exists() && source.length() > 0) { "源文件不存在或为空：${source.absolutePath}" }
    val output = File(args[1])
    output.parentFile?.mkdirs()

    val startAt = System.currentTimeMillis()
    val text = source.reader(Charset.forName("UTF-16")).useLines { lines ->
        // 一次性物化：65 105 行，避免在统计行数后重复读文件
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
}
