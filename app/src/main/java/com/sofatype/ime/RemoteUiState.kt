// T17/T35 · 跨屏连接状态机（FR-B9 三态 / 决策 32/35 卡面 / 决策 33 主卡模式）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（TASKS T17 施工口径）。
// 口径（决策 35 修订决策 32 的已连接卡面）：副卡呈现——
//   未连接 = 引导卡（灰点状态行 + 三步指引 + 「远程输入」主按钮，FR-B1）；
//   等待扫码 = 配对卡整卡（二维码 + URL 一行，FR-B4 / 决策 4，该卡面本身即「等待扫码」态）；
//   已连接 = 二维码卡保留（决策 32 独立「已连接」卡面废止）：二维码为配对成功后滚动签发的
//   新 token 有效码，下方网址行原位换成绿点状态行「已连接」+ 连接代号胶囊（决策 35；
//   T72 · 决策 61：设备名展示口径由代号胶囊替代）。
// 「已连接」的判定 = 存在已配对 WS 会话（pairedSessionActive），不再以 token 已消费判定——
// token 滚动签发后配对成功即出新码（token 恒有效），token 可观测值退出状态推导。
// 断连（会话关闭、非被顶替）→ pairedSessionActive=false → 回等待扫码卡（决策 35）。
// 「等待扫码」青点状态行落在远程模式主卡状态行的等待态（决策 32）；
// 主卡模式（决策 33）：服务运行中 = 远程模式（FR-B3 框架），停止 = 键盘态。
// 状态完全由 Manager 可观测值推导（服务运行 / 已配对会话存在 / 连接代号），
// 无隐藏事件史：同输入恒得同状态（单一事实源在 Manager，UI 只做渲染）。
// T72 增量（决策 61 · 连接代号）：配对设备名展示口径由连接代号胶囊替代——
// 状态携带 ClientCode（类型 + 两位序号），TV / H5 两端同显；未连接 / 等待态为 null。
package com.sofatype.ime

import com.sofatype.ime.remote.ClientCode

/** 跨屏输入连接状态（FR-B9 三态）。 */
enum class RemoteStatus {
    /** 未连接：服务未运行（键盘态默认）。 */
    NOT_CONNECTED,

    /** 等待扫码：服务运行中、无已配对会话、配对 token 未消费（副卡 = 配对卡；主卡状态行青点）。 */
    WAITING_SCAN,

    /** 已连接：服务运行中、存在已配对会话（副卡 = 二维码卡 + 已连接状态行；主卡状态行绿点）。 */
    CONNECTED,
}

/** 主卡模式（决策 33）：服务运行中 = 远程模式（FR-B3 框架），否则键盘态。 */
enum class RemoteMode { KEYBOARD, REMOTE }

/** 副卡卡面（决策 32 三态 → 三卡面一一对应；已连接卡面构成按决策 35 修订）。 */
enum class RemoteSideCardFace {
    /** 引导卡：标题 + 灰点状态行 + 三步指引 + 主按钮 + 按钮下提示（FR-B1）。 */
    GUIDE,

    /** 配对卡：二维码 + URL 一行（FR-B4 / 决策 4），即「等待扫码」态卡面。 */
    PAIRING,

    /** 已连接：二维码卡保留 + 网址行原位换绿点状态行「已连接」+ 代号胶囊（决策 35 / 61）。 */
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
    /** 连接代号（T72 · 决策 61：类型 + 两位序号，Manager 签发）；未连接 / 等待态为 null。 */
    val clientCode: ClientCode?,
) {
    /** 副卡卡面（三态 → 三卡面一一对应）。 */
    val sideCardFace: RemoteSideCardFace = when (status) {
        RemoteStatus.NOT_CONNECTED -> RemoteSideCardFace.GUIDE
        RemoteStatus.WAITING_SCAN -> RemoteSideCardFace.PAIRING
        RemoteStatus.CONNECTED -> RemoteSideCardFace.CONNECTED
    }

    companion object {
        /**
         * 从 Manager 可观测值推导 UI 状态（单一推导入口，单测锁定）：
         * - 服务未运行 → 键盘态 / 未连接 / 引导卡（clientCode 防御性忽略：会话已结束不展示残留值）；
         * - 运行中 + 存在已配对会话 → 远程模式 / 已连接 / 二维码卡 + 状态行（clientCode 可为 null：
         *   防御分支，渲染层只显示「已连接」不渲染胶囊）；
         * - 运行中 + 无已配对会话 → 远程模式 / 等待扫码 / 配对卡（含 token 已消费后滚动签发新码态、
         *   已配对连接断开后的回退态；token 无效的防御分支同样归等待态）。
         */
        fun of(
            serviceRunning: Boolean,
            pairedSessionActive: Boolean,
            clientCode: ClientCode?,
        ): RemoteUiState = when {
            !serviceRunning -> RemoteUiState(RemoteMode.KEYBOARD, RemoteStatus.NOT_CONNECTED, null)
            pairedSessionActive -> RemoteUiState(RemoteMode.REMOTE, RemoteStatus.CONNECTED, clientCode)
            else -> RemoteUiState(RemoteMode.REMOTE, RemoteStatus.WAITING_SCAN, null)
        }
    }
}
