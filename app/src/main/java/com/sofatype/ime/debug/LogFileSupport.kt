package com.sofatype.ime.debug

import java.io.File
import java.io.RandomAccessFile

object LogFileFormatter {

    fun priorityChar(priority: Int): Char = when (priority) {
        android.util.Log.VERBOSE -> 'V'
        android.util.Log.DEBUG -> 'D'
        android.util.Log.INFO -> 'I'
        android.util.Log.WARN -> 'W'
        android.util.Log.ERROR -> 'E'
        else -> 'A'
    }

    fun formatLine(timestamp: String, priority: Int, tag: String?, message: String): String =
        "$timestamp ${priorityChar(priority)}/${tag ?: "-"}: $message"
}

object LogFileTail {

    fun tailBytesAligned(file: File, keepBytes: Long): ByteArray {
        val size = file.length()
        if (size <= 0 || keepBytes <= 0) return ByteArray(0)
        val rawStart = if (size <= keepBytes) 0L else size - keepBytes
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(rawStart)
            val raw = ByteArray((size - rawStart).toInt())
            raf.readFully(raw)
            if (rawStart > 0) {

                raf.seek(rawStart - 1)
                val prev = raf.read()
                if (prev != '\n'.code) {
                    val firstNewline = raw.indexOf('\n'.code.toByte())
                    if (firstNewline >= 0) return raw.copyOfRange(firstNewline + 1, raw.size)
                    return ByteArray(0)
                }
            }
            return raw
        }
    }

    fun rewriteKeepingTail(file: File, keepBytes: Long): Long {
        val kept = tailBytesAligned(file, keepBytes)
        val temp = File(file.parentFile, file.name + ".rotate-tmp")
        temp.writeBytes(kept)
        if (!temp.renameTo(file)) {

            file.writeBytes(kept)
            temp.delete()
        }
        return kept.size.toLong()
    }
}
