package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiChannelHealFlowTest {

    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    @Test
    fun `判定终态落地后排队自愈重建`() {
        val text = mainSource("SettingsActivity.kt")

        assertTrue("scheduleHealRecreate 定义须存在（T50 判定后自愈）", text.contains("private fun scheduleHealRecreate()"))
        assertTrue(
            "判定链 applyDecision 之后须排队自愈重建（T50：判定实例输入失活，换新实例呈现）",
            Regex("applyDecision\\(decision\\)\\s*\\n\\s*scheduleHealRecreate\\(\\)").containsMatchIn(text),
        )
        assertTrue("自愈须以 recreate() 实现（新实例 = 新窗口 = 新输入通道）", text.contains("recreate()"))
    }

    @Test
    fun `判定终态经savedState传递并在恢复实例应用`() {
        val text = mainSource("SettingsActivity.kt")

        assertTrue(
            "onSaveInstanceState 须写出判定终态（自愈重建实例恢复依据）",
            text.contains("outState.putString(KEY_SAVED_UI_CHANNEL_DECISION"),
        )
        assertTrue(
            "onCreate 须从 savedInstanceState 读回判定终态",
            text.contains("savedInstanceState\n            ?.getString(KEY_SAVED_UI_CHANNEL_DECISION"),
        )
        assertTrue("恢复分支 restoreUiChannelDecision 定义须存在", text.contains("private fun restoreUiChannelDecision("))
        assertTrue(
            "恢复分支须一次性消费 pending 终态（防二次应用）",
            Regex("restoreUiChannelDecision\\(decision: UiInstallDecision\\)[\\s\\S]*?pendingUiChannelDecision = null").containsMatchIn(text),
        )
    }

    @Test
    fun `恢复分支不再排队二次重建`() {
        val text = mainSource("SettingsActivity.kt")

        val start = text.indexOf("private fun restoreUiChannelDecision(")
        assertTrue("恢复分支定义缺失", start >= 0)
        val body = text.substring(start, text.indexOf("private fun", start + 1).takeIf { it > start } ?: text.length)
        assertFalse("恢复分支不得排队重建（防重建循环）", body.contains("scheduleHealRecreate"))
        assertFalse("恢复分支不得直接 recreate", body.contains("recreate()"))
    }

    @Test
    fun `无标记普通回前台不触发自愈重建`() {
        val text = mainSource("SettingsActivity.kt")

        val evaluateStart = text.indexOf("private fun evaluateUiChannelInstallResult()")
        assertTrue("判定方法定义缺失", evaluateStart >= 0)
        val body = text.substring(evaluateStart, text.indexOf("private fun restoreUiChannelDecision(", evaluateStart))
        val earlyReturn = body.indexOf("readUiInstallMarker() ?: return")
        val schedule = body.indexOf("scheduleHealRecreate()")
        assertTrue("无标记早退路径缺失", earlyReturn >= 0)
        assertTrue("自愈排队调用缺失", schedule >= 0)
        assertTrue(
            "无标记普通回前台须先于自愈排队早退（T50 修复边界：非判定实例行为不变）",
            earlyReturn < schedule,
        )
    }

    @Test
    fun `savedState载荷非法时回落普通启动路径`() {
        val text = mainSource("SettingsActivity.kt")

        assertTrue(
            "savedState 判定终态读回须 runCatching 容错（T50 修复边界）",
            Regex("runCatching \\{ UiInstallDecision\\.valueOf\\(raw\\) \\}").containsMatchIn(text),
        )
    }
}
