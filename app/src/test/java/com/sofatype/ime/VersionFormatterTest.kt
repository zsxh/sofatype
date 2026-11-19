package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VersionFormatterTest {

    @Test
    fun baselineVersionMatchesDecision19() {
        assertEquals("v0.1.0", VersionFormatter.format("0.1.0"))
    }

    @Test
    fun prdFormatSampleMatches() {
        assertEquals("v1.2.3", VersionFormatter.format("1.2.3"))
    }

    @Test
    fun omitsVersionCodeSegment() {
        val text = VersionFormatter.format("0.2.0")
        assertEquals("v0.2.0", text)
        assertFalse("版本行不得含 versionCode 段（括号）", text.contains("("))
    }

    @Test
    fun nullVersionNameRendersEmptyName() {
        assertEquals("v", VersionFormatter.format(null))
    }
}
