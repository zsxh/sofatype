// T3 · 占位键盘纯逻辑单元测试（JUnit 4，不依赖 Android UI 与模拟器）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class PlaceholderKeyboardTest {

    @Test
    fun sampleKeyCommitsExactlyOneCharacter() {
        val action = PlaceholderKeyboard.actionFor(PlaceholderKey.SAMPLE)
        assertTrue("字符键动作应为 CommitText，实际 $action", action is KeyAction.CommitText)
        val text = (action as KeyAction.CommitText).text
        // T3 验收口径：输入 1 个字符
        assertEquals("提交文本必须恰好 1 个字符", 1, text.length)
        assertEquals(PlaceholderKeyboard.SAMPLE_CHAR, text)
    }

    @Test
    fun dismissKeyReturnsHideSelf() {
        assertEquals(KeyAction.HideSelf, PlaceholderKeyboard.actionFor(PlaceholderKey.DISMISS))
    }

    @Test
    fun placeholderExposesMinimalKeySet() {
        // 最小闭环只需两个键：1 个可输入键 + 1 个收起键；键位膨胀属 T5+ 范围
        assertEquals(2, PlaceholderKey.entries.size)
    }
}
