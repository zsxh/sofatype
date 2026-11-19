package com.sofatype.ime

object RemoteUiText {

    const val GUIDE_TITLE = "跨屏输入"

    const val STATUS_NOT_CONNECTED = "未连接"

    val GUIDE_STEPS: List<String> = listOf(
        "手机与电视连接同一 Wi-Fi",
        "点按「远程输入」出示二维码",
        "手机扫码，即可远程打字",
    )

    const val GUIDE_BUTTON_LABEL = "远 程 输 入"

    const val GUIDE_BUTTON_DESCRIPTION = "进入远程输入模式"

    const val GUIDE_BUTTON_HINT = "进入后：左侧显示手机输入内容，此卡变为二维码"

    const val STATUS_WAITING_SCAN = "等待手机扫码"

    const val STATUS_DISCONNECTED = "连接已断开"

    const val STATUS_CONNECTED = "实时接收中"

    const val CONNECTED_FACE_TEXT = "已连接"

    const val CONNECTED_FROM_LABEL = "来自 ·"

    const val EXIT_BUTTON_LABEL = "退出远程"

    const val EXIT_BUTTON_DESCRIPTION = "退出远程模式"

    const val REMOTE_BOTTOM_HINT = "在手机上打字，内容将实时显示在这里；手机端点『上屏』后写入电视输入框"
}
