// T71 · 决策 60 · Timber 文件树：开关开启才挂载；把每条 Timber 输出格式化成 logcat 同构行
//（MM-dd HH:mm:ss.SSS P/TAG: message，抛栈由 Timber prepareLog 追加在 message 内），
// 交 LogFileWriter 专用线程异步落盘。本树只做「转格式 + 入队」，不触碰任何业务数据，
// 文件内容与 logcat 通道同源同步（验收口径）。
package com.sofatype.ime.debug

import timber.log.Timber
import java.io.File

class LogFileTree(private val writer: LogFileWriter) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // 时间戳在日志事件到达时刻定格（非落盘时刻），行序 = 事件序
        writer.append(LogFileFormatter.formatLine(TimestampSource.now(), priority, tag, message))
    }

    companion object {

        /** 日志文件（应用私有目录 filesDir/logs/ 单文件，决策 60）。 */
        fun logFile(logsDir: File): File = File(logsDir, "sofatype.log")

        /**
         * 挂载：创建写入器并 plant（档位阈值经 provider 每次冲刷实时读取，改档即时生效）。
         * 重复挂载安全（已挂载则直接返回现值）。
         */
        @Volatile
        private var mounted: LogFileTree? = null

        fun mount(logsDir: File): LogFileTree {
            mounted?.let { return it }
            synchronized(this) {
                mounted?.let { return it }
                val tree = LogFileTree(
                    LogFileWriter(logFile(logsDir)) { DebugLogStore.maxBytes },
                )
                Timber.plant(tree)
                mounted = tree
                return tree
            }
        }

        /** 卸载：uproot + 冲净缓冲停写线程（开关关闭调用；未挂载为无害调用）。 */
        fun unmount() {
            val tree = synchronized(this) {
                val current = mounted ?: return
                mounted = null
                current
            }
            Timber.uproot(tree)
            tree.writer.shutdown()
        }

        /** 当前写入器（挂载时非 null；清空 / 崩溃路径用）。 */
        val activeWriter: LogFileWriter? get() = mounted?.writer
    }
}
