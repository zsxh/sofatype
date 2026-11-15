// T71 · 决策 60 · 日志行格式化与文件尾部截取（纯 JVM，单元测试锁定 LogFileFormatterTest / LogFileTailTest）。
// 行格式对齐 logcat 呈现口径（验收「文件内容与 logcat 同步」）：MM-dd HH:mm:ss.SSS P/TAG: message，
// 可抛栈由 Timber 的 Tree.prepareLog 追加在 message 之后（多行原样落盘）。
// 尾部截取 = 保留文件末尾 keepBytes 并裁齐到完整行（丢半个首行，环形语义保留最新）。
// 本层零 Android 依赖，可在纯 JVM 单测中直跑真实临时文件。
package com.sofatype.ime.debug

import java.io.File
import java.io.RandomAccessFile

object LogFileFormatter {

    /** logcat 级别字符映射（Log.VERBOSE..Log.ASSERT → V/D/I/W/E/A）。 */
    fun priorityChar(priority: Int): Char = when (priority) {
        android.util.Log.VERBOSE -> 'V'
        android.util.Log.DEBUG -> 'D'
        android.util.Log.INFO -> 'I'
        android.util.Log.WARN -> 'W'
        android.util.Log.ERROR -> 'E'
        else -> 'A' // Log.ASSERT 与未知取值兜底
    }

    /** 拼接日志行（时间戳字符串由调用方注入，便于纯 JVM 锁定格式）。 */
    fun formatLine(timestamp: String, priority: Int, tag: String?, message: String): String =
        "$timestamp ${priorityChar(priority)}/${tag ?: "-"}: $message"
}

object LogFileTail {

    /**
     * 读文件末尾 keepBytes 并裁齐到完整行：切割点落在行中间时丢弃首个不完整行（裁到行首），
     * 切割点恰在行边界时原样保留；文件小于 keepBytes 时原样返回全部字节（最新内容优先）。
     */
    fun tailBytesAligned(file: File, keepBytes: Long): ByteArray {
        val size = file.length()
        if (size <= 0 || keepBytes <= 0) return ByteArray(0)
        val rawStart = if (size <= keepBytes) 0L else size - keepBytes
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(rawStart)
            val raw = ByteArray((size - rawStart).toInt())
            raf.readFully(raw)
            if (rawStart > 0) {
                // 前一字节为换行 = 切割点恰在行边界，无需裁；否则裁到首个换行之后（丢半个首行）
                raf.seek(rawStart - 1)
                val prev = raf.read()
                if (prev != '\n'.code) {
                    val firstNewline = raw.indexOf('\n'.code.toByte())
                    if (firstNewline >= 0) return raw.copyOfRange(firstNewline + 1, raw.size)
                    return ByteArray(0) // 窗口内无完整行（单行超保留量）：保留空
                }
            }
            return raw
        }
    }

    /**
     * 就地轮转：用「末尾 keepBytes（裁齐整行）」覆盖原文件（临时文件 + 原子替换语义，
     * 失败不影响原文件）。返回保留的字节数。
     */
    fun rewriteKeepingTail(file: File, keepBytes: Long): Long {
        val kept = tailBytesAligned(file, keepBytes)
        val temp = File(file.parentFile, file.name + ".rotate-tmp")
        temp.writeBytes(kept)
        if (!temp.renameTo(file)) {
            // rename 失败（同目录常规不会发生）：退化为直写覆盖，仍保证环形单文件语义
            file.writeBytes(kept)
            temp.delete()
        }
        return kept.size.toLong()
    }
}
