package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DirectInstallFlowTest {

    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    @Test
    fun `settingsActivity移除应用内安装确认框路径`() {
        val text = mainSource("SettingsActivity.kt")

        for (banned in listOf("AlertDialog", "confirmDialog", "READY_TO_INSTALL", "install_dialog_")) {
            assertFalse("SettingsActivity 不得再引用确认框要素 $banned（T51 拍板：去应用内确认框）", text.contains(banned))
        }
    }

    @Test
    fun `下载完成路径直接提交安装`() {
        val text = mainSource("SettingsActivity.kt")

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

        val channels = File("src/main/java/com/sofatype/ime/update/InstallChannel.kt").readText()
        val outcome = File("src/main/java/com/sofatype/ime/update/UiInstallOutcome.kt").readText()
        assertTrue("InstallChannels 渠道门控须保留（决策 36 分支行为不变）", channels.contains("SESSION_MIN_SDK"))
        assertTrue("UI 渠道 versionCode 判定链须保留（T51：判定链不变）", outcome.contains("decisionFor"))
    }
}
