// T47 · UI 渠道安装结果判定单元测试（决策 36：无会话回执，回设置页比对自身 versionCode——
// 版本升 = 成功；未升 = 用户在安装器取消或失败，入「失败可重试」态）+ in-flight 标记
// 载荷编解码（SharedPreferences 单字符串值：升级成功替换包必杀进程，标记须跨进程存活）。
package com.sofatype.ime.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiInstallReceiptTest {

    @Test
    fun versionCodeIncreasedMeansUpdated() {
        assertEquals(UiInstallDecision.UPDATED, UiInstallReceipt.decisionFor(1, 2))
        assertEquals(UiInstallDecision.UPDATED, UiInstallReceipt.decisionFor(1, 100))
        assertEquals(UiInstallDecision.UPDATED, UiInstallReceipt.decisionFor(63, 64))
    }

    @Test
    fun versionCodeUnchangedMeansNotUpdated() {
        // 用户在安装器取消 / 安装失败：版本未升，归失败可重试（T21 口径）
        assertEquals(UiInstallDecision.NOT_UPDATED, UiInstallReceipt.decisionFor(1, 1))
    }

    @Test
    fun versionCodeDecreasedMeansNotUpdated() {
        // 降级为异常路径，不判成功
        assertEquals(UiInstallDecision.NOT_UPDATED, UiInstallReceipt.decisionFor(2, 1))
    }

    @Test
    fun invalidBeforeCodeMeansNotUpdated() {
        // before 非法（≤0）不可判「升」，兜底归失败可重试
        assertEquals(UiInstallDecision.NOT_UPDATED, UiInstallReceipt.decisionFor(0, 5))
        assertEquals(UiInstallDecision.NOT_UPDATED, UiInstallReceipt.decisionFor(-1, 5))
    }
}

class UiInstallMarkerTest {

    @Test
    fun encodeProducesStablePayload() {
        assertEquals("before=1", UiInstallMarker.encode(1))
        assertEquals("before=63", UiInstallMarker.encode(63))
    }

    @Test
    fun decodeRoundTripsEncode() {
        assertEquals(1, UiInstallMarker.decode(UiInstallMarker.encode(1)))
        assertEquals(63, UiInstallMarker.decode(UiInstallMarker.encode(63)))
    }

    @Test
    fun decodeRejectsNullOrMalformedPayload() {
        assertNull(UiInstallMarker.decode(null))
        assertNull(UiInstallMarker.decode(""))
        assertNull(UiInstallMarker.decode("nonsense"))
        assertNull(UiInstallMarker.decode("before="))
        assertNull(UiInstallMarker.decode("before=abc"))
        // 前缀混淆载荷不误读
        assertNull(UiInstallMarker.decode("xxbefore=1"))
    }

    @Test
    fun decodeRejectsNonPositiveVersionCode() {
        assertNull(UiInstallMarker.decode("before=0"))
        assertNull(UiInstallMarker.decode("before=-3"))
    }
}
