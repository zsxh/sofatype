package com.sofatype.ime.debug

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter

object DebugLogController {

    const val TAG = "DebugLog"

    private lateinit var logsDir: File

    fun init(appContext: Context) {
        val context = appContext.applicationContext
        logsDir = File(context.filesDir, "logs")
        DebugLogStore.init(context)
        CrashFileLogger.install()
        if (DebugLogStore.isEnabled) {
            LogFileTree.mount(logsDir)
        }
    }

    fun setEnabled(context: Context, value: Boolean) {
        DebugLogStore.writeEnabled(context, value)
        if (value) {

            LogFileTree.mount(logsDir)
            Timber.tag(TAG).i("调试日志已开启（档位 ${DebugLogOptions.label(DebugLogStore.sizeMb)}）")
        } else {

            Timber.tag(TAG).i("调试日志已关闭")
            LogFileTree.unmount()
        }
    }

    fun setSizeMb(context: Context, sizeMb: Int) {
        DebugLogStore.writeSizeMb(context, sizeMb)
    }

    fun clearLogs() {
        val writer = LogFileTree.activeWriter
        if (writer != null) {
            writer.clear()
            Timber.tag(TAG).i("日志文件已清空")
        } else {

            val file = logFile()
            if (file.exists()) {
                FileOutputStream(file, false).use {  }
            }
        }
    }

    fun logFile(): File = LogFileTree.logFile(logsDir)

    internal fun writeCrashStack(t: Throwable) {
        val writer = LogFileTree.activeWriter ?: return
        val sw = StringWriter(256)
        val pw = PrintWriter(sw, false)
        t.printStackTrace(pw)
        pw.flush()
        val header = LogFileFormatter.formatLine(TimestampSource.now(), android.util.Log.ERROR, TAG, "未捕获异常")
        writer.writeCrashNow(listOf(header, sw.toString().trimEnd('\n')))
    }
}
