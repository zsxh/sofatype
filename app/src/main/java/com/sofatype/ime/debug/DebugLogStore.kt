// T71 · 决策 60 · 调试日志配置存取（SharedPreferences，仅配置项：开关 + 档位）。
// 本工程首个持久化配置 API（T47 UI 渠道 in-flight 标记为消费即清的临时标记，非配置项）；
// 决策 60 拍板随本 issue 如实记录，不涉输入内容（隐私红线不破）。
// 读写走缓存（@Volatile）：IME 服务 trace() 门控在主线程高频读，不落磁盘 IO。
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

    /** 进程启动（Application.onCreate）加载一次缓存；未初始化时读到的都是默认值。 */
    fun init(context: Context) {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enabled = prefs.getBoolean(KEY_ENABLED, false)
        val raw = prefs.getInt(KEY_SIZE_MB, DebugLogOptions.DEFAULT_MB)
        sizeMbCache = DebugLogOptions.clampMb(raw)
    }

    /** 调试日志开关当前值（trace 门控 / 文件树挂载判定共用）。 */
    val isEnabled: Boolean get() = enabled

    /** 当前档位（MB，合法档位值）。 */
    val sizeMb: Int get() = sizeMbCache

    /** 轮转阈值（字节）。 */
    val maxBytes: Long get() = DebugLogOptions.bytesFor(sizeMbCache)

    /** 写开关（持久化 + 缓存同步刷新）。 */
    fun writeEnabled(context: Context, value: Boolean) {
        enabled = value
        prefs(context).edit().putBoolean(KEY_ENABLED, value).apply()
    }

    /** 写档位（持久化 + 缓存同步刷新；越界值钳制回合法档位）。 */
    fun writeSizeMb(context: Context, value: Int) {
        val clamped = DebugLogOptions.clampMb(value)
        sizeMbCache = clamped
        prefs(context).edit().putInt(KEY_SIZE_MB, clamped).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
