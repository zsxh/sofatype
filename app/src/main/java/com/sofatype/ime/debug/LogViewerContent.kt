package com.sofatype.ime.debug

import java.io.File

object LogViewerContent {

    const val TAIL_CAP_BYTES = 1_000_000L

    data class ViewLogData(
        val displayText: String,
        val lineCount: Long,
        val usedBytes: Long,
        val truncated: Boolean,
        val ok: Boolean,
    ) {
        companion object {

            fun empty(ok: Boolean): ViewLogData = ViewLogData("", 0, 0, truncated = false, ok = ok)
        }
    }

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

    private fun displayText(bytes: ByteArray, tailCapBytes: Long): Pair<String, Boolean> {
        if (bytes.size.toLong() <= tailCapBytes) return bytes.toString(Charsets.UTF_8) to false
        var start = bytes.size - tailCapBytes.toInt()

        while (start < bytes.size && bytes[start] != '\n'.code.toByte()) start++
        if (start < bytes.size) start++
        val tail = bytes.copyOfRange(start, bytes.size).toString(Charsets.UTF_8)
        return LogUiText.TAIL_NOTE + tail to true
    }
}
