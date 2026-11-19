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

        assertEquals(0, RemoteTextScrollTarget.bottomOffset(contentHeightPx = 900, viewportHeightPx = 0))
        assertEquals(0, RemoteTextScrollTarget.bottomOffset(contentHeightPx = 900, viewportHeightPx = -1))
    }
}
