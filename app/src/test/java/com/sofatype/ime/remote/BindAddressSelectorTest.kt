// T13 · 监听地址选择单元测试：锁定决策 16 选取规则（默认路由优先、回环不选、
// 接口必须 up、必须持有 IPv4、站点本地优先、同组按名稳定序）。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BindAddressSelectorTest {

    private fun nic(
        name: String,
        ipv4: String?,
        up: Boolean = true,
        loopback: Boolean = false,
        defaultRoute: Boolean = false,
    ) = NicCandidate(name, ipv4, up, loopback, defaultRoute)

    // ---- select：过滤与优先级 ----

    @Test
    fun prefersDefaultRouteInterface() {
        val chosen = BindAddressSelector.select(
            listOf(
                nic("eth0", "192.168.1.136", defaultRoute = false),
                nic("wlan0", "10.0.0.8", defaultRoute = true),
            ),
        )
        assertEquals("wlan0", chosen?.name)
        assertEquals("10.0.0.8", chosen?.ipv4)
    }

    @Test
    fun skipsLoopbackEvenWithDefaultRouteFlag() {
        assertNull(
            BindAddressSelector.select(
                listOf(nic("lo", "127.0.0.1", loopback = true, defaultRoute = true)),
            ),
        )
    }

    @Test
    fun skipsDownInterface() {
        assertNull(
            BindAddressSelector.select(
                listOf(nic("eth0", "192.168.1.136", up = false, defaultRoute = true)),
            ),
        )
    }

    @Test
    fun skipsInterfaceWithoutIpv4() {
        assertNull(
            BindAddressSelector.select(
                listOf(nic("dummy0", null, defaultRoute = true)),
            ),
        )
    }

    @Test
    fun emptyCandidatesYieldNull() {
        assertNull(BindAddressSelector.select(emptyList()))
    }

    @Test
    fun fallsBackToSiteLocalWhenNoDefaultRoute() {
        val chosen = BindAddressSelector.select(
            listOf(
                nic("rmnet0", "100.100.20.5"), // 非 RFC1918 全局地址
                nic("eth0", "10.0.2.15"),
            ),
        )
        assertEquals("10.0.2.15", chosen?.ipv4)
    }

    @Test
    fun sameClassCandidatesResolveByNameOrder() {
        val chosen = BindAddressSelector.select(
            listOf(
                nic("wlan1", "192.168.9.9"),
                nic("eth0", "192.168.1.136"),
            ),
        )
        assertEquals("eth0", chosen?.name)
    }

    @Test
    fun defaultRouteBeatsSiteLocalOnAnotherInterface() {
        val chosen = BindAddressSelector.select(
            listOf(
                nic("eth0", "192.168.1.136"), // 站点本地但非默认路由
                nic("usb0", "172.20.10.2", defaultRoute = true),
            ),
        )
        assertEquals("usb0", chosen?.name)
    }

    // ---- pickIpv4：同接口多地址 ----

    @Test
    fun pickIpv4PrefersSiteLocalOverLinkLocal() {
        assertEquals(
            "192.168.1.136",
            BindAddressSelector.pickIpv4(listOf("169.254.3.4", "192.168.1.136")),
        )
    }

    @Test
    fun pickIpv4SkipsLoopback() {
        assertNull(BindAddressSelector.pickIpv4(listOf("127.0.0.1", "127.1.2.3")))
    }

    @Test
    fun pickIpv4SkipsUnparseableValues() {
        assertEquals("10.0.2.15", BindAddressSelector.pickIpv4(listOf("not-an-ip", "10.0.2.15")))
    }

    @Test
    fun pickIpv4StableOrderWithinSameClass() {
        assertEquals("10.0.0.2", BindAddressSelector.pickIpv4(listOf("10.0.0.9", "10.0.0.2")))
    }

    // ---- IPv4 语义判定 ----

    @Test
    fun siteLocalRecognition() {
        for (address in listOf("10.0.2.15", "172.16.0.1", "172.31.255.254", "192.168.0.0")) {
            assertTrue(address, BindAddressSelector.isSiteLocalIpv4(address))
        }
        for (address in listOf("8.8.8.8", "172.15.0.1", "172.32.0.1", "192.169.0.1", "9.0.0.1", "100.64.0.1")) {
            assertFalse(address, BindAddressSelector.isSiteLocalIpv4(address))
        }
    }

    @Test
    fun loopbackRecognition() {
        assertTrue(BindAddressSelector.isLoopbackIpv4("127.0.0.1"))
        assertTrue(BindAddressSelector.isLoopbackIpv4("127.255.1.2"))
        assertFalse(BindAddressSelector.isLoopbackIpv4("192.168.1.1"))
        assertFalse(BindAddressSelector.isLoopbackIpv4("localhost"))
    }
}
