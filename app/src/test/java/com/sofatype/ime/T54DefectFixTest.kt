package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T54DefectFixTest {

    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    private val carrier: String get() = mainSource("TestTypingActivity.kt")
    private val activity: String get() = mainSource("SettingsActivity.kt")
    private val service: String get() = mainSource("SofatypeImeService.kt")

    @Test
    fun `焦点接管仅在可见性跳变沿执行且目标为编辑框`() {

        val layoutIdx = carrier.indexOf("imeShown != lastImeShown")
        assertTrue("可见性跳变沿判定存在（防非沿布局抢焦点）", layoutIdx >= 0)
        val edgeEnd = carrier.indexOf("lastImeShown = imeShown", layoutIdx)
        assertTrue("跳变沿分支闭合存在", edgeEnd > layoutIdx)
        val edgeBody = carrier.substring(layoutIdx, edgeEnd)
        assertTrue(
            "接管须在跳变沿分支内（T54 缺陷修复 R1）",
            edgeBody.contains("SettingsFocusTakeover.shouldTakeFocus("),
        )
        assertTrue(
            "收起沿须无条件 requestFocusFromTouch（焦点回编辑框：触摸模式下 FocusFinder 移焦静默失效，走查实测；T77 双框目标经 takeoverTarget 判定，锁定见 T77DualInputFormTest）",
            edgeBody.contains("requestFocusFromTouch()"),
        )
        assertFalse(
            "接管目标不得再指向 btn_check_update（T54 R2 旧目标废止）",
            edgeBody.contains("btnCheckUpdate.requestFocusFromTouch()"),
        )
    }

    @Test
    fun `收起沿接管日志与会话保活语义落源码`() {
        assertTrue(
            "收起沿接管日志存在（走查取证锚点）",
            carrier.contains("焦点接管：键盘已收起，焦点回 test_input（输入会话保活）"),
        )
        assertTrue(
            "接管决策语义 = 收起且编辑框不持焦点（tap 过按钮态）",
            mainSource("SettingsFocusTakeover.kt").contains("!imeShown && !editorHasFocus"),
        )
    }

    @Test
    fun `preIME 分路与 T59 宿主分流零改动保留`() {

        assertTrue(
            "键盘显示态须透传 IME（T54 R2 既有门控）",
            activity.contains("if (lastImeShown == true) return false"),
        )

        assertTrue(
            "CONFIRM 按焦点分流（拍板 5）保留",
            activity.contains("SettingsKeyRouter.confirmActivatesIme(focused)"),
        )
        assertTrue("可输入控件分支 showSoftInput 保留（拍板 5）", activity.contains("showSoftInput("))
        assertTrue("其余控件分支 performClick 保留（T54 口径）", activity.contains("performClick()"))
    }

    @Test
    fun `输入法切换广播接线重建输入会话`() {

        assertTrue(
            "须注册 ACTION_INPUT_METHOD_CHANGED 广播（T62）",
            carrier.contains("IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED)"),
        )
        assertTrue(
            "收到广播须 restartInput 重建输入会话（T62；T77 双框：目标 = 当前持焦输入框，无则回落 test_input，锁定见 T77DualInputFormTest）",
            carrier.contains("imm?.restartInput(restartTarget)"),
        )
        assertTrue(
            "切换广播日志存在（走查取证锚点）",
            carrier.contains("输入法切换广播 -> 重建输入会话（T62）"),
        )
    }

    @Test
    fun `T62 接线须在门控块内且随窗口注销`() {

        val debugIdx = carrier.indexOf("TestTypingGate.contentVisible(BuildConfig.DEBUG)")

        val imeIdx = carrier.indexOf("Intent.ACTION_INPUT_METHOD_CHANGED)", debugIdx)
        assertTrue("门控块存在（TestTypingGate 承载，T79 · 决策 65 起 debug / release 均放行）", debugIdx >= 0)
        assertTrue(
            "T62 广播接线须在门控块内（T79 · 决策 65：release 同样接线生效——release 也有输入框，黑洞面存在）",
            imeIdx > debugIdx,
        )
        assertTrue(
            "onDestroy 须注销输入法切换接收器（退出零残留，AGENTS 禁止事项 3）",
            carrier.contains("imeChangedReceiver?.let { unregisterReceiver(it) }"),
        )
    }

    @Test
    fun `IME服务保持零改动且T59口径接线在位`() {
        assertFalse(
            "SofatypeImeService 不得引用设置页侧本轮新接线（T59 挂账禁区）",
            service.contains("SettingsFocusTakeover") ||
                service.contains("SettingsKeyRouter") ||
                service.contains("SettingsRootView"),
        )
        assertTrue(
            "隐藏态确认键 requestShowSelf 唤起保留（拍板 3）",
            service.contains("requestShowSelf(0)"),
        )
        assertTrue(
            "隐藏态 BACK 透传保留（拍板 1）",
            service.contains("return super.onKeyDown(keyCode, ev)"),
        )
        assertTrue(
            "T38 两窗接线保留（shouldVetoShowRequest / shouldSwallowConfirmKey）",
            service.contains("showGuard.shouldVetoShowRequest(") &&
                service.contains("showGuard.shouldSwallowConfirmKey("),
        )
    }
}
