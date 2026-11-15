// T17 · 跨屏界面文案单元测试：锁定与 PRD 原文逐字一致（FR-B1 三步指引 / 按钮下提示、
// FR-B9 状态行三态、FR-B3 实时接收中 / 底部提示 / 退出远程）。
// T18 · FR-B8「连接已断开」瞬时提示文案（Q3 lead 拍板）。
// T72 · 决策 61：设备名展示口径由连接代号胶囊替代——锁定「已连接」行文字、「来自 ·」前缀
// 与 ClientCode 全文 / 序号文本（示例「微信设备 01」）。
package com.sofatype.ime

import com.sofatype.ime.remote.ClientCode
import com.sofatype.ime.remote.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteUiTextTest {

    @Test
    fun guideStepsMatchPrdFrB1Verbatim() {
        // FR-B1 原文三步：① 手机与电视连接同一 Wi-Fi ② 点按「远程输入」出示二维码 ③ 手机扫码，即可远程打字
        assertEquals(3, RemoteUiText.GUIDE_STEPS.size)
        assertEquals("手机与电视连接同一 Wi-Fi", RemoteUiText.GUIDE_STEPS[0])
        assertEquals("点按「远程输入」出示二维码", RemoteUiText.GUIDE_STEPS[1])
        assertEquals("手机扫码，即可远程打字", RemoteUiText.GUIDE_STEPS[2])
    }

    @Test
    fun guideButtonHintMatchesPrdFrB1Verbatim() {
        assertEquals("进入后：左侧显示手机输入内容，此卡变为二维码", RemoteUiText.GUIDE_BUTTON_HINT)
    }

    @Test
    fun guideTitleAndStatusMatchPrdFrB1() {
        assertEquals("跨屏输入", RemoteUiText.GUIDE_TITLE)
        assertEquals("未连接", RemoteUiText.STATUS_NOT_CONNECTED)
    }

    @Test
    fun statusThreeStatesMatchPrdFrB9AndFrB3() {
        // 等待扫码（决策 32 落主卡状态行等待态）/ 已连接主标题（FR-B3 实时接收中）
        assertEquals("等待手机扫码", RemoteUiText.STATUS_WAITING_SCAN)
        assertEquals("实时接收中", RemoteUiText.STATUS_CONNECTED)
    }

    @Test
    fun connectedFaceAndFromLabelsPerDecision61() {
        // T72 · 决策 61：副卡已连接状态行只保留「已连接」（胶囊紧随其后）；主卡「来自 ·」前缀不变
        assertEquals("已连接", RemoteUiText.CONNECTED_FACE_TEXT)
        assertEquals("来自 ·", RemoteUiText.CONNECTED_FROM_LABEL)
    }

    @Test
    fun clientCodeTextFormatsTypeDeviceAndTwoDigitNumber() {
        // 决策 61 胶囊文案：「[类型]设备[两位序号]」，画板示例「微信设备 01」；四类型逐字锁定
        assertEquals("微信设备 01", ClientCode(DeviceType.WECHAT, 1).text)
        assertEquals("苹果设备 03", ClientCode(DeviceType.APPLE, 3).text)
        assertEquals("安卓设备 09", ClientCode(DeviceType.ANDROID, 9).text)
        assertEquals("浏览器设备 12", ClientCode(DeviceType.BROWSER, 12).text)
    }

    @Test
    fun clientCodeNumberTextPadsToTwoDigits() {
        assertEquals("01", ClientCode(DeviceType.WECHAT, 1).numberText)
        assertEquals("08", ClientCode(DeviceType.WECHAT, 8).numberText)
        assertEquals("12", ClientCode(DeviceType.WECHAT, 12).numberText)
    }

    @Test
    fun statusDisconnectedTextMatchesFrB8() {
        // T18 · FR-B8 原文（Q3 lead 拍板：灰点瞬时过渡提示，终态仍为决策 32 锁定的「等待手机扫码」）
        assertEquals("连接已断开", RemoteUiText.STATUS_DISCONNECTED)
    }

    @Test
    fun remoteBottomHintMatchesPrdFrB3Verbatim() {
        assertEquals(
            "在手机上打字，内容将实时显示在这里；手机端点『上屏』后写入电视输入框",
            RemoteUiText.REMOTE_BOTTOM_HINT,
        )
    }

    @Test
    fun exitButtonLabelAndDescriptionMatchArtboardAria() {
        assertEquals("退出远程", RemoteUiText.EXIT_BUTTON_LABEL)
        assertEquals("退出远程模式", RemoteUiText.EXIT_BUTTON_DESCRIPTION)
    }

    @Test
    fun guideButtonLabelAndDescriptionMatchArtboard() {
        // 键面按画板①「远 程 输 入」间隔排印；无障碍标注 = 画板① aria-label
        assertEquals("远 程 输 入", RemoteUiText.GUIDE_BUTTON_LABEL)
        assertEquals("进入远程输入模式", RemoteUiText.GUIDE_BUTTON_DESCRIPTION)
    }
}
