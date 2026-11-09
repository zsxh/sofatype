// T25 · 远程模式空闲自动退出判定模型（PRD 第 8 章电量与资源 P1：「远程模式空闲（无手机连接）
// 30 分钟自动退出并收起键盘」）。
// 空闲口径按 PRD 括注 = 无手机连接：远程模式运行期间，从「进入无手机连接态」（服务启动成功
// 或已配对连接断开回到等待扫码）起算，持续满阈值即应自动退出；手机已连接期间不判空闲。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（同 ImeShowGuard / DeleteRepeatRhythm
// 施工口径：时间由调用方注入，本类不持时钟）；接线在 RemoteInputServerManager（排程与执行）。
package com.sofatype.ime.remote

/**
 * 远程模式空闲退出判定（T25 · 第 8 章电量资源）。
 * 状态：空闲窗口起点（调用方时钟毫秒）——null = 非空闲（手机已连接）或未在跟踪
 * （远程模式未运行 / 已退出）。单一布尔结论经 [shouldExit] 给出，排程时机由接线层决定。
 */
class RemoteIdlePolicy(
    /** 空闲自动退出阈值（ms）：正式口径 30 分钟（PRD 第 8 章）；验证允许临时缩短，验后恢复。 */
    val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
) {

    init {
        require(idleTimeoutMs > 0) { "空闲阈值必须为正数：$idleTimeoutMs" }
    }

    /** 空闲窗口起点（调用方时钟毫秒）；null = 非空闲。 */
    private var idleSinceMs: Long? = null

    /**
     * 进入无手机连接态（远程模式开始 / 已配对连接断开回到等待扫码）时调用：
     * 空闲自此刻起算；重复调用刷新起点（后一次进入覆盖前一次）。
     */
    fun onIdleStarted(nowMs: Long) {
        idleSinceMs = nowMs
    }

    /** 退出空闲跟踪（手机已连接 / 远程模式结束）：清除窗口，[shouldExit] 恒否。 */
    fun onIdleEnded() {
        idleSinceMs = null
    }

    /**
     * 此刻是否已达到空闲自动退出条件：处于空闲窗口且经过时长满 [idleTimeoutMs]。
     * 仅作状态判定，不做任何执行动作（停服与收起键盘由接线层负责）。
     */
    fun shouldExit(nowMs: Long): Boolean {
        val since = idleSinceMs ?: return false
        return nowMs - since >= idleTimeoutMs
    }

    companion object {
        /** 正式空闲阈值 = 30 分钟（PRD 第 8 章电量与资源 P1 原文口径）。 */
        const val DEFAULT_IDLE_TIMEOUT_MS = 30L * 60L * 1000L
    }
}
