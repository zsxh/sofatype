package com.sofatype.ime

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ManualCheckLogTest {

    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    @Test
    fun `手动检查发起日志接线存在`() {
        val text = mainSource("SettingsActivity.kt")
        assertTrue(
            "startCheck 须以 UPDATE_LOG_TAG 写发起日志并携带 endpoint（T50 R2 取证口径；T71 起 Timber 形态）",
            text.contains("Timber.tag(UPDATE_LOG_TAG).i(\"手动检查发起 endpoint=\$endpoint\")"),
        )
    }

    @Test
    fun `检查落态日志覆盖三态标识与失败原因`() {
        val text = mainSource("SettingsActivity.kt")
        assertTrue(
            "onCheckResult 须以 UPDATE_LOG_TAG 写落态日志（T50 R2 取证口径；T71 起 Timber 形态）",
            text.contains("Timber.tag(UPDATE_LOG_TAG).i(\"手动检查结果 \${checkResultLabel(result)}\")"),
        )
        for (marker in listOf("result=UP_TO_DATE", "result=NEWER tag=", "result=FAILED reason=")) {
            assertTrue("落态日志须含三态标识 $marker（T50 R2）", text.contains(marker))
        }
        assertTrue("失败落态须携带 httpStatus（T50 R2）", text.contains("http=\${result.httpStatus ?: \"-\"}"))
    }

    @Test
    fun `日志摘要不含用户输入内容`() {
        val text = mainSource("SettingsActivity.kt")

        val start = text.indexOf("private fun checkResultLabel(")
        assertTrue("checkResultLabel 定义缺失", start >= 0)
        val body = text.substring(start, text.indexOf("private fun", start + 1).takeIf { it > start } ?: text.length)
        for (banned in listOf("test_input", "input.text", "editText")) {
            assertTrue("日志摘要不得引用用户输入面 $banned", !body.contains(banned))
        }
    }
}
