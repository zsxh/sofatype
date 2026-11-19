package com.sofatype.ime.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogRotationTest {

    private val oneMb = 1024L * 1024L

    @Test
    fun `未超档位不轮转`() {
        assertNull(LogRotation.plan(0, 100, oneMb))
        assertNull(LogRotation.plan(oneMb - 100, 100, oneMb))
        assertNull(LogRotation.plan(oneMb, 0, oneMb))
    }

    @Test
    fun `超档位触发轮转且保留一半档位`() {
        val plan = LogRotation.plan(oneMb, 1, oneMb)
        assertEquals(oneMb / 2, plan!!.keepBytes)

        assertEquals(oneMb - oneMb / 2, plan.discardBytes)
    }

    @Test
    fun `现有长度不足保留量时丢弃为零`() {

        val plan = LogRotation.plan(100, oneMb * 3, oneMb)
        assertEquals(oneMb / 2, plan!!.keepBytes)
        assertEquals(0, plan.discardBytes)
    }

    @Test
    fun `档位非正值兜底为 1 字节计划`() {
        val plan = LogRotation.plan(10, 1, 0)
        assertEquals(0, plan!!.keepBytes)
        assertEquals(10, plan.discardBytes)
        assertNull(LogRotation.plan(0, 0, 0))
    }
}
