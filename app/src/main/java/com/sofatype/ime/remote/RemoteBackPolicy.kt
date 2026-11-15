// T74 · 远程模式 BACK 按连接态分流决策模型（决策 62 · FR-B7「系统返回键退出」条款修订）：
// 远程服务运行中按系统返回键不再一律退出远程——按连接态分流：
//   已连接态（存在已配对会话）BACK = 只收起键盘窗口（FR-A1 语义），HTTP/WS 服务、已配对会话、
//   远程缓冲与连接代号全部保持，手机端无感知（无 disconnect 通知）；键盘已收起时透传宿主
//   （T59 隐藏态口径同族：收起无效果且拦截宿主返回语义），服务保活不受影响；
//   重入（切输入框唤起键盘）由既有 onStartInputView T17 路径自动回远程模式。
//   未连接态（等待扫码，无已配对会话）BACK = 维持现行 FR-B7 完整清理退出（通知手机 +
//   关监听 + 清 token / 缓冲 / 设备名），不在无人使用时残留监听端口；该分支与现行行为一致，
//   不区分键盘显示态（现行口径不变）。
//   服务未运行（键盘态）BACK = 既有键盘态分支（显示态收起 / 隐藏态透传，T59 口径），不变。
// 纯 Kotlin，不依赖 Android 类：分流结论由本模型单点给出（单一事实源），接线在
// SofatypeImeService.dispatchKeyDown BACK 分支；单元测试锁定四分支。
// 保活窗口兜底不变：已连接无活动 10 分钟自退（决策 59）与无手机连接 30 分钟空闲自退（T25）
// 在键盘收起期间照常计时到期（RemoteInputServerManager 既有排程，不因收起顺延）。
package com.sofatype.ime.remote

/**
 * 远程模式 BACK 分流决策（T74 · 决策 62）。
 */
object RemoteBackPolicy {

    /** BACK 裁决结论。 */
    enum class Action {
        /** 完整清理退出远程模式（FR-B7 既有链路：通知手机 + 关监听 + 清 token / 缓冲 / 设备名）。 */
        EXIT_REMOTE,

        /** 只收起键盘窗口保活（FR-A1 语义）：服务 / 会话 / 远程缓冲 / 连接代号全保持，手机端无感知。 */
        HIDE_KEEP_ALIVE,

        /** 键盘态既有分支（显示态 finishComposition + 收起 / 隐藏态透传宿主，T59 口径）。 */
        KEYBOARD_DEFAULT,
    }

    /**
     * 一次 BACK 按击的分流裁决（唯一入口）：
     * - 服务未运行 → 键盘态既有分支；
     * - 运行中 + 无已配对会话（等待扫码）→ 完整清理退出（FR-B7 既有，不残留监听端口）；
     * - 运行中 + 已配对会话 + 键盘显示态 → 只收起键盘保活（决策 62）；
     * - 运行中 + 已配对会话 + 键盘已收起 → 键盘态既有分支（隐藏态透传宿主，T59 口径同族；
     *   收起无效果且拦截宿主返回语义；保活不受影响，重入沿 T17 路径自动回远程模式）。
     */
    fun resolve(
        serviceRunning: Boolean,
        pairedSessionActive: Boolean,
        inputViewShown: Boolean,
    ): Action = when {
        !serviceRunning -> Action.KEYBOARD_DEFAULT
        !pairedSessionActive -> Action.EXIT_REMOTE
        inputViewShown -> Action.HIDE_KEEP_ALIVE
        else -> Action.KEYBOARD_DEFAULT
    }
}
