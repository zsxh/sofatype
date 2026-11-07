// T13 · WebSocket 握手（RFC 6455 第 4 节）。纯 Kotlin/JVM，可被单元测试覆盖。
package com.sofatype.ime.remote

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object WsHandshake {
    /** RFC 6455 4.2.1 固定 GUID。 */
    const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    const val REQUIRED_VERSION = "13"

    /** Sec-WebSocket-Accept = base64(SHA-1(clientKey + GUID))。 */
    fun acceptKey(clientKey: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest((clientKey.trim() + GUID).toByteArray(StandardCharsets.US_ASCII))
        return Base64Codec.encode(sha1)
    }

    /** 请求头（键已统一小写）是否构成合法的 WebSocket 升级请求。 */
    fun isUpgradeRequest(headers: Map<String, String>): Boolean =
        headers["upgrade"]?.equals("websocket", ignoreCase = true) == true &&
            !headers["sec-websocket-key"].isNullOrBlank() &&
            headers["sec-websocket-version"]?.trim() == REQUIRED_VERSION

    /** 101 升级响应完整字节（ASCII）。 */
    fun upgradeResponse(clientKey: String): ByteArray =
        ("HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: ${acceptKey(clientKey)}\r\n" +
            "\r\n").toByteArray(StandardCharsets.US_ASCII)
}
