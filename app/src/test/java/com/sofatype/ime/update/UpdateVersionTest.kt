// T21 · 版本比较单元测试（决策 17「tag = 版本名」，JUnit 4，纯 JVM）。
package com.sofatype.ime.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {

    @Test
    fun equalVersionsWithVPrefixMatch() {
        assertEquals(0, UpdateVersion.compare("0.1.0", "v0.1.0"))
        assertEquals(0, UpdateVersion.compare("0.1.0", "V0.1.0"))
    }

    @Test
    fun newerReleaseIsPositive() {
        assertTrue(UpdateVersion.compare("0.1.0", "v0.1.1") > 0)
        assertTrue(UpdateVersion.compare("0.1.0", "v0.2.0") > 0)
        assertTrue(UpdateVersion.compare("0.1.0", "v1.0.0") > 0)
        assertTrue(UpdateVersion.compare("0.1.9", "v0.10.0") > 0)
    }

    @Test
    fun olderReleaseIsNegative() {
        assertTrue(UpdateVersion.compare("0.2.0", "v0.1.0") < 0)
        assertTrue(UpdateVersion.compare("1.0.0", "v0.9.9") < 0)
    }

    @Test
    fun missingSegmentsPaddedWithZero() {
        assertEquals(0, UpdateVersion.compare("0.1.0", "v0.1"))
        assertEquals(0, UpdateVersion.compare("0.1", "v0.1.0"))
        assertTrue(UpdateVersion.compare("0.1", "v0.2") > 0)
    }

    @Test
    fun nonNumericSegmentCountsAsZero() {
        // 「0.2.0-rc1」段内前导数字为 0 → 与 0.2.0 相同
        assertEquals(0, UpdateVersion.compare("0.2.0", "v0.2.0-rc1"))
        // 非数字段按 0：「0.x.1」= 0.0.1 < 0.1.0 → release 更旧为负
        assertTrue(UpdateVersion.compare("0.1.0", "v0.x.1") < 0)
    }

    @Test
    fun emptyInputsCompareAsZero() {
        assertEquals(0, UpdateVersion.compare("", "v"))
        assertEquals(0, UpdateVersion.compare("v", ""))
    }
}
