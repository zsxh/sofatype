// T17 · 跨屏连接状态机（FR-B9 三态 / 决策 32 三卡面 / 决策 33 主卡模式）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（TASKS T17 施工口径）。
// 口径（决策 32）：副卡按整卡面切换呈现连接三态——
//   未连接 = 引导卡（灰点状态行 + 三步指引 + 「远程输入」主按钮，FR-B1）；
//   等待扫码 = 配对卡整卡（二维码 + URL 一行，FR-B4 / 决策 4，该卡面本身即等待扫码态）；
//   已连接 = 「已连接」卡面（绿点 + 设备名，不显示已失效二维码）。
// 「等待扫码」青点状态行落在远程模式主卡状态行的等待态（决策 32）；
// 主卡模式（决策 33）：服务运行中 = 远程模式（FR-B3 框架），停止 = 键盘态。
// 状态完全由 Manager 可观测值推导（服务运行 / 配对 token 有效 / 配对设备名），
// 无隐藏事件史：同输入恒得同状态（单一事实源在 Manager，UI 只做渲染）。
package com.sofatype.ime

/** 跨屏输入连接状态（FR-B9 三态）。 */
enum class RemoteStatus {
    /** 未连接：服务未运行（键盘态默认）。 */
    NOT_CONNECTED,

    /** 等待扫码：服务运行中、配对 token 未消费（副卡 = 配对卡；主卡状态行青点）。 */
    WAITING_SCAN,

    /** 已连接：服务运行中、token 已消费（副卡 = 已连接卡面；主卡状态行绿点）。 */
    CONNECTED,
}

/** 主卡模式（决策 33）：服务运行中 = 远程模式（FR-B3 框架），否则键盘态。 */
enum class RemoteMode { KEYBOARD, REMOTE }

/** 副卡卡面（决策 32 三卡面整卡切换）。 */
enum class RemoteSideCardFace {
    /** 引导卡：标题 + 灰点状态行 + 三步指引 + 主按钮 + 按钮下提示（FR-B1）。 */
    GUIDE,

    /** 配对卡：二维码 + URL 一行（FR-B4 / 决策 4），即「等待扫码」态卡面。 */
    PAIRING,

    /** 已连接卡面：绿点 + 设备名，不显示已失效二维码（决策 32）。 */
    CONNECTED,
}

/**
 * 跨屏输入 UI 状态（不可变值对象）。
 * 由 [of] 从 Manager 可观测值推导；UI 层依 [mode] 切主卡、依 [sideCardFace] 切副卡、
 * 依 [status] 渲染主卡状态行三态（灰 / 青 / 绿）。
 */
data class RemoteUiState(
    val mode: RemoteMode,
    val status: RemoteStatus,
    /** 配对设备名（hello.deviceName，决策 34：会话内存驻留，停止即清）；未连接 / 等待态为 null。 */
    val deviceName: String?,
) {
    /** 副卡卡面（决策 32 三态 → 三卡面一一对应）。 */
    val sideCardFace: RemoteSideCardFace = when (status) {
        RemoteStatus.NOT_CONNECTED -> RemoteSideCardFace.GUIDE
        RemoteStatus.WAITING_SCAN -> RemoteSideCardFace.PAIRING
        RemoteStatus.CONNECTED -> RemoteSideCardFace.CONNECTED
    }

    companion object {
        /**
         * 从 Manager 可观测值推导 UI 状态（单一推导入口，单测锁定）：
         * - 服务未运行 → 键盘态 / 未连接 / 引导卡（deviceName 防御性忽略：会话已结束不展示残留值）；
         * - 运行中 + token 有效 → 远程模式 / 等待扫码 / 配对卡；
         * - 运行中 + token 已消费 → 远程模式 / 已连接 / 已连接卡面（deviceName 可为 null：hello 未携带时 UI 只显示「已连接」）。
         */
        fun of(
            serviceRunning: Boolean,
            pairingTokenActive: Boolean,
            deviceName: String?,
        ): RemoteUiState = when {
            !serviceRunning -> RemoteUiState(RemoteMode.KEYBOARD, RemoteStatus.NOT_CONNECTED, null)
            pairingTokenActive -> RemoteUiState(RemoteMode.REMOTE, RemoteStatus.WAITING_SCAN, null)
            else -> RemoteUiState(RemoteMode.REMOTE, RemoteStatus.CONNECTED, deviceName)
        }
    }
}
