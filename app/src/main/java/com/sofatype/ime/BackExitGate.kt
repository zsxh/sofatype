// T67 · 设置页 BACK 二次退出窗口纯决策（决策 57：修订设置页 BACK 单击直退行为）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（参照 ImeShowGuard 纯决策 + 测试模式；
// 时间由调用方注入，本类不持时钟）。接线见 SettingsActivity：两条 BACK 路径
//（pre-IME 分路 BACK 分支、冷实例经 IME 隐藏态透传落 Activity 默认 BACK）共用同一实例，
// 收敛到同一「二次退出」裁决——首击只提示不退出、2 秒内再击退出、超窗重新计。
package com.sofatype.ime

/**
 * 设置页 BACK 二次退出守卫（T67 · 决策 57）。
 *
 * 状态机（按击裁决）：
 * - 窗口未开启（首击 / 超窗后再击）：开启 [windowMs] 提示窗，裁决 = 提示不退出（SHOW_HINT）；
 * - 窗口开启且按击落在窗内（距今 < [windowMs]）：裁决 = 退出（EXIT），窗口随之关闭——
 *   宿主 finish 后本实例随之销毁，窗口关闭仅为状态机确定性；
 * - 窗口开启但已超窗（距今 ≥ [windowMs]）：视同新首击，重新计窗并再提示（超窗重新计，
 *   不累计退出）。
 *
 * 所有时间参数为调用方时钟（接线侧用 SystemClock.uptimeMillis），本类不持时钟、不依赖 Android 框架。
 */
class BackExitGate(private val windowMs: Long) {

    /** 单击裁决：提示（首击 / 超窗重计击）或退出（窗内次击）。 */
    enum class Decision { SHOW_HINT, EXIT }

    /** 提示窗截止时刻；Long.MIN_VALUE = 未开启（首击必落提示分支）。 */
    private var windowUntilMs = Long.MIN_VALUE

    /**
     * 一次 BACK 按击裁决（决策 57）。
     * @param nowMs 本次按击时刻（调用方时钟）
     */
    fun onBack(nowMs: Long): Decision {
        if (nowMs < windowUntilMs) {
            // 窗内次击：直接退出并关窗（宿主 finish 后实例销毁，关窗仅为状态机确定性）
            windowUntilMs = Long.MIN_VALUE
            return Decision.EXIT
        }
        // 首击 / 超窗击：重新计窗，只提示不退出（超窗重新计）
        windowUntilMs = nowMs + windowMs
        return Decision.SHOW_HINT
    }

    companion object {
        /** 二次退出提示窗长（决策 57：2 秒）；单一事实源，接线侧与单测共用。 */
        const val WINDOW_MS = 2_000L
    }
}
