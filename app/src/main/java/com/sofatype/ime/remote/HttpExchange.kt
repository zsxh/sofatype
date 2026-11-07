// T13 · HTTP 请求解析与路由。纯 Kotlin/JVM，可被单元测试覆盖。
// 本期骨架口径：GET / 下发占位页（正式 H5 页随 T15）；GET /ws 升级 WebSocket
//（token/hello/state 握手随 T14）；其余 404/405/400。
package com.sofatype.ime.remote

import java.io.InputStream
import java.nio.charset.StandardCharsets

/** 解析后的 HTTP 请求。headers 键统一小写；path 已去掉 query。 */
class HttpRequest(val method: String, val path: String, val headers: Map<String, String>)

object HttpRequestParser {
    /** 请求头总量上限，防恶意超大头部占用内存。 */
    const val MAX_HEAD_BYTES = 8 * 1024

    /**
     * 从流中解析一个请求头块（读到 CRLFCRLF 为止）。
     * 格式非法 / 超限 / 流在头块结束前断开，返回 null（连接层回 400 并断开）。
     */
    fun parse(input: InputStream): HttpRequest? {
        val head = readHead(input) ?: return null
        val lines = String(head, StandardCharsets.US_ASCII).split("\r\n")
        if (lines.isEmpty()) return null
        val parts = lines[0].split(" ")
        if (parts.size != 3) return null
        val method = parts[0]
        val target = parts[1]
        val version = parts[2]
        if (method.isEmpty() || !target.startsWith("/") || !version.startsWith("HTTP/")) return null
        val headers = HashMap<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            if (line.isEmpty()) continue
            val colon = line.indexOf(':')
            if (colon <= 0) return null
            headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
        return HttpRequest(method.uppercase(), target.substringBefore("?"), headers)
    }

    /** 读到 CRLFCRLF；超限或提前 EOF 返回 null。 */
    private fun readHead(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        var b3 = -1
        var b2 = -1
        var b1 = -1
        while (true) {
            val b = input.read()
            if (b < 0) return null
            if (out.size() >= MAX_HEAD_BYTES) return null
            out.write(b)
            // 先判定（b3,b2,b1,b = 末四字节），再移位
            if (b3 == 0x0D && b2 == 0x0A && b1 == 0x0D && b == 0x0A) return out.toByteArray()
            b3 = b2
            b2 = b1
            b1 = b
        }
    }
}

sealed class RouteResult {
    /** 直接回 HTTP 响应（含完整字节，Connection: close）。 */
    class Respond(val bytes: ByteArray) : RouteResult()

    /** 升级为 WebSocket（请求已通过 [WsHandshake.isUpgradeRequest]）。 */
    object UpgradeWebSocket : RouteResult()
}

object HttpRouter {
    const val WS_PATH = "/ws"

    fun route(request: HttpRequest): RouteResult = when {
        request.path == "/" && request.method == "GET" ->
            RouteResult.Respond(
                HttpResponses.build(
                    "200 OK",
                    "text/html; charset=utf-8",
                    PlaceholderPage.HTML.toByteArray(StandardCharsets.UTF_8),
                ),
            )
        request.path == "/" ->
            RouteResult.Respond(textResponse("405 Method Not Allowed", "仅支持 GET"))
        request.path == WS_PATH && request.method == "GET" ->
            if (WsHandshake.isUpgradeRequest(request.headers)) RouteResult.UpgradeWebSocket
            else RouteResult.Respond(textResponse("400 Bad Request", "缺少 WebSocket 升级头"))
        else -> RouteResult.Respond(textResponse("404 Not Found", "未找到资源"))
    }

    private fun textResponse(statusText: String, body: String): ByteArray =
        HttpResponses.build(statusText, "text/plain; charset=utf-8", body.toByteArray(StandardCharsets.UTF_8))
}

object HttpResponses {
    /** 组装 HTTP/1.1 响应字节；固定 Connection: close（一连接一请求，简化骨架生命周期）。 */
    fun build(statusText: String, contentType: String, body: ByteArray): ByteArray {
        val head = "HTTP/1.1 $statusText\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n" +
            "\r\n"
        return head.toByteArray(StandardCharsets.US_ASCII) + body
    }
}

/** T13 占位页：验收用 curl 打开；正式手机端 H5 页随 T15 下发（决策 20：H5 打包进 APK assets）。 */
object PlaceholderPage {
    val HTML: String = """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>sofatype 跨屏输入</title>
        </head>
        <body>
          <h1>sofatype 跨屏输入</h1>
          <p>T13 服务骨架占位页；正式页面随 T15 下发。</p>
          <p>WebSocket 端点：ws://&lt;本机地址&gt;/ws</p>
        </body>
        </html>
    """.trimIndent()
}
