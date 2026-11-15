// T71 · 决策 60 · 轮转规划锁定：未超档不轮转；超档触发保留最新（环形语义）、
// 保留量 = 档位一半、丢弃量 = 超出部分；档位下限兜底。
package com.sofatype.ime.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogRotationTest {

    private val oneMb = 1024L * 1024L

    @Test
    fun `未超档位不轮转`() {
        assertNull(LogRotation.plan(0, 100, oneMb))
        assertNull(LogRotation.plan(oneMb - 100, 100, oneMb)) // 恰好贴线不超
        assertNull(LogRotation.plan(oneMb, 0, oneMb))
    }

    @Test
    fun `超档位触发轮转且保留一半档位`() {
        val plan = LogRotation.plan(oneMb, 1, oneMb)
        assertEquals(oneMb / 2, plan!!.keepBytes)
        // 丢弃量 = 现有长度 - 保留量
        assertEquals(oneMb - oneMb / 2, plan.discardBytes)
    }

    @Test
    fun `现有长度不足保留量时丢弃为零`() {
        // 现有 100 字节 + 巨量写入触发轮转：保留量上限受现有长度约束，丢弃 = 0
        val plan = LogRotation.plan(100, oneMb * 3, oneMb)
        assertEquals(oneMb / 2, plan!!.keepBytes)
        assertEquals(0, plan.discardBytes)
    }

    @Test
    fun `档位非正值兜底为 1 字节计划`() {
        val plan = LogRotation.plan(10, 1, 0)
        assertEquals(0, plan!!.keepBytes) // 1/2 = 0
        assertEquals(10, plan.discardBytes)
        assertNull(LogRotation.plan(0, 0, 0)) // 无写入不轮转
    }
}
