package com.sofatype.ime.debug

object CrashFileLogger {

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                if (DebugLogStore.isEnabled) {
                    DebugLogController.writeCrashStack(throwable)
                }
            } catch (_: Throwable) {

            }
            previous?.uncaughtException(thread, throwable)
        }
        installed = true
    }
}
