// T13 · WebSocket 帧编解码（RFC 6455）。纯 Kotlin/JVM，可被单元测试覆盖。
// 客户端→服务端帧必须掩码（RFC 6455 5.3），服务端→客户端帧不掩码；
// 负载上限 1 MiB，防恶意大帧耗尽内存（PRD 第 8 章可靠性）。
package com.sofatype.ime.remote

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** WebSocket 操作码（RFC 6455 5.2）。 */
enum class WsOpcode(val code: Int) {
    CONTINUATION(0x0),
    TEXT(0x1),
    BINARY(0x2),
    CLOSE(0x8),
    PING(0x9),
    PONG(0xA),
    ;

    companion object {
        fun fromCode(code: Int): WsOpcode? = entries.firstOrNull { it.code == code }
    }
}

/** 协议错误：携带应回发的 WebSocket 关闭码（RFC 6455 第 7 节）。 */
class WsProtocolException(val closeCode: Int, message: String) : IOException(message)

/** 一帧解码结果。 */
class WsFrameData(val opcode: WsOpcode, val payload: ByteArray, val isFinal: Boolean)

/** 一条完整消息（分片聚合后）。 */
class WsMessage(val opcode: WsOpcode, val payload: ByteArray)

object WsFrameCodec {
    const val MAX_PAYLOAD_BYTES: Int = 1 shl 20 // 1 MiB

    private val controlOpcodes = setOf(WsOpcode.CLOSE, WsOpcode.PING, WsOpcode.PONG)

    /**
     * 编码一帧。[mask] 为 true 时必须提供 4 字节 [maskKey]（客户端方向）；
     * 服务端方向不掩码（RFC 6455 5.1：服务端不得掩码）。
     */
    fun encode(opcode: WsOpcode, payload: ByteArray, mask: Boolean = false, maskKey: ByteArray? = null): ByteArray {
        require(!mask || (maskKey != null && maskKey.size == 4)) { "掩码帧必须提供 4 字节掩码键" }
        val key: ByteArray? = if (mask) maskKey else null
        val header = java.io.ByteArrayOutputStream(14)
        var b0 = 0x80 or opcode.code
        if (mask) b0 = b0 or 0x80
        header.write(b0)
        val len = payload.size
        when {
            len <= 125 -> header.write(if (mask) 0x80 or len else len)
            len <= 0xFFFF -> {
                header.write(if (mask) 0x80 or 126 else 126)
                header.write((len ushr 8) and 0xFF)
                header.write(len and 0xFF)
            }
            else -> {
                header.write(if (mask) 0x80 or 127 else 127)
                val v = len.toLong()
                for (shift in 56 downTo 0 step 8) {
                    header.write(((v ushr shift) and 0xFF).toInt())
                }
            }
        }
        if (mask) header.write(key, 0, 4)
        val body = if (key != null) {
            ByteArray(payload.size) { i ->
                (payload[i].toInt() xor key[i % 4].toInt()).toByte()
            }
        } else {
            payload
        }
        return header.toByteArray() + body
    }

    /**
     * 从流中读取一帧；干净地读到流结尾返回 null。
     * [requireMask] 为 true 时要求客户端掩码位（RFC 6455，未掩码按协议错误关闭 1002）。
     * 帧头截断抛 EOFException；协议违规抛 [WsProtocolException]。
     */
    fun readFrom(input: InputStream, requireMask: Boolean = true): WsFrameData? {
        val b0 = input.read()
        if (b0 < 0) return null
        val b1 = readByteOrEof(input)
        val isFinal = b0 and 0x80 != 0
        val opcode = WsOpcode.fromCode(b0 and 0x0F)
            ?: throw WsProtocolException(1002, "未知操作码 0x${Integer.toHexString(b0 and 0x0F)}")
        val masked = b1 and 0x80 != 0
        if (requireMask && !masked) throw WsProtocolException(1002, "客户端帧未掩码")
        var len = b1 and 0x7F
        when (len) {
            126 -> len = readUInt16(input)
            127 -> len = readLength64(input)
        }
        if (len > MAX_PAYLOAD_BYTES) {
            throw WsProtocolException(1009, "帧负载超限：$len > $MAX_PAYLOAD_BYTES")
        }
        if (opcode in controlOpcodes && (!isFinal || len > 125)) {
            throw WsProtocolException(1002, "控制帧必须不分片且负载 ≤125 字节")
        }
        val maskKey = if (masked) readFully(input, 4) else null
        val payload = readFully(input, len)
        if (maskKey != null) {
            for (i in payload.indices) {
                payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
        }
        return WsFrameData(opcode, payload, isFinal)
    }

    private fun readByteOrEof(input: InputStream): Int {
        val b = input.read()
        if (b < 0) throw EOFException("帧头不完整")
        return b
    }

    private fun readUInt16(input: InputStream): Int {
        val hi = readByteOrEof(input)
        val lo = readByteOrEof(input)
        return (hi shl 8) or lo
    }

    private fun readLength64(input: InputStream): Int {
        var v = 0L
        repeat(8) {
            val b = readByteOrEof(input)
            v = (v shl 8) or b.toLong()
        }
        if (v > MAX_PAYLOAD_BYTES) throw WsProtocolException(1009, "帧负载超限：$v > $MAX_PAYLOAD_BYTES")
        return v.toInt()
    }

    private fun readFully(input: InputStream, n: Int): ByteArray {
        val out = ByteArray(n)
        var offset = 0
        while (offset < n) {
            val read = input.read(out, offset, n - offset)
            if (read < 0) throw EOFException("帧负载不完整（已读 $offset / $n 字节）")
            offset += read
        }
        return out
    }
}

/**
 * 数据帧 → 消息聚合器（RFC 6455 5.4 分片）。
 * 控制帧（CLOSE/PING/PONG）不进入消息流，由连接层直接处理，不打断分片中的消息。
 */
class WsMessageAssembler(private val maxMessageBytes: Int = WsFrameCodec.MAX_PAYLOAD_BYTES) {
    private var messageOpcode: WsOpcode? = null
    private val buffer = java.io.ByteArrayOutputStream()

    /** 输入一帧；聚合完成时返回完整消息，否则返回 null；协议违规抛 [WsProtocolException]。 */
    fun feed(frame: WsFrameData): WsMessage? {
        when (frame.opcode) {
            WsOpcode.TEXT, WsOpcode.BINARY -> {
                if (messageOpcode != null) throw WsProtocolException(1002, "分片未结束时出现新数据帧")
                messageOpcode = frame.opcode
                write(frame.payload)
                return if (frame.isFinal) complete() else null
            }
            WsOpcode.CONTINUATION -> {
                if (messageOpcode == null) throw WsProtocolException(1002, "无起始帧的分片")
                write(frame.payload)
                return if (frame.isFinal) complete() else null
            }
            else -> return null // 控制帧不进消息流
        }
    }

    private fun write(payload: ByteArray) {
        if (buffer.size() + payload.size > maxMessageBytes) {
            throw WsProtocolException(1009, "消息超限：> $maxMessageBytes 字节")
        }
        buffer.write(payload)
    }

    private fun complete(): WsMessage {
        val opcode = messageOpcode ?: throw IllegalStateException("无消息类型")
        val payload = buffer.toByteArray()
        buffer.reset()
        messageOpcode = null
        return WsMessage(opcode, payload)
    }
}
