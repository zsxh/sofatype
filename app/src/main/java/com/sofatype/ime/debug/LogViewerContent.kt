// T71 · 决策 60 · 查看日志页内容读取（纯 JVM 文件 IO，单元测试锁定 LogViewerContentTest）。
// 读日志文件 → 行数 / 字节数 / 展示文本。展示文本超尾部上限时只取文件末尾（最新优先），
// 前置截断说明行——查看页为现场浏览面，完整取证走 adb pull（决策 60「无导出」口径）；
// 上限防 TextView 大文本主线程排版卡顿（10 MB 档直排有 ANR 风险）。读操作必须在后台线程调用
//（严禁主线程磁盘 IO），Activity 侧用后台执行器承载。
package com.sofatype.ime.debug

import java.io.File

object LogViewerContent {

    /** 查看页展示文本尾部上限（字节，防主线程大文本排版卡顿）。 */
    const val TAIL_CAP_BYTES = 1_000_000L

    /** 一次读取的结果：展示文本 + 行数 + 字节数 + 是否尾部截断 + 读取是否成功。 */
    data class ViewLogData(
        val displayText: String,
        val lineCount: Long,
        val usedBytes: Long,
        val truncated: Boolean,
        val ok: Boolean,
    ) {
        companion object {
            /** 文件缺失 / 读取失败的空结果（查看页呈现「日志为空」与 0 统计）。 */
            fun empty(ok: Boolean): ViewLogData = ViewLogData("", 0, 0, truncated = false, ok = ok)
        }
    }

    /** 读日志文件（后台线程调用）。文件不存在 = 空结果（ok=true）。 */
    fun read(file: File, tailCapBytes: Long = TAIL_CAP_BYTES): ViewLogData {
        if (!file.exists()) return ViewLogData.empty(ok = true)
        return try {
            val bytes = file.readBytes()
            val usedBytes = bytes.size.toLong()
            val lineCount = bytes.count { it == '\n'.code.toByte() }.toLong()
            val (display, truncated) = displayText(bytes, tailCapBytes)
            ViewLogData(display, lineCount, usedBytes, truncated, ok = true)
        } catch (_: java.io.IOException) {
            ViewLogData.empty(ok = false)
        }
    }

    /**
     * 展示文本：超上限取末尾整块（最新优先），前置截断说明行；截断点对齐行首
     *（丢半个首行，与 LogFileTail 同口径）。
     */
    private fun displayText(bytes: ByteArray, tailCapBytes: Long): Pair<String, Boolean> {
        if (bytes.size.toLong() <= tailCapBytes) return bytes.toString(Charsets.UTF_8) to false
        var start = bytes.size - tailCapBytes.toInt()
        // 对齐行首：跳过首个不完整行
        while (start < bytes.size && bytes[start] != '\n'.code.toByte()) start++
        if (start < bytes.size) start++ // 越过换行符
        val tail = bytes.copyOfRange(start, bytes.size).toString(Charsets.UTF_8)
        return LogUiText.TAIL_NOTE + tail to true
    }
}
