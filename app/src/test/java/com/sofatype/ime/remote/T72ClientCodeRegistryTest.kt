package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class T72ClientCodeRegistryTest {

    private fun registry(ttlMs: Long = ClientCodeRegistry.DEFAULT_TTL_MS) = ClientCodeRegistry(ttlMs = ttlMs)

    private val wechat = DeviceType.WECHAT
    private val apple = DeviceType.APPLE
    private val android = DeviceType.ANDROID
    private val browser = DeviceType.BROWSER

    @Test
    fun emptyTableIssuesNumberOne() {

        val reg = registry()
        assertEquals("微信设备 01", reg.issue("key-a", wechat, nowMs = 0L).text)
    }

    @Test
    fun missTakesTableMaxPlusOne() {

        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.issue("key-b", apple, nowMs = 1L)
        assertEquals(3, reg.issue("key-c", android, nowMs = 2L).number)
    }

    @Test
    fun hitKeepsNumberAcrossReconnects() {
        val reg = registry()
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 0L).number)

        assertEquals(1, reg.issue("key-a", wechat, nowMs = 5 * 60 * 1000L).number)
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 10 * 60 * 1000L).number)
    }

    @Test
    fun hitUpdatesTypeButKeepsNumber() {

        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        val reissued = reg.issue("key-a", browser, nowMs = 1000L)
        assertEquals(browser, reissued.type)
        assertEquals(1, reissued.number)
        assertEquals("浏览器设备 01", reissued.text)
    }

    @Test
    fun numberedKeysRemainStableWhenNewDevicesInterleave() {

        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.issue("key-b", apple, nowMs = 1L)
        reg.issue("key-c", android, nowMs = 2L)
        assertEquals(2, reg.issue("key-b", apple, nowMs = 3L).number)
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 4L).number)
        assertEquals(3, reg.issue("key-c", android, nowMs = 5L).number)
    }

    @Test
    fun touchRollsTtlForward() {

        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.pin("key-a")
        reg.touch("key-a", nowMs = 10 * 60 * 1000L)

        assertEquals(1, reg.issue("key-a", wechat, nowMs = 24 * 60 * 1000L + 59_999L).number)
    }

    @Test
    fun reconnectWithinTtlAfterDisconnectKeepsNumber() {

        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.pin("key-a")
        reg.unpin()
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 15 * 60 * 1000L - 1L).number)
    }

    @Test
    fun reconnectAfterTtlIssuesFreshNumber() {

        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.issue("key-b", apple, nowMs = 0L)
        reg.pin("key-a")
        reg.unpin()

        reg.touch("key-b", nowMs = 20 * 60 * 1000L)
        assertEquals(2, reg.issue("key-b", apple, nowMs = 20 * 60 * 1000L).number)
        assertEquals(3, reg.issue("key-a", wechat, nowMs = 20 * 60 * 1000L).number)
    }

    @Test
    fun expiredEntriesArePurgedLazilyOnNextAccess() {

        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.issue("key-b", apple, nowMs = 0L)
        assertEquals(1, reg.issue("key-c", android, nowMs = ClientCodeRegistry.DEFAULT_TTL_MS + 1L).number)
    }

    @Test
    fun pinnedKeySurvivesTtlExpiryWhileConnected() {

        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.pin("key-a")

        reg.issue("key-z", apple, nowMs = 10 * ClientCodeRegistry.DEFAULT_TTL_MS)
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 10 * ClientCodeRegistry.DEFAULT_TTL_MS).number)
    }

    @Test
    fun pinnedKeyIsNeverEvictedWhenTableIsFull() {

        val reg = registry()
        reg.issue("pinned", wechat, nowMs = 0L)
        reg.pin("pinned")
        for (i in 1..7) {
            reg.issue("key-$i", apple, nowMs = i.toLong() * 1000L)
        }
        reg.touch("pinned", nowMs = 0L)

        reg.issue("key-new", android, nowMs = 10_000L)
        assertEquals(1, reg.issue("pinned", wechat, nowMs = 10_001L).number)
    }

    @Test
    fun evictionDropsLeastRecentlySeen() {

        val reg = registry()
        for (i in 1..8) {
            assertEquals(i, reg.issue("key-$i", DeviceType.entries[i % DeviceType.entries.size], nowMs = i.toLong() * 1000L).number)
        }

        assertEquals(9, reg.issue("key-new", browser, nowMs = 9_000L).number)

        assertEquals(10, reg.issue("key-1", wechat, nowMs = 9_001L).number)
    }

    @Test
    fun capacityStaysAtLimit() {

        val reg = registry()
        for (i in 1..20) {
            reg.issue("key-$i", browser, nowMs = i.toLong() * 1000L)
        }

        assertEquals(14, reg.issue("key-14", browser, nowMs = 20_001L).number)

        assertEquals(21, reg.issue("key-1", browser, nowMs = 20_002L).number)
    }

    @Test
    fun anonymousIssuesSameRuleNumberWithoutStoring() {

        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        assertEquals(2, reg.issue(null, browser, nowMs = 1L).number)
        assertEquals(2, reg.issue(null, browser, nowMs = 2L).number)

        assertEquals(2, reg.issue("key-b", apple, nowMs = 3L).number)
    }

    @Test
    fun anonymousOnEmptyTableGetsOne() {
        val reg = registry()
        assertEquals(1, reg.issue(null, browser, nowMs = 0L).number)
    }

    @Test
    fun touchIsNoOpForUnknownOrNullKeys() {

        val reg = registry()
        reg.touch(null, nowMs = 0L)
        reg.touch("ghost", nowMs = 0L)
        assertEquals("浏览器设备 01", reg.issue("fresh", browser, nowMs = 1000L).text)
    }

    @Test
    fun clearResetsTableButKeepsPinContract() {

        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.pin("key-a")
        reg.clear()
        reg.unpin()
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 1000L).number)
    }
}
