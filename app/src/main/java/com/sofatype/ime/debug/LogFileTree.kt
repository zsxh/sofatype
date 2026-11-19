package com.sofatype.ime.debug

import timber.log.Timber
import java.io.File

class LogFileTree(private val writer: LogFileWriter) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {

        writer.append(LogFileFormatter.formatLine(TimestampSource.now(), priority, tag, message))
    }

    companion object {

        fun logFile(logsDir: File): File = File(logsDir, "sofatype.log")

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

        fun unmount() {
            val tree = synchronized(this) {
                val current = mounted ?: return
                mounted = null
                current
            }
            Timber.uproot(tree)
            tree.writer.shutdown()
        }

        val activeWriter: LogFileWriter? get() = mounted?.writer
    }
}
