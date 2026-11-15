// T71 · 决策 60 · 调试日志控制器：进程级接线收口（Application.onCreate 调 init 一次）。
// ① 文件树挂载/卸载随开关（开启即挂、关闭先落「调试日志已关闭」日志再卸载）；
// ② 开关切换 / 清空动作 / 崩溃栈三类新增日志点与写失败降级经本类收口；
// ③ 未捕获异常钩子常驻安装：崩溃时按开关把栈写入日志文件（同步，上限 2s），
//    随后交回系统默认 handler——崩溃行为不变（决策 60 口径）。
package com.sofatype.ime.debug

import android.content.Context
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter

object DebugLogController {

    /** 开关 / 轮转 / 清空 / 降级日志点统一 TAG（画板 15 I/DebugLog 行口径）。 */
    const val TAG = "DebugLog"

    /** 日志目录：应用私有 filesDir/logs/（决策 60；卸载应用即随目录清除）。 */
    private lateinit var logsDir: File

    /** init：加载配置缓存 + 常驻安装未捕获异常钩子 + 按开机开关状态挂载文件树。 */
    fun init(appContext: Context) {
        val context = appContext.applicationContext
        logsDir = File(context.filesDir, "logs")
        DebugLogStore.init(context)
        CrashFileLogger.install()
        if (DebugLogStore.isEnabled) {
            LogFileTree.mount(logsDir)
        }
    }

    /** 开关切换（设置子页调用）：持久化 + 挂载/卸载 + 开关切换日志点（决策 60）。 */
    fun setEnabled(context: Context, value: Boolean) {
        DebugLogStore.writeEnabled(context, value)
        if (value) {
            // 先挂树再落开关日志：开启这条日志本身进文件（画板 15「调试日志已开启（档位 2 MB）」）
            LogFileTree.mount(logsDir)
            Timber.tag(TAG).i("调试日志已开启（档位 ${DebugLogOptions.label(DebugLogStore.sizeMb)}）")
        } else {
            // 先落开关日志再卸树：关闭这条日志也进文件（文件保留，清空只走查看页按钮）
            Timber.tag(TAG).i("调试日志已关闭")
            LogFileTree.unmount()
        }
    }

    /** 档位切换（设置子页调用）：持久化即生效（下一轮冲刷按新档位轮转判定），无独立日志点。 */
    fun setSizeMb(context: Context, sizeMb: Int) {
        DebugLogStore.writeSizeMb(context, sizeMb)
    }

    /** 清空日志（查看页调用）：文件截断为空 + 清空动作日志点（决策 60）。 */
    fun clearLogs() {
        val writer = LogFileTree.activeWriter
        if (writer != null) {
            writer.clear()
            Timber.tag(TAG).i("日志文件已清空")
        } else {
            // 树未挂载（开关关闭但文件保留）：直接截断既有文件（无树在场，无日志点可落）
            val file = logFile()
            if (file.exists()) {
                FileOutputStream(file, false).use { /* 截断为空 */ }
            }
        }
    }

    /** 查看页 / 子页读取当前文件（可能为空或不存在）。 */
    fun logFile(): File = LogFileTree.logFile(logsDir)

    /**
     * 崩溃路径（CrashFileLogger 回调）：异常栈写入文件（单批同步落盘，含轮转判定）后返回；
     * 树未挂载（开关关闭）直接跳过。调用方随后交回系统默认 handler，崩溃行为不变。
     */
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
