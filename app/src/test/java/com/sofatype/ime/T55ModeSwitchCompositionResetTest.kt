// T55 · 模式切换键候选态残留守卫单元测试（docs/issues/open/T55.md · 户主拍板 2026-11-11）：
// 模式切换键必须同时复位组合与候选——① 中文态按[大写] = 切英文态：组合串与候选取消、
// 不落输入框；② [中/英] 切换：取消组合态（原「组合串以字母形式上屏」口径废止），落零候选词态。
// SofatypeImeService 为 Android 服务层，纯 JVM 单测无法实例化（无 Robolectric，仓库口径），
// 接线以源码扫描锁定回归——先例：T59HiddenKeySemanticsTest（T59）、CandidatesBarOccupancyTest（T53）、
// CursorComposerSyncTest（T49）。行为级取证走模拟器走查。
package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T55ModeSwitchCompositionResetTest {

    /** 全文口径 = app/ 模块目录（CandidatesBarOccupancyTest 同口径）。 */
    private val serviceSource: String =
        File("src/main/java/com/sofatype/ime/SofatypeImeService.kt").readText()

    /** 取函数体：从签名行起、到下一个成员级 KDoc 边界行止（CandidatesBarOccupancyTest 同口径）。 */
    private fun serviceBody(signature: String): String =
        serviceSource.substringAfter(signature).substringBefore("\n    /**")

    /** execute 内 ToggleCase 分支体（下一分支 OpenSymbols 为稳定边界）。 */
    private val toggleCaseBody: String =
        serviceSource.substringAfter("KeyAction.ToggleCase -> {")
            .substringBefore("KeyAction.OpenSymbols")

    /** execute 内 ToggleLanguage 分支体（when 末分支，以其后 switchKeyboardMode 的 KDoc 为稳定边界）。 */
    private val toggleLanguageBody: String =
        serviceSource.substringAfter("KeyAction.ToggleLanguage -> {")
            .substringBefore("字母键盘 ↔ 符号键盘切换（FR-A5）")

    @Test
    fun `中文态大写键取消组合并切英文态`() {
        val chineseBranch = toggleCaseBody.substringAfter("if (languageMode == LanguageMode.CHINESE)")
        assertTrue(
            "中文态按[大写]须取消组合态（cancelComposition，不落输入框）",
            chineseBranch.contains("cancelComposition()"),
        )
        val langIdx = chineseBranch.indexOf("languageMode = LanguageMode.ENGLISH")
        val cancelIdx = chineseBranch.indexOf("cancelComposition()")
        val refreshIdx = chineseBranch.indexOf("refreshCandidatesBar()")
        assertTrue(
            "中文态按[大写]须切换为英文输入态（languageMode = ENGLISH）",
            langIdx > cancelIdx,
        )
        assertTrue(
            "取消组合后须刷新候选栏清空 chips（refreshCandidatesBar）",
            refreshIdx > langIdx,
        )
    }

    @Test
    fun `英文态大写键维持纯大小写切换`() {
        // FR-A3 不回归：大小写翻转与键面刷新在中文分支之外（英文态路径不得出现 cancelComposition）
        val cancelIdx = toggleCaseBody.indexOf("cancelComposition()")
        val flipIdx = toggleCaseBody.indexOf("caseState = if (caseState == CaseState.UPPER)")
        val applyIdx = toggleCaseBody.indexOf("applyCaseState()")
        assertTrue("中文分支（cancelComposition）须存在", cancelIdx >= 0)
        assertTrue(
            "大小写翻转须在中文分支之外（英文态纯 FR-A3 切换）",
            flipIdx > cancelIdx,
        )
        assertTrue("键面刷新（applyCaseState）须保留", applyIdx > flipIdx)
    }

    @Test
    fun `中英键取消组合不上屏且先复位再切换`() {
        assertTrue(
            "[中/英] 切换前须取消组合态（cancelComposition）",
            toggleLanguageBody.contains("cancelComposition()"),
        )
        assertFalse(
            "[中/英] 不得落定组合串上屏（原 finishComposition 口径废止：落定字母即切换残留）",
            toggleLanguageBody.contains("finishComposition"),
        )
        val cancelIdx = toggleLanguageBody.indexOf("cancelComposition()")
        val langIdx = toggleLanguageBody.indexOf("languageMode =")
        val refreshIdx = toggleLanguageBody.indexOf("refreshCandidatesBar()")
        assertTrue("取消组合须先于语言切换赋值", langIdx > cancelIdx)
        assertTrue("语言切换后须刷新候选栏（零候选词态）", refreshIdx > langIdx)
    }

    @Test
    fun `cancelComposition先清空区间再终结且复位成对`() {
        val body = serviceBody("private fun cancelComposition()")
        val setEmptyIdx = body.indexOf("setComposingText(\"\", 1)")
        val finishIdx = body.indexOf("finishComposingText()")
        val resetIdx = body.indexOf("composer.reset()")
        assertTrue(
            "取消组合须先 setComposingText(\"\") 清空框架组合区间（直接 finishComposingText 会把组合串落定上屏）",
            setEmptyIdx >= 0,
        )
        assertTrue(
            "清空后须 finishComposingText 终结空区间（不留悬空组合态）",
            finishIdx > setEmptyIdx,
        )
        assertTrue(
            "取消组合须复位状态机（composer.reset()）",
            resetIdx > finishIdx,
        )
    }

    @Test
    fun `两条模式切换分支均无落定上屏路径`() {
        assertFalse(
            "[大写] 分支不得出现落定上屏（finishComposition）",
            toggleCaseBody.contains("finishComposition"),
        )
        assertFalse(
            "[中/英] 分支不得出现落定上屏（finishComposition）",
            toggleLanguageBody.contains("finishComposition"),
        )
    }
}
