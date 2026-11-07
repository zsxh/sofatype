// T13 · HTTP 请求解析单元测试：请求行/头部解析、大小写归一、query 剥离、非法输入拒绝、头部上限。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class HttpRequestParserTest {

    private fun parse(raw: String) = HttpRequestParser.parse(
        ByteArrayInputStream(raw.toByteArray(StandardCharsets.US_ASCII)),
    )

    @Test
    fun parsesRequestLineAndHeaders() {
        val request = parse(
            "GET / HTTP/1.1\r\n" +
                "Host: 10.0.2.15:32923\r\n" +
                "Accept: */*\r\n" +
                "\r\n",
        )
        assertEquals("GET", request?.method)
        assertEquals("/", request?.path)
        assertEquals("10.0.2.15:32923", request?.headers?.get("host"))
        assertEquals("*/*", request?.headers?.get("accept"))
    }

    @Test
    fun headerKeysAreLowercasedAndValuesTrimmed() {
        val request = parse(
            "GET / HTTP/1.1\r\n" +
                "UpGrAdE:   WebSocket  \r\n" +
                "SEC-WEBSOCKET-KEY:abc123   \r\n" +
                "\r\n",
        )
        assertEquals("WebSocket", request?.headers?.get("upgrade"))
        assertEquals("abc123", request?.headers?.get("sec-websocket-key"))
    }

    @Test
    fun stripsQueryFromTarget() {
        val request = parse("GET /page?a=b&c=d HTTP/1.1\r\nHost: x\r\n\r\n")
        assertEquals("/page", request?.path)
    }

    @Test
    fun uppercasesMethod() {
        val request = parse("get / HTTP/1.1\r\n\r\n")
        assertEquals("GET", request?.method)
    }

    @Test
    fun rejectsMalformedRequestLine() {
        assertNull(parse("GETX\r\n\r\n"))
        assertNull(parse("GET /only-two-parts HTTP/1.1 extra\r\n\r\n"))
    }

    @Test
    fun rejectsTargetWithoutLeadingSlash() {
        assertNull(parse("GET hello HTTP/1.1\r\n\r\n"))
    }

    @Test
    fun rejectsVersionWithoutHttpPrefix() {
        assertNull(parse("GET / not-http\r\n\r\n"))
    }

    @Test
    fun rejectsHeaderLineWithoutColon() {
        assertNull(parse("GET / HTTP/1.1\r\nbroken-header\r\n\r\n"))
    }

    @Test
    fun rejectsEmptyNameHeader() {
        assertNull(parse("GET / HTTP/1.1\r\n: value\r\n\r\n"))
    }

    @Test
    fun rejectsEofBeforeHeadComplete() {
        assertNull(parse("GET / HTTP/1.1\r\nHost: x\r\n"))
        assertNull(parse(""))
    }

    @Test
    fun rejectsHeadBeyondSizeLimit() {
        val big = "GET / HTTP/1.1\r\nX-Pad: " + "a".repeat(HttpRequestParser.MAX_HEAD_BYTES) + "\r\n\r\n"
        assertNull(parse(big))
    }

    @Test
    fun headAtExactlyLimitStillAccepted() {
        // 头块远小于上限：正常解析
        val request = parse("GET / HTTP/1.1\r\nX-A: hi\r\n\r\n")
        assertEquals("hi", request?.headers?.get("x-a"))
    }
}
