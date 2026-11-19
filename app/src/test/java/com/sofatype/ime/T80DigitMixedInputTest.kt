package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T80DigitMixedInputTest {

    @Test
    fun `数字键码全表映射digit_0到digit_9`() {

        assertEquals("digit_0", KeyboardLayout.digitIdForKeyCode(7))
        assertEquals("digit_1", KeyboardLayout.digitIdForKeyCode(8))
        assertEquals("digit_2", KeyboardLayout.digitIdForKeyCode(9))
        assertEquals("digit_3", KeyboardLayout.digitIdForKeyCode(10))
        assertEquals("digit_4", KeyboardLayout.digitIdForKeyCode(11))
        assertEquals("digit_5", KeyboardLayout.digitIdForKeyCode(12))
        assertEquals("digit_6", KeyboardLayout.digitIdForKeyCode(13))
        assertEquals("digit_7", KeyboardLayout.digitIdForKeyCode(14))
        assertEquals("digit_8", KeyboardLayout.digitIdForKeyCode(15))
        assertEquals("digit_9", KeyboardLayout.digitIdForKeyCode(16))
    }

    @Test
    fun `数字键码边界外与常见非数字键码返回null`() {
        assertNull(KeyboardLayout.digitIdForKeyCode(6))
        assertNull(KeyboardLayout.digitIdForKeyCode(17))
        assertNull(KeyboardLayout.digitIdForKeyCode(29))
        assertNull(KeyboardLayout.digitIdForKeyCode(62))
        assertNull(KeyboardLayout.digitIdForKeyCode(66))
        assertNull(KeyboardLayout.digitIdForKeyCode(67))
    }

    @Test
    fun `数字键码映射与字母映射互不重叠`() {

        for (keyCode in 0..127) {
            val letter = KeyboardLayout.letterIdForKeyCode(keyCode)
            val digit = KeyboardLayout.digitIdForKeyCode(keyCode)
            assertTrue("keyCode=$keyCode 出现双重映射", letter == null || digit == null)
        }
    }

    @Test
    fun `数字键码映射id全命中数字键模型且输出值与键码一致`() {
        for (keyCode in 7..16) {
            val id = KeyboardLayout.digitIdForKeyCode(keyCode)!!
            val key = KeyboardLayout.keyById[id]
            assertTrue("id=$id 应在键位模型中", key != null)
            assertEquals(
                "id=$id 直出值应等于键码数字（小写态）",
                (keyCode - 7).toString(),
                KeyboardLayout.outputValue(key!!, CaseState.LOWER),
            )
            assertEquals(
                "id=$id 输出不受大小写影响（FR-A9）",
                (keyCode - 7).toString(),
                KeyboardLayout.outputValue(key, CaseState.UPPER),
            )
        }
    }

    @Test
    fun `组合态数字直出前序_组合串原样上屏并复位`() {

        val composer = ChineseComposer { input -> if (input == "ni") listOf("你") else emptyList() }
        composer.appendLetter('n')
        composer.appendLetter('i')
        assertEquals("ni", composer.composition)
        assertEquals("ni", composer.committedText())
        composer.reset()
        assertEquals("", composer.composition)
        assertFalse(composer.hasCandidates)
        composer.appendLetter('x')
        assertEquals("x", composer.composition)
    }

    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    private fun stripLineComments(source: String): String =
        source.lines().joinToString("\n") { line ->
            val idx = line.indexOf("//")
            if (idx >= 0) line.substring(0, idx) else line
        }

    private val service: String get() = stripLineComments(mainSource("SofatypeImeService.kt"))

    @Test
    fun `execute直出分支中文组合态先终结再直出且候选栏同步清空`() {

        val commitIdx = service.indexOf("commitText(KeyboardLayout.outputValue(key, caseState))")
        assertTrue("直出分支 commitText 存在", commitIdx >= 0)
        val branchStart = service.lastIndexOf("} else {", commitIdx)
        val finishIdx = service.indexOf("finishComposition()", branchStart)
        val refreshIdx = service.indexOf("refreshCandidatesBar()", finishIdx)
        val commitInBranch = service.indexOf("commitText(", refreshIdx)
        assertTrue(
            "直出分支须先 finishComposition 再 refreshCandidatesBar 再 commitText（终结后候选栏同步清空）",
            finishIdx > branchStart && refreshIdx > finishIdx && commitInBranch > refreshIdx && commitInBranch <= commitIdx,
        )

        val guard = service.substring(branchStart, finishIdx)
        assertTrue("终结判定须含中文模式门控", guard.contains("LanguageMode.CHINESE"))
        assertTrue(
            "终结判定须含组合非空判定（空组合终结为无操作）",
            guard.contains("composer.composition.isNotEmpty()"),
        )
    }

    @Test
    fun `onKeyDown数字键码拦截守卫与T45字母拦截同构`() {
        val digitIdx = service.indexOf("KeyboardLayout.digitIdForKeyCode(keyCode)?.let")
        assertTrue("T80 数字键码 DOWN 拦截分支存在", digitIdx >= 0)
        val letterIdx = service.indexOf("KeyboardLayout.letterIdForKeyCode(keyCode)?.let")
        assertTrue("T45 字母拦截分支存在且在前", letterIdx >= 0 && letterIdx < digitIdx)

        val blockStart = service.lastIndexOf(
            "if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0",
            letterIdx,
        )
        val block = service.substring(blockStart, digitIdx)
        assertTrue("守卫块须含字母键盘门控", block.contains("keyboardMode == KeyboardMode.LETTERS"))
        assertTrue("守卫块须含中文模式门控", block.contains("languageMode == LanguageMode.CHINESE"))
        assertTrue("守卫块须含远程模式排除", block.contains("!RemoteInputServerManager.isRunning()"))
        assertTrue("守卫块须含显示态门控", block.contains("isInputViewShown()"))
        assertTrue("守卫块须含 repeatCount==0（长按重复不消费）", block.contains("ev.repeatCount == 0"))

        val digitKeyIdx = service.indexOf("val digitKey = ", letterIdx)
        val execIdx = service.indexOf("execute(digitKey)", digitKeyIdx)
        val returnTrueIdx = service.indexOf("return true", execIdx)
        assertTrue("数字键码拦截须 execute(digitKey) 且消费（return true）", execIdx > digitIdx && returnTrueIdx > execIdx)
    }

    @Test
    fun `onKeyUp数字键码UP显示态一律消费隐藏态透传`() {
        val keyUpIdx = service.indexOf("override fun onKeyUp")
        assertTrue("onKeyUp 存在", keyUpIdx >= 0)
        val upBody = service.substring(keyUpIdx)
        val condIdx = upBody.indexOf("KeyboardLayout.digitIdForKeyCode(keyCode) != null")
        assertTrue("数字键码 UP 消费条件存在", condIdx >= 0)

        val guardStart = upBody.indexOf("if (event != null &&", 0)
        assertTrue("UP 消费守卫存在", guardStart >= 0 && guardStart < condIdx)
        val cond = upBody.substring(guardStart, condIdx)
        assertTrue("UP 消费须含字母键码同款条件（T45 同款守卫）", cond.contains("KeyboardLayout.letterIdForKeyCode(keyCode) != null"))
        assertTrue("UP 消费须含显示态门控（隐藏态透传）", upBody.substring(condIdx, upBody.indexOf("return true", condIdx)).contains("isInputViewShown()"))
    }

    @Test
    fun `T48删除分支与空格键码口径零回归`() {

        val delIdx = service.indexOf("if (keyCode == KeyEvent.KEYCODE_DEL)")
        assertTrue("T48 DEL 分支保留", delIdx >= 0)
        val delBody = service.substring(delIdx, service.indexOf("return super.onKeyDown", delIdx))
        assertTrue("T48 DEL 分支守卫保留（LETTERS + CHINESE + 非远程 + 显示态）", delBody.contains("isInputViewShown()"))

        val digitBranch = service.substringAfter("KeyboardLayout.digitIdForKeyCode(keyCode)")
            .substringBefore("return super.onKeyDown(keyCode, ev)")
        assertFalse("数字拦截分支不得引用 KEYCODE_SPACE", digitBranch.contains("KEYCODE_SPACE"))
    }
}
