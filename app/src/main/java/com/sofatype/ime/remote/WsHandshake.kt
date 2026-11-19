package com.sofatype.ime.remote

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object WsHandshake {

    const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    const val REQUIRED_VERSION = "13"

    fun acceptKey(clientKey: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
            .digest((clientKey.trim() + GUID).toByteArray(StandardCharsets.US_ASCII))
        return Base64Codec.encode(sha1)
    }

    fun isUpgradeRequest(headers: Map<String, String>): Boolean =
        headers["upgrade"]?.equals("websocket", ignoreCase = true) == true &&
            !headers["sec-websocket-key"].isNullOrBlank() &&
            headers["sec-websocket-version"]?.trim() == REQUIRED_VERSION

    fun upgradeResponse(clientKey: String): ByteArray =
        ("HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Accept: ${acceptKey(clientKey)}\r\n" +
            "\r\n").toByteArray(StandardCharsets.US_ASCII)
}
