// T71 · 决策 60 · 查看页动态文案锁定：用量提示 / 统计行 / 用量数字格式（画板 14/15 文案口径）。
package com.sofatype.ime.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class LogUiTextTest {

    private val oneMb = 1024L * 1024L

    @Test
    fun `用量数字为 MB 一位小数`() {
        assertEquals("0.0", LogUiText.usedMbText(0))
        val nearly = (0.8 * oneMb).toLong()
        assertEquals("0.8", LogUiText.usedMbText(nearly))
        assertEquals("1.0", LogUiText.usedMbText(oneMb))
        assertEquals("2.0", LogUiText.usedMbText(2 * oneMb))
    }

    @Test
    fun `子页用量提示口径`() {
        val nearly = (0.8 * oneMb).toLong()
        assertEquals("当前 0.8 MB / 2 MB · 轮转覆盖", LogUiText.usageHint(nearly, 2))
        assertEquals("当前 0.0 MB / 10 MB · 轮转覆盖", LogUiText.usageHint(0, 10))
    }

    @Test
    fun `查看页统计行口径`() {
        val nearly = (0.8 * oneMb).toLong()
        assertEquals("共 1842 行 · 0.8 MB / 2 MB", LogUiText.stats(1842, nearly, 2))
        assertEquals("共 0 行 · 0.0 MB / 1 MB", LogUiText.stats(0, 0, 1))
    }

    @Test
    fun `截断说明行声明仅显示最新部分`() {
        assertEquals("（仅显示最新部分，完整内容经 adb 取证）\n", LogUiText.TAIL_NOTE)
    }
}
