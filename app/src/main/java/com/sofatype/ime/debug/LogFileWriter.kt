// T71 · 决策 60 · 日志文件写入器（专用线程异步写 + 行缓冲）。
// 主线程只做 append 入队（零磁盘 IO）；实际写入全部在专用 HandlerThread 上执行：
// 行缓冲满阈值（FLUSH_BUFFER_LINES）或延迟窗（FLUSH_DELAY_MS）到达才落盘；
// 冲刷前按档位做轮转判定（LogRotation 纯模型 + LogFileTail 尾部保留），单文件环形覆盖保留最新，
// 轮转触发自身落一条日志（决策 60 新增日志点，TAG=DebugLog）；
// 写失败置降级位（停写不崩、不再尝试），降级日志经 android.util.Log 直写 logcat
//（不走 Timber，防文件树递归重入）。
// 崩溃路径 writeCrashNow / 清空 clear / 强制冲刷 flushNow 提供带超时的同步等待
//（进程将亡/页面刷新场景，阻塞上限 SYNC_TIMEOUT_MS=2s，正常功能不阻塞）。
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

        /** 本写入器自身的取证/降级日志 TAG（DebugLog，与开关/轮转/清空日志点同通道）。 */
        private const val TAG = "DebugLog"

        /** 行缓冲满即冲刷的阈值（行数）。 */
        private const val FLUSH_BUFFER_LINES = 64

        /** 行缓冲不满时的延迟冲刷窗口（ms）：低频日志不无限滞留内存。 */
        private const val FLUSH_DELAY_MS = 300L

        /** 崩溃路径 / 清空 / 强制冲刷的同步等待上限（ms）。 */
        private const val SYNC_TIMEOUT_MS = 2000L
    }

    private val thread = HandlerThread(WRITER_THREAD_NAME).also { it.start() }
    private val handler = Handler(thread.looper)

    /** 待写行缓冲（仅写线程触碰）。 */
    private val pending = ArrayDeque<String>()

    /** 已排程的延迟冲刷任务（写线程比对防重复排程）。 */
    private var flushScheduled = false

    /** 写失败降级位（volatile：任意线程读，写线程置位）。 */
    @Volatile
    private var degraded = false

    /** 追加一行（任意线程）：入队 + 按缓冲量决定即时冲刷或延迟冲刷。降级后静默丢弃。 */
    fun append(line: String) {
        if (degraded) return
        handler.post { onAppend(line) }
    }

    /** 清空日志文件（先冲缓冲再截断；带超时同步等待，供查看页清空后刷新读数）。 */
    fun clear() {
        runOnWriter("clear") {
            flushPendingLocked()
            if (!degraded) {
                try {
                    logFile.parentFile?.mkdirs()
                    FileOutputStream(logFile, false).use { /* 截断为空文件 */ }
                } catch (e: IOException) {
                    degrade(e)
                }
            }
        }
    }

    /** 强制冲刷待写缓冲（带超时同步等待；查看页刷新 / 卸载前调用）。 */
    fun flushNow() {
        runOnWriter("flush") { flushPendingLocked() }
    }

    /**
     * 崩溃路径：先冲缓冲，再把未捕获异常栈同步落盘（带轮转判定）。
     * 进程将亡场景，等待有上限；完成后由调用方交回系统默认 handler（崩溃行为不变）。
     */
    fun writeCrashNow(lines: List<String>) {
        if (degraded) return
        runOnWriter("crash") {
            flushPendingLocked()
            appendAndFlushLocked(lines)
        }
    }

    /** 停止写线程（开关关闭卸载文件树时调用）：冲净缓冲后退出线程，零残留。 */
    fun shutdown() {
        runOnWriter("shutdown") {
            flushPendingLocked()
            handler.looper.quitSafely()
        }
    }

    // ---- 以下方法仅在写线程执行 ----

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

    /** 冲刷待写缓冲：取整批交给落盘（轮转判定在落盘前统一做）。 */
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
            // 轮转：现有长度 + 本批字节超档位时触发一次（保留最新一半档位容量）
            val plan = LogRotation.plan(logFile.length(), incomingBytes, maxBytesProvider())
            val outLines = if (plan != null) {
                LogFileTail.rewriteKeepingTail(logFile, plan.keepBytes)
                val discardKb = plan.discardBytes / 1024
                // 轮转日志行并入本批直写（不走 Timber 入队，避免写线程自递归排程）；
                // 时间戳 = 轮转发生时刻（冲刷时点，即事件时刻），追加在本批末尾——
                // 本批缓冲行为更早事件，落盘顺序保持时间单调（qa Round 1 观察项 2：消除时间倒挂）
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

    /** 写失败降级：置位停写 + logcat 落一条（直写 android.util.Log，不经 Timber 文件树）。 */
    private fun degrade(e: IOException) {
        if (degraded) return
        degraded = true
        pending.clear()
        Log.w(TAG, "日志文件写入失败，降级停写：${e.javaClass.simpleName} ${e.message}")
    }

    /** 任务投递到写线程并同步等待（上限 SYNC_TIMEOUT_MS；超时不阻塞调用方）。 */
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

/** 时间戳来源（独立小对象：仅写线程访问，SimpleDateFormat 无需额外同步）。 */
internal object TimestampSource {
    private val format = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    /** 当前时间戳串（logcat 呈现口径 MM-dd HH:mm:ss.SSS）。 */
    fun now(): String = format.format(java.lang.System.currentTimeMillis())
}
