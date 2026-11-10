// T51 · FR-D4 安装确认去重（2026-11-10 户主拍板，docs/issues/open/T51.md）守卫单元测试：
// 下载完成后不再弹应用内「是否立即安装？」确认框，直接提交安装——确认语义由系统层完整承载
//（API 23 基线 = 系统安装器包确认 + 商店安全审核两道系统级确认，设备档案坑 16）。
// SettingsActivity 为 Android 交互层，纯 JVM 单测无法实例化（无 Robolectric，仓库口径），
// 确认框路径移除以源码扫描锁定回归——源码扫描先例：UpdateApiCompatTest（T46）、
// AccessibilityLabelsTest（T26，直读源文件口径）。行为级取证走模拟器（API 34 会话分支）/
// 真机（API 23 UI 渠道）走查。
package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DirectInstallFlowTest {

    /** 路径口径 = app/ 模块目录（UpdateApiCompatTest 同口径）。 */
    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    @Test
    fun `settingsActivity移除应用内安装确认框路径`() {
        val text = mainSource("SettingsActivity.kt")
        // 确认框四要素全部不得回归：AlertDialog / 框字段 / 确认态 / 框文案资源引用
        for (banned in listOf("AlertDialog", "confirmDialog", "READY_TO_INSTALL", "install_dialog_")) {
            assertFalse("SettingsActivity 不得再引用确认框要素 $banned（T51 拍板：去应用内确认框）", text.contains(banned))
        }
    }

    @Test
    fun `下载完成路径直接提交安装`() {
        val text = mainSource("SettingsActivity.kt")
        // 下载完成回调直接走渠道门控提交（无中间确认环节），字面锁定接线
        assertTrue(
            "下载完成应直接 submitInstall(release, file)（T51：确认语义由系统层承载）",
            text.contains("postIfCurrent(generation) { submitInstall(release, file) }"),
        )
    }

    @Test
    fun `stringsXml移除安装确认框文案`() {
        val text = File("src/main/res/values/strings.xml").readText()
        assertFalse(
            "strings.xml 不得保留确认框文案 install_dialog_*（T51：随路径一并移除）",
            text.contains("install_dialog_"),
        )
    }

    @Test
    fun `渠道门控与结果判定链保持不变`() {
        // 「失败可重试」与版本判定链不变（T51 边界）：渠道门控与 UI 渠道判定模型仍在
        val channels = File("src/main/java/com/sofatype/ime/update/InstallChannel.kt").readText()
        val outcome = File("src/main/java/com/sofatype/ime/update/UiInstallOutcome.kt").readText()
        assertTrue("InstallChannels 渠道门控须保留（决策 36 分支行为不变）", channels.contains("SESSION_MIN_SDK"))
        assertTrue("UI 渠道 versionCode 判定链须保留（T51：判定链不变）", outcome.contains("decisionFor"))
    }
}
