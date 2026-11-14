// T70 · 已连接无活动空闲判定模型（决策 59 · PRD 第 8 章电量与资源行 / FR-B6 修订）：
// 「已连接无活动（无任何 WS 消息收发）10 分钟自动退出并收起键盘」，防明文 LAN 会话窗口
// 无限延长（O-8 风险边界）。与 T25「无手机连接」30 分钟空闲（RemoteIdlePolicy）并存、
// 互不干扰：本模型只覆盖「存在已配对会话」期间的窗口，起算/解除接线在 RemoteInputServerManager。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（同 RemoteIdlePolicy 施工口径：
// 时间由调用方注入，本类不持时钟）。
// 活动口径 = 已配对会话上的协议消息收发（hello/input/clear/commit 与 TV 回发 committed/
// replaced/disconnect）：本端 H5 客户端（assets/remote/index.html）无 PING 心跳，
// 业务消息即全部 WS 流量，按协议消息计无遗漏。
package com.sofatype.ime.remote

/**
 * 已连接无活动空闲判定（T70 · 决策 59）。
 * 状态：最近一次 WS 消息收发时刻（调用方时钟毫秒）——null = 无已配对会话或未在跟踪
 * （远程模式未运行 / 会话已断开 / 服务已停止）。单一布尔结论经 [shouldExit] 给出，
 * 排程时机与停服执行由接线层（Manager，主线程 post 串行化 + 任务身份比对）负责。
 */
class RemoteConnectedIdlePolicy(
    /** 已连接无活动自动退出阈值（ms）：正式口径 10 分钟（决策 59）；验证允许临时缩短，验后恢复。 */
    val connectedIdleTimeoutMs: Long = DEFAULT_CONNECTED_IDLE_TIMEOUT_MS,
) {

    init {
        require(connectedIdleTimeoutMs > 0) { "已连接无活动阈值必须为正数：$connectedIdleTimeoutMs" }
    }

    /** 最近一次 WS 消息收发时刻（调用方时钟毫秒）；null = 无已配对会话 / 未跟踪。 */
    private var lastActivityMs: Long? = null

    /**
     * 记录一次 WS 消息收发（配对成功、input/clear/commit 到达等）：窗口自此刻重新起算。
     * 首次调用 = 会话窗口开始（配对成功即首次活动）。
     */
    fun onActivity(nowMs: Long) {
        lastActivityMs = nowMs
    }

    /** 会话窗口结束（已配对连接断开 / 服务停止）：清除窗口，[shouldExit] 恒否。 */
    fun onSessionEnded() {
        lastActivityMs = null
    }

    /**
     * 此刻是否已达到「已连接无活动」自动退出条件：存在活动记录且距最近活动满 [connectedIdleTimeoutMs]。
     * 仅作状态判定，不做任何执行动作（停服走 FR-B7 通知链、收起键盘由接线层负责）。
     */
    fun shouldExit(nowMs: Long): Boolean {
        val since = lastActivityMs ?: return false
        return nowMs - since >= connectedIdleTimeoutMs
    }

    companion object {
        /** 正式阈值 = 10 分钟（决策 59：PRD 第 12 章已连接无活动自动退出条款）。 */
        const val DEFAULT_CONNECTED_IDLE_TIMEOUT_MS = 10L * 60L * 1000L
    }
}
