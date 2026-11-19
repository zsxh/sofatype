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

        assertEquals(0, UpdateVersion.compare("0.2.0", "v0.2.0-rc1"))

        assertTrue(UpdateVersion.compare("0.1.0", "v0.x.1") < 0)
    }

    @Test
    fun emptyInputsCompareAsZero() {
        assertEquals(0, UpdateVersion.compare("", "v"))
        assertEquals(0, UpdateVersion.compare("v", ""))
    }
}
