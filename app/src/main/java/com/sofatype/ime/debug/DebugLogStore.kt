package com.sofatype.ime.debug

import android.content.Context

object DebugLogStore {

    private const val PREFS_NAME = "debug_log_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_SIZE_MB = "size_mb"

    @Volatile
    private var enabled = false

    @Volatile
    private var sizeMbCache = DebugLogOptions.DEFAULT_MB

    fun init(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ENABLED, false)
        val raw = prefs.getInt(KEY_SIZE_MB, DebugLogOptions.DEFAULT_MB)
        sizeMbCache = DebugLogOptions.clampMb(raw)
    }

    val isEnabled: Boolean get() = enabled

    val sizeMb: Int get() = sizeMbCache

    val maxBytes: Long get() = DebugLogOptions.bytesFor(sizeMbCache)

    fun writeEnabled(context: Context, value: Boolean) {
        enabled = value
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    fun writeSizeMb(context: Context, value: Int) {
        val clamped = DebugLogOptions.clampMb(value)
        sizeMbCache = clamped
        prefs(context).edit().putInt(KEY_SIZE_MB, clamped).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
