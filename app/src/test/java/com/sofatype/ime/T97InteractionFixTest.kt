package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T97InteractionFixTest {

    private fun mainSource(path: String): String =
        File("src/main/$path").readText()

    private val service: String get() = mainSource("java/com/sofatype/ime/SofatypeImeService.kt")

    private fun serviceBody(signature: String): String =
        service.substringAfter(signature).substringBefore("\n    }")

    @Test
    fun `退格策略_选区最优先`() {
        assertEquals(BackspacePolicy.Target.SELECTION, BackspacePolicy.resolve(selectionLength = 2, beforeLength = 5, afterLength = 3))
        assertEquals(BackspacePolicy.Target.SELECTION, BackspacePolicy.resolve(selectionLength = 1, beforeLength = 0, afterLength = 0))
    }

    @Test
    fun `退格策略_光标前有文本删前字符`() {
        assertEquals(BackspacePolicy.Target.BEFORE, BackspacePolicy.resolve(selectionLength = 0, beforeLength = 5, afterLength = 0))
        assertEquals(BackspacePolicy.Target.BEFORE, BackspacePolicy.resolve(selectionLength = 0, beforeLength = 5, afterLength = 3))
    }

    @Test
    fun `退格策略_光标前为空且后有余文删后字符`() {
        assertEquals(BackspacePolicy.Target.AFTER, BackspacePolicy.resolve(selectionLength = 0, beforeLength = 0, afterLength = 2))
        assertEquals(BackspacePolicy.Target.AFTER, BackspacePolicy.resolve(selectionLength = 0, beforeLength = 0, afterLength = 1))
    }

    @Test
    fun `退格策略_前后皆空为无操作`() {
        assertEquals(BackspacePolicy.Target.NONE, BackspacePolicy.resolve(selectionLength = 0, beforeLength = 0, afterLength = 0))
    }

    @Test
    fun `退格策略_零长度选区不按选区分流`() {
        assertEquals(BackspacePolicy.Target.BEFORE, BackspacePolicy.resolve(selectionLength = 0, beforeLength = 1, afterLength = 0))
    }

    @Test
    fun `deleteBackward接线策略分发与三种删除原语`() {
        val body = serviceBody("private fun deleteBackward()")
        assertTrue("deleteBackward 须走 BackspacePolicy.resolve 分发", body.contains("BackspacePolicy.resolve("))
        assertTrue("选区分支须清选区（commitText 空串）", body.contains("ic.commitText(\"\", 1)"))
        assertTrue("删前分支须 deleteSurroundingText(1, 0)", body.contains("ic.deleteSurroundingText(1, 0)"))
        assertTrue("搁浅尾段分支须 deleteSurroundingText(0, 1)", body.contains("ic.deleteSurroundingText(0, 1)"))

        val resolveIdx = body.indexOf("BackspacePolicy.resolve(")
        val selectionIdx = body.indexOf("BackspacePolicy.Target.SELECTION ->")
        val beforeIdx = body.indexOf("BackspacePolicy.Target.BEFORE ->")
        val afterIdx = body.indexOf("BackspacePolicy.Target.AFTER ->")
        val noneIdx = body.indexOf("BackspacePolicy.Target.NONE ->")
        assertTrue(
            "策略调用须先于全部分支执行",
            resolveIdx >= 0 && resolveIdx < selectionIdx && resolveIdx < beforeIdx &&
                resolveIdx < afterIdx && resolveIdx < noneIdx,
        )
    }

    @Test
    fun `组合态删除优先删composer后走deleteBackward`() {
        val body = serviceBody("private fun performDelete()")
        val composerIdx = body.indexOf("composer.composition.isNotEmpty()")
        val composerDelIdx = body.indexOf("composer.deleteLastLetter()")
        val fallbackIdx = body.indexOf("deleteBackward()")
        assertTrue(
            "组合态非空须先删 composer（字母段逐字删除口径不变）",
            composerIdx >= 0 && composerDelIdx > composerIdx && fallbackIdx > composerDelIdx,
        )
    }

    @Test
    fun `进符号键盘焦点落返回键`() {
        val body = serviceBody("private fun switchKeyboardMode(mode: KeyboardMode)")
        val symbolsIdx = body.indexOf("KeyboardMode.SYMBOLS -> SymbolsLayout.BACK_ID")
        assertTrue("进符号键盘焦点目标 = [返回] 键（SymbolsLayout.BACK_ID）", symbolsIdx >= 0)
        val rebuildIdx = body.indexOf("rebuildKeys(")
        val postIdx = body.indexOf("keyboardView?.post")
        assertTrue("焦点落点须在键区重建之后", rebuildIdx >= 0 && postIdx > rebuildIdx)
        assertTrue("焦点落点须经 requestFocus", body.contains("keyViews[focusTargetId]?.requestFocus"))
    }

    @Test
    fun `回字母键盘焦点落123键`() {
        val body = serviceBody("private fun switchKeyboardMode(mode: KeyboardMode)")
        assertTrue(
            "回字母键盘焦点目标 = [123_!] 键（KeyboardLayout.SYMBOLS_ENTRY_KEY_ID）",
            body.contains("KeyboardMode.LETTERS -> KeyboardLayout.SYMBOLS_ENTRY_KEY_ID"),
        )
        assertEquals("symbols", KeyboardLayout.SYMBOLS_ENTRY_KEY_ID)
        assertEquals(KeyAction.OpenSymbols, KeyboardLayout.keyById.getValue(KeyboardLayout.SYMBOLS_ENTRY_KEY_ID).action)
    }

    @Test
    fun `布局切换落点键位在各自键盘真实存在`() {
        val lettersNavigator = FocusNavigator(
            KeyboardLayout.rows(LanguageMode.CHINESE),
            initialCell = KeyboardLayout.initialCell,
        )
        val symbolsPosition = lettersNavigator.positionsOf(KeyboardLayout.SYMBOLS_ENTRY_KEY_ID)
        assertTrue("[123_!] 键须在字母键盘格网内", symbolsPosition.isNotEmpty())

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            for (offset in 0..SymbolsLayout.MAX_WINDOW_OFFSET) {
                val navigator = FocusNavigator(
                    SymbolsLayout.rows(width, offset),
                    SymbolsLayout.overlayCells(),
                    SymbolsLayout.initialCell,
                )
                assertTrue(
                    "[返回] 键须在符号键盘全部窗口格网内（$width offset=$offset）",
                    navigator.positionsOf(SymbolsLayout.BACK_ID).isNotEmpty(),
                )
            }
        }
    }

    @Test
    fun `布局切换保留组合态落定不丢内容`() {
        val body = serviceBody("private fun switchKeyboardMode(mode: KeyboardMode)")
        val finishIdx = body.indexOf("finishComposition()")
        val modeIdx = body.indexOf("keyboardMode = mode")
        assertTrue(
            "切换前须 finishComposition（组合串原样上屏，既有内容不丢）",
            finishIdx >= 0 && finishIdx < modeIdx,
        )
        assertFalseSafe(body.contains("cancelComposition"), "布局切换不得取消组合态（取消=丢内容）")
    }

    private fun assertFalseSafe(condition: Boolean, message: String) {
        assertTrue(message, !condition)
    }

    @Test
    fun `行4光标键键面行为朗读三者一致`() {
        val byId = KeyboardLayout.keyById
        val left = byId.getValue("cursor_left")
        val right = byId.getValue("cursor_right")

        assertEquals(KeyIcon.CURSOR_LEFT, left.icon)
        assertEquals(KeyAction.CursorLeft, left.action)
        assertEquals("光标左移", left.contentDescription)
        assertEquals(KeyIcon.CURSOR_RIGHT, right.icon)
        assertEquals(KeyAction.CursorRight, right.action)
        assertEquals("光标右移", right.contentDescription)

        val executeBody = serviceBody("private fun execute(key: Key)")
        val leftIdx = executeBody.indexOf("KeyAction.CursorLeft -> moveCursor(-1)")
        val rightIdx = executeBody.indexOf("KeyAction.CursorRight -> moveCursor(1)")
        assertTrue("执行层光标左移行为 = moveCursor(-1)", leftIdx >= 0)
        assertTrue("执行层光标右移行为 = moveCursor(1)", rightIdx >= 0)
    }

    @Test
    fun `行4键面图标为箭头非删除形状`() {
        val leftIcon = mainSource("res/drawable/ic_key_cursor_left.xml")
        val rightIcon = mainSource("res/drawable/ic_key_cursor_right.xml")
        val backspaceIcon = mainSource("res/drawable/ic_key_backspace.xml")
        assertTrue("← 键面为横向箭头笔画", leftIcon.contains("M19,12 H5") && leftIcon.contains("M11,6 L5,12 L11,18"))
        assertTrue("→ 键面为横向箭头笔画", rightIcon.contains("M5,12 H19") && rightIcon.contains("M13,6 L19,12 L13,18"))
        assertTrue("删除键面保持 ⌫ 造型（与光标键区分）", backspaceIcon.contains("M8.5,5 H19.5"))
    }

    @Test
    fun `既有删除键行为不回归`() {
        val byId = KeyboardLayout.keyById
        assertEquals(KeyAction.Delete, byId.getValue("backspace").action)
        val symbolsById = SymbolsLayout.allKeys(SymbolsWidth.FULL_WIDTH).associateBy { it.id }
        assertEquals(KeyAction.Delete, symbolsById.getValue("delete").action)
        assertEquals("删除", byId.getValue("backspace").contentDescription)
        assertEquals("删除", symbolsById.getValue("delete").contentDescription)

        val delIdx = service.indexOf("if (keyCode == KeyEvent.KEYCODE_DEL)")
        val delBody = service.substring(delIdx, service.indexOf("return super.onKeyDown", delIdx))
        assertTrue("KEYCODE_DEL 显示态组合态删除分支保留", delBody.contains("performDelete()"))
    }

    @Test
    fun `符号键盘返回键与字母键盘123键标注锁定`() {
        val symbolsById = SymbolsLayout.allKeys(SymbolsWidth.FULL_WIDTH).associateBy { it.id }
        assertEquals("返回字母键盘", symbolsById.getValue(SymbolsLayout.BACK_ID).contentDescription)
        assertEquals("进入符号键盘", KeyboardLayout.keyById.getValue(KeyboardLayout.SYMBOLS_ENTRY_KEY_ID).contentDescription)
        assertNull(KeyboardLayout.outputValue(KeyboardLayout.keyById.getValue(KeyboardLayout.SYMBOLS_ENTRY_KEY_ID), CaseState.LOWER))
    }
}
