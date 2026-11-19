package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CandidatesBarOccupancyTest {

    private val serviceSource: String =
        File("src/main/java/com/sofatype/ime/SofatypeImeService.kt").readText()

    private fun serviceBody(signature: String): String =
        serviceSource.substringAfter(signature).substringBefore("\n    }")

    @Test
    fun `候选栏占位无条件保留不随语言或键盘模式隐藏`() {
        val body = serviceBody("private fun refreshCandidatesBar()")

        assertTrue(
            "候选栏占位须无条件可见（refreshCandidatesBar 须置 VISIBLE）",
            body.contains("bar.visibility = View.VISIBLE"),
        )
        assertFalse(
            "候选栏不得再整栏隐藏（决策 42：中/英切换不再 GONE，FR-A7 隐藏分支为废止口径）",
            body.contains("View.GONE"),
        )
        assertFalse(
            "占位判定不得随语言/键盘模式切换（决策 42：占位无条件保留，T52 的 languageMode 判定口径同步废止）",
            body.contains("languageMode"),
        )
    }

    @Test
    fun `候选装载仅字母键盘组合态触发`() {
        val body = serviceBody("private fun refreshCandidatesBar()")

        assertTrue(
            "候选装载判定须显式限定字母键盘（keyboardMode == LETTERS && composer.hasCandidates）",
            body.contains("keyboardMode == KeyboardMode.LETTERS && composer.hasCandidates"),
        )
    }

    @Test
    fun `键区上边距恒定不随候选栏显隐切换`() {

        assertFalse(
            "键区上边距不得运行期切换（决策 42：setKeyAreaTopMargin 死分支已删，重引即破坏键区顶恒定）",
            serviceSource.contains("setKeyAreaTopMargin"),
        )
    }

    @Test
    fun `切换键盘先终结组合态保证符号键盘无候选内容`() {
        val body = serviceBody("private fun switchKeyboardMode(mode: KeyboardMode)")

        assertTrue(
            "切换键盘须先终结组合态（finishComposition）",
            body.contains("finishComposition()"),
        )
        assertTrue(
            "组合态终结须先于 keyboardMode 赋值",
            body.indexOf("finishComposition()") < body.indexOf("keyboardMode = mode"),
        )
    }

    @Test
    fun `远程上屏路径不重算候选栏显隐`() {
        val body = serviceBody("private fun commitRemoteText(text: String)")

        assertFalse(
            "远程上屏路径不得重算候选栏（T17：显隐归 renderRemoteUi 驱动）",
            body.contains("refreshCandidatesBar()"),
        )
    }

    @Test
    fun `远程模式候选栏与键区仍由renderRemoteUi驱动隐藏`() {

        val modeCheck = serviceSource.indexOf("state.mode == RemoteMode.REMOTE")
        val areaGone = serviceSource.indexOf("keyArea?.visibility = View.GONE")
        val barGone = serviceSource.indexOf("candidatesBar?.visibility = View.GONE")
        assertTrue("renderRemoteUi 须存在 REMOTE 模式分支判断", modeCheck >= 0)
        assertTrue(
            "远程模式须驱动键区 GONE（T17）",
            areaGone > modeCheck,
        )
        assertTrue(
            "远程模式须驱动候选栏 GONE（T17：候选栏保持隐藏）",
            barGone > modeCheck,
        )
    }
}
