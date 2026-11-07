// T13 · HTTP 路由单元测试：/ 下发占位页、/ws 升级判定、404/405/400 分支、响应字节构造。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class HttpRouterTest {

    private fun request(method: String, path: String, vararg headers: Pair<String, String>) =
        HttpRequest(method, path, linkedMapOf(*headers))

    private fun upgradeHeaders() = arrayOf(
        "host" to "tv:32923",
        "upgrade" to "websocket",
        "connection" to "Upgrade",
        "sec-websocket-key" to "dGhlIHNhbXBsZSBub25jZQ==",
        "sec-websocket-version" to "13",
    )

    @Test
    fun rootGetServesPlaceholderPage() {
        val route = HttpRouter.route(request("GET", "/", "host" to "x"))
        assertTrue(route is RouteResult.Respond)
        val bytes = (route as RouteResult.Respond).bytes
        val text = String(bytes, StandardCharsets.UTF_8)
        assertTrue(text.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(text.contains("Content-Type: text/html; charset=utf-8"))
        // T14 配对占位页：从 URL query 取 token、发 hello、展示「已连接 · TV名」
        assertTrue(text.contains("sofatype 跨屏输入"))
        assertTrue(text.contains("'hello'"))
        assertTrue(text.contains("token"))
        assertTrue(text.contains("已连接 · "))
    }

    @Test
    fun rootResponseContentLengthMatchesBodyBytes() {
        val bytes = (HttpRouter.route(request("GET", "/")) as RouteResult.Respond).bytes
        val headEnd = indexOfEmptyLine(bytes)
        val head = String(bytes.copyOf(headEnd), StandardCharsets.US_ASCII)
        val declared = head.lineSequence().first { it.startsWith("Content-Length:") }
            .substringAfter(':').trim().toInt()
        assertEquals(bytes.size - headEnd - 4, declared)
    }

    /** 定位头块结束符 CRLFCRLF 的起点。 */
    private fun indexOfEmptyLine(bytes: ByteArray): Int {
        for (i in 0..bytes.size - 4) {
            if (bytes[i] == 0x0D.toByte() && bytes[i + 1] == 0x0A.toByte() &&
                bytes[i + 2] == 0x0D.toByte() && bytes[i + 3] == 0x0A.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    @Test
    fun rootNonGetIsMethodNotAllowed() {
        for (method in listOf("POST", "PUT", "DELETE", "HEAD")) {
            val route = HttpRouter.route(request(method, "/", "host" to "x"))
            val text = String((route as RouteResult.Respond).bytes, StandardCharsets.US_ASCII)
            assertTrue(method, text.startsWith("HTTP/1.1 405 Method Not Allowed\r\n"))
        }
    }

    @Test
    fun wsPathWithValidUpgradeHeadersSchedulesUpgrade() {
        val route = HttpRouter.route(request("GET", "/ws", *upgradeHeaders()))
        assertEquals(RouteResult.UpgradeWebSocket, route)
    }

    @Test
    fun wsPathWithoutUpgradeHeadersIsRejected() {
        val route = HttpRouter.route(request("GET", "/ws", "host" to "x"))
        val text = String((route as RouteResult.Respond).bytes, StandardCharsets.US_ASCII)
        assertTrue(text.startsWith("HTTP/1.1 400 Bad Request\r\n"))
    }

    @Test
    fun wsPathNonGetIsNotFound() {
        val route = HttpRouter.route(request("POST", "/ws", *upgradeHeaders()))
        val text = String((route as RouteResult.Respond).bytes, StandardCharsets.US_ASCII)
        assertTrue(text.startsWith("HTTP/1.1 404 Not Found\r\n"))
    }

    @Test
    fun unknownPathIsNotFound() {
        val route = HttpRouter.route(request("GET", "/nope", "host" to "x"))
        val text = String((route as RouteResult.Respond).bytes, StandardCharsets.US_ASCII)
        assertTrue(text.startsWith("HTTP/1.1 404 Not Found\r\n"))
    }

    @Test
    fun httpResponsesCarryConnectionClose() {
        val bytes = (HttpRouter.route(request("GET", "/")) as RouteResult.Respond).bytes
        val text = String(bytes, StandardCharsets.US_ASCII)
        assertTrue(text.contains("Connection: close\r\n"))
    }
}
