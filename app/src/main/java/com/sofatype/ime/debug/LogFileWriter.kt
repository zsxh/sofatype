package com.sofatype.ime.debug

import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LogFileWriter(
    private val logFile: File,
    private val maxBytesProvider: () -> Long,
) {
    companion object {
        private const val WRITER_THREAD_NAME = "sofatype-log-writer"

        private const val TAG = "DebugLog"

        private const val FLUSH_BUFFER_LINES = 64

        private const val FLUSH_DELAY_MS = 300L

        private const val SYNC_TIMEOUT_MS = 2000L
    }

    private val thread = HandlerThread(WRITER_THREAD_NAME).also { it.start() }
    private val handler = Handler(thread.looper)

    private val pending = ArrayDeque<String>()

    private var flushScheduled = false

    @Volatile
    private var degraded = false

    fun append(line: String) {
        if (degraded) return
        handler.post { onAppend(line) }
    }

    fun clear() {
        runOnWriter("clear") {
            flushPendingLocked()
            if (!degraded) {
                try {
                    logFile.parentFile?.mkdirs()
                    FileOutputStream(logFile, false).use {  }
                } catch (e: IOException) {
                    degrade(e)
                }
            }
        }
    }

    fun flushNow() {
        runOnWriter("flush") { flushPendingLocked() }
    }

    fun writeCrashNow(lines: List<String>) {
        if (degraded) return
        runOnWriter("crash") {
            flushPendingLocked()
            appendAndFlushLocked(lines)
        }
    }

    fun shutdown() {
        runOnWriter("shutdown") {
            flushPendingLocked()
            handler.looper.quitSafely()
        }
    }

    private fun onAppend(line: String) {
        if (degraded) return
        pending.addLast(line)
        if (pending.size >= FLUSH_BUFFER_LINES) {
            flushPendingLocked()
        } else if (!flushScheduled) {
            flushScheduled = true
            handler.postDelayed({
                flushScheduled = false
                flushPendingLocked()
            }, FLUSH_DELAY_MS)
        }
    }

    private fun flushPendingLocked() {
        if (pending.isEmpty() || degraded) return
        val batch = ArrayList<String>(pending.size)
        while (pending.isNotEmpty()) batch.add(pending.removeFirst())
        appendAndFlushLocked(batch)
    }

    private fun appendAndFlushLocked(lines: List<String>) {
        if (degraded || lines.isEmpty()) return
        try {
            logFile.parentFile?.mkdirs()
            val incomingBytes = lines.sumOf { it.length.toLong() + 1 }

            val plan = LogRotation.plan(logFile.length(), incomingBytes, maxBytesProvider())
            val outLines = if (plan != null) {
                LogFileTail.rewriteKeepingTail(logFile, plan.keepBytes)
                val discardKb = plan.discardBytes / 1024

                val rotateLine = LogFileFormatter.formatLine(
                    TimestampSource.now(),
                    Log.INFO,
                    TAG,
                    "文件超限轮转触发：丢弃最早 ${discardKb} KB，保留最新",
                )
                lines + rotateLine
            } else {
                lines
            }
            writeToDisk(outLines)
        } catch (e: IOException) {
            degrade(e)
        }
    }

    private fun writeToDisk(lines: List<String>) {
        FileOutputStream(logFile, true).use { out ->
            val sb = StringBuilder()
            for (line in lines) {
                sb.append(line).append('\n')
            }
            out.write(sb.toString().toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    private fun degrade(e: IOException) {
        if (degraded) return
        degraded = true
        pending.clear()
        Log.w(TAG, "日志文件写入失败，降级停写：${e.javaClass.simpleName} ${e.message}")
    }

    private fun runOnWriter(what: String, task: () -> Unit) {
        val done = CountDownLatch(1)
        handler.post {
            try {
                task()
            } finally {
                done.countDown()
            }
        }
        if (!done.await(SYNC_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            Log.w(TAG, "日志写线程任务超时未完成：$what")
        }
    }
}

internal object TimestampSource {
    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun now(): String = format.format(java.lang.System.currentTimeMillis())
}
