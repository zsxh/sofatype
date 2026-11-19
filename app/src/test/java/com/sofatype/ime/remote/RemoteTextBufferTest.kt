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

        val emoji = "😀"
        assertEquals(2, emoji.length)
        assertEquals(1, codePoints(emoji))
        val clamped = RemoteTextBuffer.clamp("😀😀😀", 2)
        assertEquals("😀😀", clamped)
        assertEquals(2, codePoints(clamped))
    }

    @Test
    fun clampNeverSplitsSurrogatePairAtBoundary() {

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
