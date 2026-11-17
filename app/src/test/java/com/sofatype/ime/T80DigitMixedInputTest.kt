// T80 · 中文组合态 × 数字混输与删除失同步修复单元测试（JUnit 4，纯 Kotlin，不依赖 Android UI）。
// 户主拍板语义（2026-11-16）：组合态按数字 = 组合串先原样上屏（finishComposition 既有语义），
// 数字紧随其后直出（组合 "ni" 按 5 → 正文 "ni5"）。
// ① 模型层：硬件数字键码 → 数字键 id 映射全表（KeyboardLayout.digitIdForKeyCode，T45 同族口径）；
//   组合态数字直出前序语义的模型侧锁定（committedText 原样上屏 + reset 复位，服务层接线由
//   ② 源码扫描锁定——SofatypeImeService 为 Android 服务层，纯 JVM 单测无法实例化（无 Robolectric，
//   仓库口径），先例：T59HiddenKeySemanticsTest / SettingsFocusTakeoverTest）。
// ② 源码扫描守卫：execute() 直出分支终结组合态（终结后候选栏同步清空）+ onKeyDown 数字键码
//   拦截（守卫与 T45 字母拦截同构）+ onKeyUp 数字键码 UP 显示态消费（T59 对称口径）+
//   T48 DEL 分支 / 空格键码既有口径零回归。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T80DigitMixedInputTest {

    // ── ① 模型层：数字键码映射（T45 letterIdForKeyCode 同族口径） ──

    @Test
    fun `数字键码全表映射digit_0到digit_9`() {
        // KEYCODE_0=7 .. KEYCODE_9=16（android.view.KeyEvent 自 API 1 起稳定常量）
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
        assertNull(KeyboardLayout.digitIdForKeyCode(29))  // KEYCODE_A（字母归 T45 映射）
        assertNull(KeyboardLayout.digitIdForKeyCode(62))  // KEYCODE_SPACE（R-2 另行评估，本轮不动）
        assertNull(KeyboardLayout.digitIdForKeyCode(66))  // KEYCODE_ENTER（确认键集合）
        assertNull(KeyboardLayout.digitIdForKeyCode(67))  // KEYCODE_DEL（T48 拦截口径不变）
    }

    @Test
    fun `数字键码映射与字母映射互不重叠`() {
        // 两映射函数互斥：任一键码至多命中其一（服务层拦截判定 letter 在前、digit 在后无歧义）
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

    // ── ① 模型层：组合态数字直出的 composer 前序语义 ──

    @Test
    fun `组合态数字直出前序_组合串原样上屏并复位`() {
        // 拍板语义模型侧锁定：组合 "ni" 直出数字前，committedText() = "ni"（原样上屏内容），
        // reset() 后组合态清空——服务层 finishComposition 即这两个原语成对（既有实现零改动）。
        // 复位后重按字母产生全新单字母组合：失同步连锁「旧拼音复活」的模型侧反证。
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

    // ── ② 源码扫描守卫：服务层接线锁定 ──

    /** 路径口径 = app/ 模块目录（T59HiddenKeySemanticsTest 同口径）。 */
    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    /** 行注释剥离（// … 至行尾），扫描锚点只落真实代码——注释提及标识符不干扰序位断言。 */
    private fun stripLineComments(source: String): String =
        source.lines().joinToString("\n") { line ->
            val idx = line.indexOf("//")
            if (idx >= 0) line.substring(0, idx) else line
        }

    private val service: String get() = stripLineComments(mainSource("SofatypeImeService.kt"))

    @Test
    fun `execute直出分支中文组合态先终结再直出且候选栏同步清空`() {
        // Text 动作 else 分支（非符号键盘 × 非字母入组键 = 数字列 / .com / @ 直出）内序位：
        // finishComposition（组合串原样上屏 + composer 复位）→ refreshCandidatesBar（候选栏清空）
        // → commitText（数字紧随其后直出，框架 commitText 语义为替换组合区，先终结才不吞拼音）
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
        // 终结判定门控：仅中文模式 × 组合非空（英文模式 / 空组合行为不变，空组合终结为无操作）
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
        // 同一守卫块（同一 if 体内）：ACTION_DOWN 首按 + 字母键盘 + 中文模式 + 非远程 + 显示态
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
        // 拦截后映射对应数字键走 execute（与网格 OK 同路）并消费
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
        // 与字母 UP 同一守卫表达式（T45 同款条件 + isInputViewShown 门控）：
        // 显示态一律消费（防孤儿 UP 双写），隐藏态整体不命中、透传宿主（T59 对称口径）
        val guardStart = upBody.indexOf("if (event != null &&", 0)
        assertTrue("UP 消费守卫存在", guardStart >= 0 && guardStart < condIdx)
        val cond = upBody.substring(guardStart, condIdx)
        assertTrue("UP 消费须含字母键码同款条件（T45 同款守卫）", cond.contains("KeyboardLayout.letterIdForKeyCode(keyCode) != null"))
        assertTrue("UP 消费须含显示态门控（隐藏态透传）", upBody.substring(condIdx, upBody.indexOf("return true", condIdx)).contains("isInputViewShown()"))
    }

    @Test
    fun `T48删除分支与空格键码口径零回归`() {
        // T48 DEL 分支原样保留（数字拦截不得吞并删除键码；分支含显示态门控、隐藏态透传宿主）
        val delIdx = service.indexOf("if (keyCode == KeyEvent.KEYCODE_DEL)")
        assertTrue("T48 DEL 分支保留", delIdx >= 0)
        val delBody = service.substring(delIdx, service.indexOf("return super.onKeyDown", delIdx))
        assertTrue("T48 DEL 分支守卫保留（LETTERS + CHINESE + 非远程 + 显示态）", delBody.contains("isInputViewShown()"))
        // 数字拦截分支不触碰空格键码（R-2 空格键面语义另行评估，模型侧映射 null 已锁）
        val digitBranch = service.substringAfter("KeyboardLayout.digitIdForKeyCode(keyCode)")
            .substringBefore("return super.onKeyDown(keyCode, ev)")
        assertFalse("数字拦截分支不得引用 KEYCODE_SPACE", digitBranch.contains("KEYCODE_SPACE"))
    }
}
