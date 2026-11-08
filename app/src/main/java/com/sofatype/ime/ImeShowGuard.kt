// T38 · 收起-唤起时序竞争守卫（FR-A1 / FR-A10；T12 坑 1 / 设备档案坑 6）。
// 真机实测（小米电视4，T12 Round 1 户主复验）确认键转发在 IME 与宿主两端落地存在时序竞争：
// ① 「完成/下屏」收起后，宿主侧同一按键触发的 showSoftInput 迟到到达，键盘被重新弹出（收起失效）；
// ② 键盘收起后 OK 唤起时，同一按键的转发副本在重弹完成后才送达 IME，
//    落在初始焦点键直出字符（唤起直落字符）。
// 本类把两侧抑制窗做成纯 Kotlin 状态判定（时间由调用方注入，单测锁定窗口边界与吞键语义），
// 服务层只在框架回调点接线（见 SofatypeImeService 对应注释），不引入第三方依赖。
package com.sofatype.ime

/**
 * 收起-唤起时序守卫（T38）。
 *
 * 两个相互独立的窗口：
 * - 收起抑制窗（Bug A）：[onHideRequested] 起 [showGuardMs] 内，宿主迟到的显示请求应被拒绝；
 *   已落窗的显示应立即复位收起（API 23 无显示请求裁决覆写点时的兜底语义）。
 * - 唤起抑制窗（Bug B）：[onWindowShown] 起 [wakeGuardMs] 内，首个确认键按唤起键转发副本吞掉；
 *   吞一次即解除，其后的确认键照常放行（快速连按、候选选词不受影响）。
 *
 * 所有时间参数为调用方时钟（服务层用 SystemClock.uptimeMillis），本类不持时钟、不依赖 Android 框架。
 */
class ImeShowGuard(private val showGuardMs: Long, private val wakeGuardMs: Long) {

    /** 收起抑制窗截止时刻；Long.MIN_VALUE = 未开启（判定恒否）。 */
    private var showGuardUntilMs = Long.MIN_VALUE

    /** 唤起抑制窗截止时刻；吞掉首个确认键后复位为未开启。 */
    private var wakeGuardUntilMs = Long.MIN_VALUE

    /** 「完成/下屏」发起收起时调用：开启收起后显示抑制窗（重复调用刷新窗长，快连收取最后一次）。 */
    fun onHideRequested(nowMs: Long) {
        showGuardUntilMs = nowMs + showGuardMs
    }

    /**
     * Bug A 第一层：显示请求是否应拒绝（收起抑制窗内）。
     * 接线点为 API 34 的显示请求裁决回调；命中即不落窗，无闪现。
     */
    fun shouldVetoShowRequest(nowMs: Long): Boolean = nowMs < showGuardUntilMs

    /**
     * Bug A 第二层：窗口已落地的显示是否应立即复位收起（收起抑制窗内）。
     * 与 [shouldVetoShowRequest] 同窗判定；接线点为窗口显示回调，覆盖无裁决覆写点的 API 版本。
     */
    fun shouldRehideOnShown(nowMs: Long): Boolean = nowMs < showGuardUntilMs

    /** 输入窗口转为可见时调用：开启唤起确认键抑制窗（窗口显示回调与新输入会话起点均接线）。 */
    fun onWindowShown(nowMs: Long) {
        wakeGuardUntilMs = nowMs + wakeGuardMs
    }

    /**
     * Bug B：当前确认键是否应按唤起键转发副本吞掉（唤起抑制窗内）。
     * 吞掉首个命中键并解除窗口——同一窗内后续确认键放行，
     * 保证「唤起后快速连按 / 唤起后立即选词」不被抑制；窗外恒否。
     */
    fun shouldSwallowConfirmKey(nowMs: Long): Boolean {
        if (nowMs >= wakeGuardUntilMs) return false
        wakeGuardUntilMs = Long.MIN_VALUE
        return true
    }
}
