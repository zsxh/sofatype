// T20 · 版本展示文本单元测试（FR-D3 / 决策 19，JUnit 4，纯 JVM）。
// 锁定格式「v<versionName> (<versionCode>)」：基线 0.1.0 (1)（决策 19）、PRD 格式示例 1.2.3 (63)、
// 多位 versionCode、versionName 为 null 的兜底渲染。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionFormatterTest {

    @Test
    fun baselineVersionMatchesDecision19() {
        assertEquals("v0.1.0 (1)", VersionFormatter.format("0.1.0", 1))
    }

    @Test
    fun prdFormatSampleMatches() {
        assertEquals("v1.2.3 (63)", VersionFormatter.format("1.2.3", 63))
    }

    @Test
    fun multiDigitVersionCodeKeepsParenthesisFormat() {
        assertEquals("v0.2.0 (12)", VersionFormatter.format("0.2.0", 12))
    }

    @Test
    fun nullVersionNameRendersEmptyName() {
        assertEquals("v (5)", VersionFormatter.format(null, 5))
    }
}
