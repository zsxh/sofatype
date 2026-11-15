// T72 · 决策 61 · TV 端序号注册表单元测试：锁定 LRU 内存表签发与保号语义——
// 表空回 01、未命中全表最大 +1、命中沿用原序号、TTL 15 分钟自最近 WS 活动滚动续期
// （连接中永不变：钉住键不被惰性清除与满员淘汰）、容量 8 满淘汰最久未见、
// 断开后 15 分钟窗口内重连保号（超窗失号重签）、匿名签发仅当次连接固定。
// 时钟全部由测试注入 nowMs（注册表不自取系统时钟）。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class T72ClientCodeRegistryTest {

    private fun registry(ttlMs: Long = ClientCodeRegistry.DEFAULT_TTL_MS) = ClientCodeRegistry(ttlMs = ttlMs)

    private val wechat = DeviceType.WECHAT
    private val apple = DeviceType.APPLE
    private val android = DeviceType.ANDROID
    private val browser = DeviceType.BROWSER

    // ---- 表空回 01 / 未命中全表最大 +1 ----

    @Test
    fun emptyTableIssuesNumberOne() {
        // 表空回 01（决策 61）
        val reg = registry()
        assertEquals("微信设备 01", reg.issue("key-a", wechat, nowMs = 0L).text)
    }

    @Test
    fun missTakesTableMaxPlusOne() {
        // 未命中取全表最大序号 +1
        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.issue("key-b", apple, nowMs = 1L)
        assertEquals(3, reg.issue("key-c", android, nowMs = 2L).number)
    }

    // ---- 命中沿用原序号（刷新 / 断线重连 / 被顶替后再连，窗口内代号不变） ----

    @Test
    fun hitKeepsNumberAcrossReconnects() {
        val reg = registry()
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 0L).number)
        // 刷新 / 断线重连（窗口内）：沿用原序号
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 5 * 60 * 1000L).number)
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 10 * 60 * 1000L).number)
    }

    @Test
    fun hitUpdatesTypeButKeepsNumber() {
        // 命中沿用序号，类型随本次 hello 更新（同端换浏览器形态的防御口径：键同则设备同）
        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        val reissued = reg.issue("key-a", browser, nowMs = 1000L)
        assertEquals(browser, reissued.type)
        assertEquals(1, reissued.number)
        assertEquals("浏览器设备 01", reissued.text)
    }

    @Test
    fun numberedKeysRemainStableWhenNewDevicesInterleave() {
        // 多设备交替重连：各自序号稳定可辨（保号机制核心语义）
        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.issue("key-b", apple, nowMs = 1L)
        reg.issue("key-c", android, nowMs = 2L)
        assertEquals(2, reg.issue("key-b", apple, nowMs = 3L).number)
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 4L).number)
        assertEquals(3, reg.issue("key-c", android, nowMs = 5L).number)
    }

    // ---- TTL 15 分钟滚动续期 / 断开窗口保号 ----

    @Test
    fun touchRollsTtlForward() {
        // 自最近一次 WS 活动滚动续期：touch 后过期判定随新 lastSeen 起算
        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.pin("key-a")
        reg.touch("key-a", nowMs = 10 * 60 * 1000L)
        // 15 分钟自 touch 起算：TTL 边界内重连保号
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 24 * 60 * 1000L + 59_999L).number)
    }

    @Test
    fun reconnectWithinTtlAfterDisconnectKeepsNumber() {
        // 决策 59 衔接：断开后窗口自最后活动起算 15 分钟，期内重连保号（断开不清表）
        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.pin("key-a")
        reg.unpin() // 会话断开
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 15 * 60 * 1000L - 1L).number)
    }

    @Test
    fun reconnectAfterTtlIssuesFreshNumber() {
        // 超窗（15 分钟无任何活动）：条目惰性清除，重连失号——表内仅剩其他设备时取 max+1
        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.issue("key-b", apple, nowMs = 0L)
        reg.pin("key-a")
        reg.unpin()
        // key-a 已超窗；key-b 经 WS 活动 touch 续期存活（lastSeen 推进）→ max = 02，key-a 重签 03
        reg.touch("key-b", nowMs = 20 * 60 * 1000L)
        assertEquals(2, reg.issue("key-b", apple, nowMs = 20 * 60 * 1000L).number)
        assertEquals(3, reg.issue("key-a", wechat, nowMs = 20 * 60 * 1000L).number)
    }

    @Test
    fun expiredEntriesArePurgedLazilyOnNextAccess() {
        // 全表过期：表空回 01（决策 61「表空回 01」语义在过期清理后同样成立）
        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.issue("key-b", apple, nowMs = 0L)
        assertEquals(1, reg.issue("key-c", android, nowMs = ClientCodeRegistry.DEFAULT_TTL_MS + 1L).number)
    }

    // ---- 连接中代号永不变（钉住键不受清除与淘汰影响） ----

    @Test
    fun pinnedKeySurvivesTtlExpiryWhileConnected() {
        // 连接中（钉住）即使无活动超过 TTL 也不清键——代号刷新只发生在重连时刻
        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.pin("key-a")
        // 远超 TTL 后其他键触发惰性清理：钉住键仍在，命中沿用
        reg.issue("key-z", apple, nowMs = 10 * ClientCodeRegistry.DEFAULT_TTL_MS)
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 10 * ClientCodeRegistry.DEFAULT_TTL_MS).number)
    }

    @Test
    fun pinnedKeyIsNeverEvictedWhenTableIsFull() {
        // 容量 8 已满 + 当前连接（钉住）为最久未见：淘汰只落在非钉住键上，连接中代号永不变
        val reg = registry()
        reg.issue("pinned", wechat, nowMs = 0L)
        reg.pin("pinned")
        for (i in 1..7) {
            reg.issue("key-$i", apple, nowMs = i.toLong() * 1000L)
        }
        reg.touch("pinned", nowMs = 0L) // pinned 最久未见（lastSeen 0，其余递增）
        // 新设备到达触发满员淘汰：pinned 不被淘汰
        reg.issue("key-new", android, nowMs = 10_000L)
        assertEquals(1, reg.issue("pinned", wechat, nowMs = 10_001L).number)
    }

    @Test
    fun evictionDropsLeastRecentlySeen() {
        // 满淘汰最久未见：lastSeen 最小的键被挤出，新键获得 max+1
        val reg = registry()
        for (i in 1..8) {
            assertEquals(i, reg.issue("key-$i", DeviceType.entries[i % DeviceType.entries.size], nowMs = i.toLong() * 1000L).number)
        }
        // key-1（lastSeen 1000）最久未见；第 9 台到达 → 淘汰 key-1，签发 09
        assertEquals(9, reg.issue("key-new", browser, nowMs = 9_000L).number)
        // key-1 已失号：重连按未命中重签（max+1）
        assertEquals(10, reg.issue("key-1", wechat, nowMs = 9_001L).number)
    }

    @Test
    fun capacityStaysAtLimit() {
        // 容量上限 8 守恒：连发 20 键后表内存活键 = 最近 8 键（key-13..key-20）
        val reg = registry()
        for (i in 1..20) {
            reg.issue("key-$i", browser, nowMs = i.toLong() * 1000L)
        }
        // 存活键重连沿用原序号（未淘汰）
        assertEquals(14, reg.issue("key-14", browser, nowMs = 20_001L).number)
        // 已淘汰键（key-1）重连必未命中：淘汰最久未见 key-13 后取全表最大 +1 = 21
        assertEquals(21, reg.issue("key-1", browser, nowMs = 20_002L).number)
    }

    // ---- 匿名签发（hello 未携带 deviceKey）：仅当次连接内固定 ----

    @Test
    fun anonymousIssuesSameRuleNumberWithoutStoring() {
        // 匿名按新设备签发 max+1、不入表：同表状态下两次匿名均得同一 max+1（表序号不冲突）
        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        assertEquals(2, reg.issue(null, browser, nowMs = 1L).number)
        assertEquals(2, reg.issue(null, browser, nowMs = 2L).number)
        // 匿名不占表位：后续未命中键仍从 max+1（=2）起签
        assertEquals(2, reg.issue("key-b", apple, nowMs = 3L).number)
    }

    @Test
    fun anonymousOnEmptyTableGetsOne() {
        val reg = registry()
        assertEquals(1, reg.issue(null, browser, nowMs = 0L).number)
    }

    // ---- touch 细节 ----

    @Test
    fun touchIsNoOpForUnknownOrNullKeys() {
        // 匿名 / 未知键 touch 无害（不建条目、不抛异常）
        val reg = registry()
        reg.touch(null, nowMs = 0L)
        reg.touch("ghost", nowMs = 0L)
        assertEquals("浏览器设备 01", reg.issue("fresh", browser, nowMs = 1000L).text)
    }

    // ---- pin / clear ----

    @Test
    fun clearResetsTableButKeepsPinContract() {
        // 清表（仅测试口径）后重签回 01；pin/unpin 独立于表内容
        val reg = registry()
        reg.issue("key-a", wechat, nowMs = 0L)
        reg.pin("key-a")
        reg.clear()
        reg.unpin()
        assertEquals(1, reg.issue("key-a", wechat, nowMs = 1000L).number)
    }
}
