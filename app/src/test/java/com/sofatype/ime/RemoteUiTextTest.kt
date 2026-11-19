package com.sofatype.ime

import com.sofatype.ime.remote.ClientCode
import com.sofatype.ime.remote.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteUiTextTest {

    @Test
    fun guideStepsMatchPrdFrB1Verbatim() {

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

        assertEquals("等待手机扫码", RemoteUiText.STATUS_WAITING_SCAN)
        assertEquals("实时接收中", RemoteUiText.STATUS_CONNECTED)
    }

    @Test
    fun connectedFaceAndFromLabelsPerDecision61() {

        assertEquals("已连接", RemoteUiText.CONNECTED_FACE_TEXT)
        assertEquals("来自 ·", RemoteUiText.CONNECTED_FROM_LABEL)
    }

    @Test
    fun clientCodeTextFormatsTypeDeviceAndTwoDigitNumber() {

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

        assertEquals("远 程 输 入", RemoteUiText.GUIDE_BUTTON_LABEL)
        assertEquals("进入远程输入模式", RemoteUiText.GUIDE_BUTTON_DESCRIPTION)
    }
}
