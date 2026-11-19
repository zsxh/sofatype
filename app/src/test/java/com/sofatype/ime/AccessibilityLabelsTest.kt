package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

class AccessibilityLabelsTest {

    @Test
    fun `字母键盘全部键位有无障碍标注`() {
        val keys = KeyboardLayout.allKeys()
        assertTrue("字母键盘键位不应为空", keys.isNotEmpty())
        for (key in keys) {
            assertTrue("字母键盘键位 ${key.id} 缺少无障碍标注", key.contentDescription.isNotBlank())
        }
    }

    @Test
    fun `符号键盘全角半角两态全部键位有无障碍标注`() {

        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val keys = (0..SymbolsLayout.MAX_WINDOW_OFFSET).flatMap { offset -> SymbolsLayout.allKeys(width, offset) }
            assertTrue("符号键盘键位不应为空（$width）", keys.isNotEmpty())
            for (key in keys) {
                assertTrue("符号键盘键位 ${key.id}（$width）缺少无障碍标注", key.contentDescription.isNotBlank())
            }
        }
    }

    @Test
    fun `设计稿已标注键位文案锁定`() {
        val byId = KeyboardLayout.keyById

        assertEquals("上档切换", byId.getValue("shift").contentDescription)
        assertEquals("下屏，收起键盘", byId.getValue("hide").contentDescription)
        assertEquals("删除", byId.getValue("backspace").contentDescription)
        assertEquals("光标左移", byId.getValue("cursor_left").contentDescription)
        assertEquals("光标右移", byId.getValue("cursor_right").contentDescription)

        assertEquals(
            "分词：组合态内切分拼音（如 xi'an），选词后不上屏；无组合态时忽略",
            byId.getValue("segment").contentDescription,
        )

        assertEquals("上档切换，当前大写", KeyboardLayout.shiftContentDescription(CaseState.UPPER))
        assertEquals("上档切换，当前小写", KeyboardLayout.shiftContentDescription(CaseState.LOWER))
        assertEquals("中英文切换，当前为中文", KeyboardLayout.langContentDescription(chinese = true))
        assertEquals("中英文切换，当前为英文", KeyboardLayout.langContentDescription(chinese = false))
        assertEquals("全角半角切换，当前为半角", SymbolsLayout.widthContentDescription(halfWidth = true))
        assertEquals("全角半角切换，当前为全角", SymbolsLayout.widthContentDescription(halfWidth = false))

        assertEquals("中英文切换", byId.getValue("lang").contentDescription)
        assertEquals("进入符号键盘", byId.getValue("symbols").contentDescription)

        val symbolsById = SymbolsLayout.allKeys(SymbolsWidth.FULL_WIDTH).associateBy { it.id }
        assertEquals("全角半角切换", symbolsById.getValue(SymbolsLayout.TOGGLE_WIDTH_ID).contentDescription)
        assertEquals("返回字母键盘", symbolsById.getValue(SymbolsLayout.BACK_ID).contentDescription)
        assertEquals("删除", symbolsById.getValue("delete").contentDescription)
        assertEquals("下屏，收起键盘", symbolsById.getValue("hide").contentDescription)
    }

    @Test
    fun `跨屏按钮标注文案锁定`() {

        assertEquals("进入远程输入模式", RemoteUiText.GUIDE_BUTTON_DESCRIPTION)
        assertEquals("退出远程模式", RemoteUiText.EXIT_BUTTON_DESCRIPTION)

    }

    @Test
    fun `手机H5页交互元素aria-label齐全`() {
        val html = File("src/main/assets/remote/index.html").readText()
        val expectedLabels = listOf(
            "重连",
            "远程输入内容",
            "删除一个字符",
            "清空全部内容",
            "上屏",
        )
        for (label in expectedLabels) {
            assertTrue("H5 页缺少 aria-label「$label」", html.contains("aria-label=\"$label\""))
        }
    }
}
