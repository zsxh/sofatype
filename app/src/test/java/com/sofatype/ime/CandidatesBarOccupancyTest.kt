// T53 · 候选栏空态占位全模式恒定守卫单元测试（docs/issues/open/T53.md · PRD 决策 42）：
// 候选栏空态几何占位无条件保留——中/英模式 × 字母/符号键盘全组合键区顶恒定，中/英切换不再
// 整栏隐藏、键区不上移（FR-A7「英文/数字模式下候选栏隐藏、键盘区上移」条款废止）；
// 候选装载仅由字母键盘 × 中文组合态触发（英文/符号键盘恒空态占位，无 chips、无「更多⌄」）；
// 远程模式候选栏显隐由 renderRemoteUi 驱动（T17，候选栏保持隐藏）不回归。
// T52（决策 41）守卫口径随决策 42 同步：占位判定不得含语言/键盘模式条件。
// SofatypeImeService 为 Android 交互层，纯 JVM 单测无法实例化（无 Robolectric，仓库口径），
// 接线以源码扫描锁定回归——源码扫描先例：CursorComposerSyncTest（T49）、DirectInstallFlowTest（T51）、
// AccessibilityLabelsTest（T26）。行为级取证走模拟器/真机走查。
package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CandidatesBarOccupancyTest {

    /** 全文口径 = app/ 模块目录（CursorComposerSyncTest 同口径）。 */
    private val serviceSource: String =
        File("src/main/java/com/sofatype/ime/SofatypeImeService.kt").readText()

    /** 取函数体：从签名行起、到下一个成员级 KDoc 边界行止（边界串见实现，CursorComposerSyncTest 同口径）。 */
    private fun serviceBody(signature: String): String =
        serviceSource.substringAfter(signature).substringBefore("\n    /**")

    @Test
    fun `候选栏占位无条件保留不随语言或键盘模式隐藏`() {
        val body = serviceBody("private fun refreshCandidatesBar()")
        // 决策 42：占位全模式恒定——函数内不得再有任何整栏隐藏分支
        //（原「LETTERS × CHINESE」「CHINESE」显隐判定口径均废止，FR-A7 英文条款废止后无隐藏路径）
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
        // 决策 42 内容口径：英文/符号键盘候选栏恒空态占位（无 chips、无「更多⌄」）——
        // 装载判定显式限定字母键盘 × 组合态有候选
        assertTrue(
            "候选装载判定须显式限定字母键盘（keyboardMode == LETTERS && composer.hasCandidates）",
            body.contains("keyboardMode == KeyboardMode.LETTERS && composer.hasCandidates"),
        )
    }

    @Test
    fun `键区上边距恒定不随候选栏显隐切换`() {
        // 决策 42：键区顶全模式恒定——键区上边距回归布局静态值 ime_key_area_margin_top，
        // 运行期切换边距的 setKeyAreaTopMargin 已随 FR-A7 废止删除，不得回归重引
        assertFalse(
            "键区上边距不得运行期切换（决策 42：setKeyAreaTopMargin 死分支已删，重引即破坏键区顶恒定）",
            serviceSource.contains("setKeyAreaTopMargin"),
        )
    }

    @Test
    fun `切换键盘先终结组合态保证符号键盘无候选内容`() {
        val body = serviceBody("private fun switchKeyboardMode(mode: KeyboardMode)")
        // 结构前提（T52 守卫保留）：切键盘先 finishComposition（组合串落定 + composer 复位）再重建键区；
        // 符号键盘直出符号不进组合态——「符号键盘恒空态占位」依赖此序
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
        // T17 边界：远程模式候选栏显隐由 renderRemoteUi 驱动（候选栏保持隐藏、防空态占位顶低
        // 状态行）——commitRemoteText 只复位组合态，不得重算候选栏（决策 42 占位恒定后，
        // 远程路径重算会把空态占位 VISIBLE 顶进远程框架）
        assertFalse(
            "远程上屏路径不得重算候选栏（T17：显隐归 renderRemoteUi 驱动）",
            body.contains("refreshCandidatesBar()"),
        )
    }

    @Test
    fun `远程模式候选栏与键区仍由renderRemoteUi驱动隐藏`() {
        // T17 行为不回归：renderRemoteUi 的 REMOTE 分支须直接驱动候选栏与键区 GONE
        //（顺序约束：驱动行位于 REMOTE 模式判断之后）
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
