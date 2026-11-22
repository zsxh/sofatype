package com.sofatype.ime.dicts

import org.junit.Assert.assertEquals
import org.junit.Test

class DictPackEndpointTest {

    @Test
    fun `正式目录地址为构建期常量_当前显式缺失为空串`() {
        DictPackEndpoint.setDebugOverride(null)
        assertEquals(DictPackEndpoint.OFFICIAL_CATALOG_URL, DictPackEndpoint.catalogUrl())
        assertEquals("官方 URL 字面值缺失项以空串落地（零猜测）", "", DictPackEndpoint.catalogUrl())
    }

    @Test
    fun `调试覆写仅内存生效_空串与null恢复正式值`() {
        DictPackEndpoint.setDebugOverride("http://10.0.2.2:8000/wordpack-manifest.json")
        assertEquals("http://10.0.2.2:8000/wordpack-manifest.json", DictPackEndpoint.catalogUrl())

        DictPackEndpoint.setDebugOverride("")
        assertEquals(DictPackEndpoint.OFFICIAL_CATALOG_URL, DictPackEndpoint.catalogUrl())

        DictPackEndpoint.setDebugOverride(null)
        assertEquals(DictPackEndpoint.OFFICIAL_CATALOG_URL, DictPackEndpoint.catalogUrl())
    }
}
