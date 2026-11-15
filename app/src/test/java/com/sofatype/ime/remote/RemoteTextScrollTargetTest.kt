// T76 · 远程大字区装载滚动目标单元测试（JUnit 4，纯 Kotlin · 决策 31 滚动保持末尾）。
// 锁定边界：内容超出视口 = 差值（滚动到底）；未超出 / 恰好占满 = 0（不滚动）；
// 高度缺失或非法（≤ 0）= 0（防御分支不滚动）。接线在 SofatypeImeService.renderRemoteText，
// 焦点保持行为（滚动不触碰焦点树）为渲染层事实，走模拟器走查取证。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteTextScrollTargetTest {

    @Test
    fun `内容超出视口返回差值`() {
        assertEquals(300, RemoteTextScrollTarget.bottomOffset(contentHeightPx = 900, viewportHeightPx = 600))
    }

    @Test
    fun `内容未超出视口返回零`() {
        assertEquals(0, RemoteTextScrollTarget.bottomOffset(contentHeightPx = 500, viewportHeightPx = 600))
    }

    @Test
    fun `内容恰好占满视口返回零`() {
        assertEquals(0, RemoteTextScrollTarget.bottomOffset(contentHeightPx = 600, viewportHeightPx = 600))
    }

    @Test
    fun `内容高度非法返回零`() {
        assertEquals(0, RemoteTextScrollTarget.bottomOffset(contentHeightPx = 0, viewportHeightPx = 600))
        assertEquals(0, RemoteTextScrollTarget.bottomOffset(contentHeightPx = -1, viewportHeightPx = 600))
    }

    @Test
    fun `视口高度非法返回零`() {
        // 未测量 / 视图缺子项防御分支：视口 ≤ 0 不滚动
        assertEquals(0, RemoteTextScrollTarget.bottomOffset(contentHeightPx = 900, viewportHeightPx = 0))
        assertEquals(0, RemoteTextScrollTarget.bottomOffset(contentHeightPx = 900, viewportHeightPx = -1))
    }
}
