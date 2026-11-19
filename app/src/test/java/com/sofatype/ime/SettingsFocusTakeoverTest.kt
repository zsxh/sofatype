package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsFocusTakeoverTest {

    companion object {

        private const val SCREEN_H = 1080
    }

    @Test
    fun `键盘未显示时可见帧为全屏判定为不可见`() {

        assertFalse(SettingsFocusTakeover.isImeShown(SCREEN_H, SCREEN_H))
        assertFalse(SettingsFocusTakeover.isImeShown(SCREEN_H - 1, SCREEN_H))
    }

    @Test
    fun `键盘占位时可见帧显著低于全屏判定为可见`() {

        assertTrue(SettingsFocusTakeover.isImeShown(580, SCREEN_H))
    }

    @Test
    fun `阈值边界等于阈值判为不可见低于阈值判为可见`() {
        val thresholdBottom = SCREEN_H - SettingsFocusTakeover.IME_FRAME_THRESHOLD_PX
        assertFalse(SettingsFocusTakeover.isImeShown(thresholdBottom, SCREEN_H))
        assertTrue(SettingsFocusTakeover.isImeShown(thresholdBottom - 1, SCREEN_H))
    }

    @Test
    fun `接管条件为键盘不可见且编辑框不持焦点`() {

        assertTrue(SettingsFocusTakeover.shouldTakeFocus(imeShown = false, editorHasFocus = false))

        assertFalse(SettingsFocusTakeover.shouldTakeFocus(imeShown = true, editorHasFocus = true))
        assertFalse(SettingsFocusTakeover.shouldTakeFocus(imeShown = true, editorHasFocus = false))

        assertFalse(SettingsFocusTakeover.shouldTakeFocus(imeShown = false, editorHasFocus = true))
    }

    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    @Test
    fun `测试打字页接线全局布局监听与纯决策`() {

        val text = mainSource("TestTypingActivity.kt")
        assertTrue(
            "焦点接管须走全局布局监听（T54 R2）",
            text.contains("addOnGlobalLayoutListener(focusTakeoverListener)"),
        )
        assertTrue(
            "接管判定须调用 SettingsFocusTakeover 纯决策（T54 R2）",
            text.contains("SettingsFocusTakeover.shouldTakeFocus("),
        )
        assertTrue(
            "收起沿须无条件 requestFocusFromTouch（T54 缺陷修复 R1 会话保活；T77 双框：目标经 takeoverTarget 判定 = 当前持焦框优先，无则回落 test_input）",
            text.contains("requestFocusFromTouch()"),
        )
        assertTrue(
            "可见性须走 isImeShown 阈值判定（T54 R2）",
            text.contains("SettingsFocusTakeover.isImeShown("),
        )
        assertTrue(
            "监听须在 onDestroy 移除（退出零残留）",
            text.contains("removeOnGlobalLayoutListener(listener)"),
        )
    }

    @Test
    fun `Manifest 声明 adjustResize 检测通道`() {
        val text = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "SettingsActivity 须声明 windowSoftInputMode=adjustResize（键盘收起检测前提）",
            text.contains("android:name=\".SettingsActivity\"") &&
                text.contains("android:windowSoftInputMode=\"adjustResize\""),
        )
    }

    @Test
    fun `根视图接线 pre-IME 键分路`() {
        val text = mainSource("SettingsActivity.kt")
        assertTrue(
            "须接线 SettingsRootView.preImeKeyHandler（T54 R2 pre-IME 分路）",
            text.contains("preImeKeyHandler = { handlePreImeKey(it) }"),
        )
        assertTrue(
            "须调用 SettingsKeyRouter 路由决策（T54 R2）",
            text.contains("SettingsKeyRouter.route("),
        )
        assertTrue(
            "方向路由须走 FocusFinder 焦点搜索（T54 R2）",
            text.contains("FocusFinder.getInstance().findNextFocus("),
        )
        assertTrue(
            "键盘显示态须透传 IME（打字链路零改动，T54 R2）",
            text.contains("if (lastImeShown == true) return false"),
        )
    }

    @Test
    fun `布局根视图为 SettingsRootView`() {
        val text = File("src/main/res/layout/activity_settings.xml").readText()
        assertTrue(
            "布局根节点须为 SettingsRootView（pre-IME 分路入口，T54 R2）",
            text.contains("com.sofatype.ime.SettingsRootView"),
        )
        assertTrue(
            "根节点须挂 settings_root id（Activity 接线依据，T54 R2）",
            text.contains("@+id/settings_root"),
        )
    }

    @Test
    fun `IME 服务保持零改动`() {
        val text = mainSource("SofatypeImeService.kt")
        assertFalse(
            "SofatypeImeService 为 T54 本轮禁区（T59 挂账），不得引用 SettingsFocusTakeover / SettingsKeyRouter / SettingsRootView",
            text.contains("SettingsFocusTakeover") ||
                text.contains("SettingsKeyRouter") ||
                text.contains("SettingsRootView"),
        )
    }
}
