// T50 Round 2 · 手动检查路径日志点守卫（2026-11-10 户主指令，review-qa Round 1 非阻塞建议 1）：
// startCheck 发起一条、onCheckResult 落态一条（三态标识 + 失败原因），TAG=UpdateCheck 既有口径。
// SettingsActivity 为 Android 交互层，纯 JVM 单测无法实例化（无 Robolectric，仓库口径），
// 日志接线以源码扫描锁定回归——先例：UpdateApiCompatTest（T46）、UiChannelHealFlowTest（T50 R1）。
// 红线锁定：日志语句仅含 endpoint / tag / reason / http 取值，不含用户输入内容。
package com.sofatype.ime

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManualCheckLogTest {

    /** 路径口径 = app/ 模块目录（UpdateApiCompatTest 同口径）。 */
    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    @Test
    fun `手动检查发起日志接线存在`() {
        val text = mainSource("SettingsActivity.kt")
        assertTrue(
            "startCheck 须以 UPDATE_LOG_TAG 写发起日志并携带 endpoint（T50 R2 取证口径）",
            text.contains("Log.i(UPDATE_LOG_TAG, \"手动检查发起 endpoint=\$endpoint\")"),
        )
    }

    @Test
    fun `检查落态日志覆盖三态标识与失败原因`() {
        val text = mainSource("SettingsActivity.kt")
        assertTrue(
            "onCheckResult 须以 UPDATE_LOG_TAG 写落态日志（T50 R2 取证口径）",
            text.contains("Log.i(UPDATE_LOG_TAG, \"手动检查结果 \${checkResultLabel(result)}\")"),
        )
        for (marker in listOf("result=UP_TO_DATE", "result=NEWER tag=", "result=FAILED reason=")) {
            assertTrue("落态日志须含三态标识 $marker（T50 R2）", text.contains(marker))
        }
        assertTrue("失败落态须携带 httpStatus（T50 R2）", text.contains("http=\${result.httpStatus ?: \"-\"}"))
    }

    @Test
    fun `日志摘要不含用户输入内容`() {
        val text = mainSource("SettingsActivity.kt")
        // 红线（AGENTS 禁止事项 2）：checkResultLabel 摘要仅允许 release.tag / reason / httpStatus，
        // 不得引用测试输入框（test_input）等用户输入面
        val start = text.indexOf("private fun checkResultLabel(")
        assertTrue("checkResultLabel 定义缺失", start >= 0)
        val body = text.substring(start, text.indexOf("private fun", start + 1).takeIf { it > start } ?: text.length)
        for (banned in listOf("test_input", "input.text", "editText")) {
            assertTrue("日志摘要不得引用用户输入面 $banned", !body.contains(banned))
        }
    }
}
