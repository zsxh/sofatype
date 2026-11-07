// T13 · WebSocket 握手单元测试：Sec-WebSocket-Accept 计算（RFC 6455 示例向量）、
// 升级请求判定、101 响应字节。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class WsHandshakeTest {

    @Test
    fun acceptKeyMatchesRfc6455Example() {
        // RFC 6455 4.2.2 第 10 步示例
        assertEquals("s3pPLMBiTxaQ9kYGzzhZRbK+xOo=", WsHandshake.acceptKey("dGhlIHNhbXBsZSBub25jZQ=="))
    }

    @Test
    fun acceptKeyIsDeterministicAndTrimsInput() {
        assertEquals(
            WsHandshake.acceptKey("abc"),
            WsHandshake.acceptKey("  abc  "),
        )
    }

    @Test
    fun upgradeResponseContainsRequiredHeaders() {
        val text = String(
            WsHandshake.upgradeResponse("dGhlIHNhbXBsZSBub25jZQ=="),
            StandardCharsets.US_ASCII,
        )
        assertTrue(text.startsWith("HTTP/1.1 101 Switching Protocols\r\n"))
        assertTrue(text.contains("Upgrade: websocket\r\n"))
        assertTrue(text.contains("Connection: Upgrade\r\n"))
        assertTrue(text.contains("Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=\r\n"))
        assertTrue(text.endsWith("\r\n\r\n"))
    }

    private fun headers(vararg pairs: Pair<String, String>) = pairs.toMap()

    @Test
    fun validUpgradeRequestIsRecognized() {
        assertTrue(
            WsHandshake.isUpgradeRequest(
                headers(
                    "upgrade" to "websocket",
                    "sec-websocket-key" to "some-key",
                    "sec-websocket-version" to "13",
                ),
            ),
        )
    }

    @Test
    fun upgradeValueIsCaseInsensitive() {
        assertTrue(
            WsHandshake.isUpgradeRequest(
                headers(
                    "upgrade" to "WebSocket",
                    "sec-websocket-key" to "some-key",
                    "sec-websocket-version" to "13",
                ),
            ),
        )
    }

    @Test
    fun missingKeyIsNotUpgrade() {
        assertFalse(
            WsHandshake.isUpgradeRequest(
                headers("upgrade" to "websocket", "sec-websocket-version" to "13"),
            ),
        )
        assertFalse(
            WsHandshake.isUpgradeRequest(
                headers(
                    "upgrade" to "websocket",
                    "sec-websocket-key" to "   ",
                    "sec-websocket-version" to "13",
                ),
            ),
        )
    }

    @Test
    fun wrongVersionIsNotUpgrade() {
        assertFalse(
            WsHandshake.isUpgradeRequest(
                headers(
                    "upgrade" to "websocket",
                    "sec-websocket-key" to "some-key",
                    "sec-websocket-version" to "12",
                ),
            ),
        )
    }

    @Test
    fun missingUpgradeHeaderIsNotUpgrade() {
        assertFalse(
            WsHandshake.isUpgradeRequest(
                headers("sec-websocket-key" to "some-key", "sec-websocket-version" to "13"),
            ),
        )
    }
}
