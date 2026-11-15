// T71 · 决策 60 · 未捕获异常文件日志钩子：包装系统默认 handler——崩溃时先按开关把异常栈
// 写入日志文件（同步，上限 2s，进程将亡场景可接受），随后交回系统默认 handler，
// 崩溃行为与现状一致（弹窗 / 杀进程 / 系统崩溃呈现零变化）。开关关闭时直通零旁路。
package com.sofatype.ime.debug

object CrashFileLogger {

    @Volatile
    private var installed = false

    /** 常驻安装（进程一次，Application.onCreate 调用；重复安装幂等跳过）。 */
    fun install() {
        if (installed) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (DebugLogStore.isEnabled) {
                    DebugLogController.writeCrashStack(throwable)
                }
            } catch (_: Throwable) {
                // 崩溃路径的兜底：文件日志失败不得影响系统默认崩溃处理
            }
            previous?.uncaughtException(thread, throwable)
        }
        installed = true
    }
}
