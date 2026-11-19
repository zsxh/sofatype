package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T55ModeSwitchCompositionResetTest {

    private val serviceSource: String =
        File("src/main/java/com/sofatype/ime/SofatypeImeService.kt").readText()

    private fun serviceBody(signature: String): String =
        serviceSource.substringAfter(signature).substringBefore("\n    }")

    private val toggleCaseBody: String =
        serviceSource.substringAfter("KeyAction.ToggleCase -> {")
            .substringBefore("KeyAction.OpenSymbols")

    private val toggleLanguageBody: String =
        serviceSource.substringAfter("KeyAction.ToggleLanguage -> {")
            .substringBefore("private fun switchKeyboardMode")

    @Test
    fun `大小写键不再承载语言切换`() {

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
