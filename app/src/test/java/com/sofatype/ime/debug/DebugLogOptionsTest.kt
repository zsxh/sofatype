// T71 · 决策 60 · 日志档位模型锁定：四档 1/2/5/10 MB、默认 2 MB、字节换算、越界钳制、档位文案。
package com.sofatype.ime.debug

import org.junit.Assert.assertEquals
import org.junit.Test

class DebugLogOptionsTest {

    @Test
    fun `档位集合为四档且含默认档`() {
        assertEquals(listOf(1, 2, 5, 10), DebugLogOptions.MB_CHOICES.toList())
        assertTrue(DebugLogOptions.MB_CHOICES.toList().contains(DebugLogOptions.DEFAULT_MB))
    }

    @Test
    fun `默认档位为 2 MB`() {
        assertEquals(2, DebugLogOptions.DEFAULT_MB)
    }

    @Test
    fun `MB 换算字节`() {
        assertEquals(1024L * 1024L, DebugLogOptions.bytesFor(1))
        assertEquals(2L * 1024L * 1024L, DebugLogOptions.bytesFor(2))
        assertEquals(10L * 1024L * 1024L, DebugLogOptions.bytesFor(10))
    }

    @Test
    fun `越界档位钳制回合法档位`() {
        assertEquals(1, DebugLogOptions.clampMb(0))
        assertEquals(1, DebugLogOptions.clampMb(1))
        assertEquals(2, DebugLogOptions.clampMb(2))
        assertEquals(5, DebugLogOptions.clampMb(4))
        assertEquals(10, DebugLogOptions.clampMb(9))
        assertEquals(10, DebugLogOptions.clampMb(100))
        // 低于下限（含负值）钳回最小合法档位
        assertEquals(1, DebugLogOptions.clampMb(-3))
    }

    @Test
    fun `档位文案口径`() {
        assertEquals("1 MB", DebugLogOptions.label(1))
        assertEquals("2 MB", DebugLogOptions.label(2))
        assertEquals("10 MB", DebugLogOptions.label(10))
    }

    private fun assertTrue(v: Boolean) {
        org.junit.Assert.assertTrue(v)
    }
}
