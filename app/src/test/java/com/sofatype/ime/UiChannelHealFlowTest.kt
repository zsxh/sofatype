// T50 · UI 渠道判定态入口自愈（docs/issues/open/T50.md）守卫单元测试：
// 升级替换包杀进程后 am start 重建的判定实例，本机（MiTV API 23）实测窗口输入派发整体失活
//（tap/OK/BACK 全不达，视图树与渲染正常）；修复 = 判定终态落地后 recreate() 换新实例呈现，
// 判定结果经 onSaveInstanceState/savedInstanceState 一次性传递，恢复实例不再二次重建。
// SettingsActivity 为 Android 交互层，纯 JVM 单测无法实例化（无 Robolectric，仓库口径），
// 接线以源码扫描锁定回归——源码扫描先例：UpdateApiCompatTest（T46）、DirectInstallFlowTest（T51）。
// 行为级取证走模拟器（等价复现路径）走查。
package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiChannelHealFlowTest {

    /** 路径口径 = app/ 模块目录（UpdateApiCompatTest 同口径）。 */
    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    @Test
    fun `判定终态落地后排队自愈重建`() {
        val text = mainSource("SettingsActivity.kt")
        // 判定即排队 recreate：接线三要素（排队方法定义、判定链调用、recreate 调用）
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
        // 传递链四要素：保存接线 / 恢复读回 / 恢复分支 / 一次性消费
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
        // 抽取 restoreUiChannelDecision 方法体（至下一个 private fun / companion 为止），
        // 区间内不得出现重建排队与 recreate 调用——否则判定终态页反复重建（T50 修复边界）
        val start = text.indexOf("private fun restoreUiChannelDecision(")
        assertTrue("恢复分支定义缺失", start >= 0)
        val body = text.substring(start, text.indexOf("private fun", start + 1).takeIf { it > start } ?: text.length)
        assertFalse("恢复分支不得排队重建（防重建循环）", body.contains("scheduleHealRecreate"))
        assertFalse("恢复分支不得直接 recreate", body.contains("recreate()"))
    }

    @Test
    fun `无标记普通回前台不触发自愈重建`() {
        val text = mainSource("SettingsActivity.kt")
        // 修复前行为保序：无 in-flight 标记时早退（?: return）必须先于自愈排队出现
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
        // 恢复读回须容错：runCatching 包裹 valueOf，非法载荷回落 null（普通启动路径）
        assertTrue(
            "savedState 判定终态读回须 runCatching 容错（T50 修复边界）",
            Regex("runCatching \\{ UiInstallDecision\\.valueOf\\(raw\\) \\}").containsMatchIn(text),
        )
    }
}
