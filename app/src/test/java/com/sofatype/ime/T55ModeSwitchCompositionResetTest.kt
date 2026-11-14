// T55 · 模式切换键候选态残留守卫单元测试（docs/issues/closed/T55.md · 户主拍板 2026-11-11）：
// 模式切换键必须同时复位组合与候选——[中/英] 切换：取消组合态（原「组合串以字母形式上屏」
// 口径废止），落零候选词态。
// T66 · 决策 51 返工：中文态无[⇧]键位（该键位 = 分词键），原「中文态按[大写]切英文」路径
// 废止删除；中文 → 英文切换唯一路径 = [中/英] 键（决策 43-② 复位口径不变），
// 切英文固定落小写（决策 51-③）。
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

    /** execute 内 ToggleCase 分支体（下一分支 OpenSymbols 为稳定边界；T66 · 决策 51-② 分词键在其前）。 */
    private val toggleCaseBody: String =
        serviceSource.substringAfter("KeyAction.ToggleCase -> {")
            .substringBefore("KeyAction.OpenSymbols")

    /** execute 内 ToggleLanguage 分支体（以其后 switchKeyboardMode 函数为稳定边界）。 */
    private val toggleLanguageBody: String =
        serviceSource.substringAfter("KeyAction.ToggleLanguage -> {")
            .substringBefore("private fun switchKeyboardMode")

    @Test
    fun `大小写键不再承载语言切换`() {
        // T66 · 决策 51-②（决策 49 废止）：[⇧] 键位仅存在于英文模式，纯大小写切换——
        // 分支内不得再有语言切换 / 组合取消（中文 → 英文唯一路径 = [中/英] 键）
        assertFalse(
            "ToggleCase 分支不得再切语言（决策 51-②：决策 49 中文态[⇧]路径废止删除）",
            toggleCaseBody.contains("languageMode"),
        )
        assertFalse(
            "ToggleCase 分支不得再取消组合态（中文态无[⇧]键位，该键位为分词键）",
            toggleCaseBody.contains("cancelComposition"),
        )
        val flipIdx = toggleCaseBody.indexOf("caseState = if (caseState == CaseState.UPPER)")
        val applyIdx = toggleCaseBody.indexOf("applyCaseState()")
        assertTrue(
            "英文态纯大小写翻转须保留（FR-A3）",
            flipIdx >= 0,
        )
        assertTrue("键面/触发态刷新（applyCaseState）须保留", applyIdx > flipIdx)
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
        val rebuildIdx = toggleLanguageBody.indexOf("rebuildKeys(")
        assertTrue("取消组合须先于语言切换赋值", langIdx > cancelIdx)
        assertTrue(
            "语言切换后须重建键区（决策 51：行 1 头键随模式分化；重建内含候选栏刷新，落零候选词态）",
            rebuildIdx > langIdx,
        )
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
    fun `中英切换分支无落定上屏路径`() {
        assertFalse(
            "[中/英] 分支不得出现落定上屏（finishComposition）",
            toggleLanguageBody.contains("finishComposition"),
        )
    }
}
