// T17 · 跨屏输入界面文案与格式化（FR-B1 / FR-B9 / FR-B3 / 决策 32/34）。
// 纯 Kotlin，不依赖 Android 类：文案为代码常量（IME 侧既有口径，同 KeyboardLayout 键面文字 / PairingCard 标注），
// 单元测试锁定与 PRD 原文逐字一致。格式化函数输出状态行展示文本：
// 设备名来自 hello.deviceName（决策 34：会话内存驻留用于展示，不入日志不持久化）。
package com.sofatype.ime

/** 跨屏输入界面文案（FR-B1 引导卡 / FR-B9 状态行 / FR-B3 远程主卡 / 决策 32/33）。 */
object RemoteUiText {

    // ---- 引导卡（FR-B1，画板①原文） ----

    /** 副卡标题。 */
    const val GUIDE_TITLE = "跨屏输入"

    /** 未连接状态行文字（灰点态）。 */
    const val STATUS_NOT_CONNECTED = "未连接"

    /** 三步指引（FR-B1 原文三行）。 */
    val GUIDE_STEPS: List<String> = listOf(
        "手机与电视连接同一 Wi-Fi",
        "点按「远程输入」出示二维码",
        "手机扫码，即可远程打字",
    )

    /** 主按钮键面（画板①「远 程 输 入」间隔排印）。 */
    const val GUIDE_BUTTON_LABEL = "远 程 输 入"

    /** 主按钮无障碍标注（画板① aria-label / PRD 第 8 章）。 */
    const val GUIDE_BUTTON_DESCRIPTION = "进入远程输入模式"

    /** 按钮下提示文案（FR-B1 原文）。 */
    const val GUIDE_BUTTON_HINT = "进入后：左侧显示手机输入内容，此卡变为二维码"

    // ---- 状态行（FR-B9 三态 / 决策 32） ----

    /** 等待扫码状态行文字（青点态，远程模式主卡状态行等待态）。 */
    const val STATUS_WAITING_SCAN = "等待手机扫码"

    /**
     * 「连接已断开」瞬时提示文字（T18 · FR-B8 原文 / Q3 lead 拍板）：已配对连接断开时主卡
     * 状态行短暂呈现（灰点），约 3s 后回 [STATUS_WAITING_SCAN] 终态；FR-B9 三态文字不动。
     */
    const val STATUS_DISCONNECTED = "连接已断开"

    /** 已连接状态行文字（绿点态主标题，FR-B3「实时接收中」）。 */
    const val STATUS_CONNECTED = "实时接收中"

    /** 已连接卡面文字前缀（FR-B9「已连接 · 设备名」）。 */
    private const val CONNECTED_FACE_PREFIX = "已连接 · "

    /** 已连接卡面（FR-B9）：deviceName 为 null（hello 未携带）时只显示「已连接」。 */
    fun connectedFaceText(deviceName: String?): String =
        if (deviceName.isNullOrEmpty()) "已连接" else "$CONNECTED_FACE_PREFIX$deviceName"

    /**
     * 远程主卡状态行「来自 · <设备名>」（FR-B3；决策 34：协议无位置字段，只展示设备名）。
     * hello 未携带设备名（null / 空）时返回 null——渲染层据此隐藏「来自」行（T18 · T17 遗留：
     * 修复「来自 · 」悬空尾巴），不再输出悬空前缀。
     */
    fun connectedFromText(deviceName: String?): String? {
        val name = deviceName?.takeIf { it.isNotBlank() } ?: return null
        return "来自 · $name"
    }

    // ---- 远程模式主卡其余框架（FR-B3 / 决策 33，画板⑤原文） ----

    /** 「退出远程」按钮键面。 */
    const val EXIT_BUTTON_LABEL = "退出远程"

    /** 「退出远程」按钮无障碍标注（画板⑤ aria-label / PRD 第 8 章）。 */
    const val EXIT_BUTTON_DESCRIPTION = "退出远程模式"

    /** 主卡底部提示文案（FR-B3 原文，内层引号随 PRD 用单引号层级）。 */
    const val REMOTE_BOTTOM_HINT = "在手机上打字，内容将实时显示在这里；手机端点『上屏』后写入电视输入框"
}
