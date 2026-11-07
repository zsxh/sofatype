// T15 · 远程输入缓冲模型单元测试（FR-C7 字数上限 + 第 8 章隐私）：
// 200 码点上限、超限截断不拆代理对（emoji 完整）、clear、防御性兜底限长。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteTextBufferTest {

    @Test
    fun setStoresFullTextUnderLimit() {
        val buffer = RemoteTextBuffer(charLimit = 200)
        assertEquals("客厅的小米盒子", buffer.set("客厅的小米盒子"))
        assertTrue(buffer.hasText)
    }

    @Test
    fun clearEmptiesBufferAndIsIdempotent() {
        val buffer = RemoteTextBuffer()
        buffer.set("内容")
        buffer.clear()
        assertEquals("", buffer.text)
        assertFalse(buffer.hasText)
        buffer.clear()
        assertEquals("", buffer.text)
    }

    @Test
    fun setEmptyTextOverwritesPreviousContent() {
        val buffer = RemoteTextBuffer()
        buffer.set("旧内容")
        assertEquals("", buffer.set(""))
        assertFalse(buffer.hasText)
    }

    @Test
    fun clampTruncatesToLimitByCodePoints() {
        val text = "abcde"
        assertEquals("abc", RemoteTextBuffer.clamp(text, 3))
        assertEquals("abcde", RemoteTextBuffer.clamp(text, 5))
        assertEquals("abcde", RemoteTextBuffer.clamp(text, 6))
    }

    @Test
    fun clampCountsSupplementaryPairAsOneCodePoint() {
        // 😀 = 1 个码点（2 个 UTF-16 单元）
        val emoji = "😀"
        assertEquals(2, emoji.length)
        assertEquals(1, codePoints(emoji))
        val clamped = RemoteTextBuffer.clamp("😀😀😀", 2)
        assertEquals("😀😀", clamped)
        assertEquals(2, codePoints(clamped))
    }

    @Test
    fun clampNeverSplitsSurrogatePairAtBoundary() {
        // "a😀"（3 单元）限 2 单元宽的码点数：边界落在代理对中间时整对排除
        val clamped = RemoteTextBuffer.clamp("a😀b", 1)
        assertEquals("a", clamped)
        val clamped2 = RemoteTextBuffer.clamp("a😀b", 2)
        assertEquals("a😀", clamped2)
    }

    @Test
    fun clampHandlesEmptyAndZeroLimit() {
        assertEquals("", RemoteTextBuffer.clamp("", 200))
        assertEquals("", RemoteTextBuffer.clamp("任意", 0))
    }

    @Test
    fun setClampsOverLimitInputDefensively() {
        // 手机端 H5 已限流（FR-C7）；服务端兜底：缓冲永不超上限
        val buffer = RemoteTextBuffer(charLimit = 5)
        val stored = buffer.set("abcdefgh")
        assertEquals("abcde", stored)
        assertEquals("abcde", buffer.text)
    }

    @Test
    fun repeatedSetKeepsBufferWithinLimitWithEmoji() {
        val buffer = RemoteTextBuffer(charLimit = 3)
        buffer.set("😀😀😀😀😀")
        assertEquals("😀😀😀", buffer.text)
        buffer.set("中文中文中文中文")
        assertEquals("中文中", buffer.text)
    }

    @Test
    fun defaultCharLimitMatchesProtocol() {
        assertEquals(200, RemoteTextBuffer().charLimit)
        assertEquals(PairingSession.DEFAULT_CHAR_LIMIT, RemoteTextBuffer().charLimit)
    }

    /** 按 Unicode 码点计数（代理对计 1）。 */
    private fun codePoints(text: String): Int {
        var count = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            i += if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
            count++
        }
        return count
    }
}
