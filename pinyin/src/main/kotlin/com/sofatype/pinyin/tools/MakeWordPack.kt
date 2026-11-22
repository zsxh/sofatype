package com.sofatype.pinyin.tools

import com.sofatype.pinyin.DictFormat
import java.io.File

fun main(args: Array<String>) {
    require(args.size == 2) { "用法：makeWordPack <源文件路径> <产物路径>" }
    val source = File(args[0])
    require(source.exists() && source.length() > 0) { "词包源文件不存在或为空：${source.absolutePath}" }
    val output = File(args[1])
    output.parentFile?.mkdirs()

    val startAt = System.currentTimeMillis()
    val text = source.readLines().asSequence()
    val (data, stats) = DictBuilder.build(text)
    require(data.entryCount > 0) { "词包源无有效词条：${source.absolutePath}" }
    DictFormat.write(data, output.outputStream())
    val elapsed = System.currentTimeMillis() - startAt

    println("[makeWordPack] 读入行数：${stats.lineCount}")
    println("[makeWordPack] 有效词条：${stats.entryCount}")
    println("[makeWordPack] 跳过行数：${stats.skippedLineCount}")
    println("[makeWordPack] 音节数：${stats.syllableCount}")
    println("[makeWordPack] 词数（去重后）：${stats.wordCount}")
    println("[makeWordPack] 产物：${output.absolutePath}（${output.length()} 字节）")
    println("[makeWordPack] 耗时：$elapsed ms")
}
