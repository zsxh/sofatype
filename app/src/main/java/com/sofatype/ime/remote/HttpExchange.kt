package com.sofatype.ime.remote

import java.io.InputStream
import java.nio.charset.StandardCharsets

class HttpRequest(val method: String, val path: String, val headers: Map<String, String>)

object HttpRequestParser {

    const val MAX_HEAD_BYTES = 8 * 1024

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

            if (b3 == 0x0D && b2 == 0x0A && b1 == 0x0D && b == 0x0A) return out.toByteArray()
            b3 = b2
            b2 = b1
            b1 = b
        }
    }
}

sealed class RouteResult {

    class Respond(val bytes: ByteArray) : RouteResult()

    object UpgradeWebSocket : RouteResult()
}

object HttpRouter {
    const val WS_PATH = "/ws"

    fun route(
        request: HttpRequest,
        mainPageProvider: () -> ByteArray = { PlaceholderPage.HTML.toByteArray(StandardCharsets.UTF_8) },
    ): RouteResult = when {
        request.path == "/" && request.method == "GET" ->
            RouteResult.Respond(
                HttpResponses.build(
                    "200 OK",
                    "text/html; charset=utf-8",
                    mainPageProvider(),
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

    fun build(statusText: String, contentType: String, body: ByteArray): ByteArray {
        val head = "HTTP/1.1 $statusText\r\n" +
            "Content-Type: $contentType\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Connection: close\r\n" +
            "\r\n"
        return head.toByteArray(StandardCharsets.US_ASCII) + body
    }
}

object PlaceholderPage {
    val HTML: String = """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>sofatype 跨屏输入</title>
          <style>
            body { margin: 0; min-height: 100vh; display: flex; flex-direction: column;
                   align-items: center; justify-content: center; background: #06080C;
                   color: #F2F6FB; font-family: system-ui, sans-serif; }
            h1 { font-size: 20px; margin: 0 0 16px; }
            #status { font-size: 16px; color: #7DD3FC; margin: 0 0 8px; }
            #meta { font-size: 13px; color: #7F848D; margin: 0; }
          </style>
        </head>
        <body>
          <h1>sofatype 跨屏输入</h1>
          <p id="status">正在连接…</p>
          <p id="meta">T14 配对占位页；正式页面随 T15 下发。</p>
          <script>
            (function () {
              var statusEl = document.getElementById('status');
              var metaEl = document.getElementById('meta');
              var gotState = false; // 收到 state 后 onclose 不再覆盖配对结论文案
              var token = new URLSearchParams(location.search).get('token') || '';
              var ws = new WebSocket((location.protocol === 'https:' ? 'wss://' : 'ws://') + location.host + '/ws');
              ws.onopen = function () {
                ws.send(JSON.stringify({ type: 'hello', token: token, deviceName: 'browser' }));
              };
              ws.onmessage = function (ev) {
                var msg;
                try { msg = JSON.parse(ev.data); } catch (e) { return; }
                if (!msg || msg.type !== 'state') return;
                gotState = true;
                if (msg.connected) {
                  statusEl.textContent = '已连接 · ' + msg.tvName;
                  metaEl.textContent = '字数上限 ' + msg.charLimit + ' 字（T14 配对占位页）';
                } else {
                  statusEl.textContent = '配对失败：token 无效或已使用，请重新扫码';
                  ws.close();
                }
              };
              ws.onclose = function (ev) {
                if (!gotState) {
                  statusEl.textContent = '连接已关闭（' + ev.code + '）';
                }
              };
              ws.onerror = function () {
                statusEl.textContent = '连接失败，请检查与电视在同一 Wi-Fi 后重新扫码';
              };
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}
