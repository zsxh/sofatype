package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.nio.charset.StandardCharsets

class WsFrameCodecTest {

    private fun decode(bytes: ByteArray, requireMask: Boolean = true): WsFrameData? =
        WsFrameCodec.readFrom(ByteArrayInputStream(bytes), requireMask)

    private fun failCode(block: () -> Unit): Int {
        try {
            block()
        } catch (e: WsProtocolException) {
            return e.closeCode
        }
        throw AssertionError("应抛出 WsProtocolException")
    }

    @Test
    fun encodesSmallTextFrameUnmasked() {
        val bytes = WsFrameCodec.encode(WsOpcode.TEXT, "Hi".toByteArray(StandardCharsets.US_ASCII))
        assertTrue(
            "实际 = ${bytes.joinToString(" ") { "0x%02X".format(it) }}",
            bytes.contentEquals(
                byteArrayOf(
                    0x81.toByte(),
                    0x02,
                    0x48,
                    0x69,
                ),
            ),
        )
    }

    @Test
    fun encodesEmptyPingFrame() {
        val bytes = WsFrameCodec.encode(WsOpcode.PING, ByteArray(0))
        assertTrue(bytes.contentEquals(byteArrayOf(0x89.toByte(), 0x00)))
    }

    @Test
    fun encodesTwoByteLengthForPayloadOf126() {
        val bytes = WsFrameCodec.encode(WsOpcode.BINARY, ByteArray(126))
        assertEquals(0x7E, bytes[1].toInt() and 0xFF)
        assertEquals(0x00, bytes[2].toInt())
        assertEquals(0x7E, bytes[3].toInt())
        assertEquals(4 + 126, bytes.size)
    }

    @Test
    fun encodesTwoByteLengthAtUpperBoundary() {
        val bytes = WsFrameCodec.encode(WsOpcode.BINARY, ByteArray(0xFFFF))
        assertEquals(0x7E, bytes[1].toInt() and 0xFF)
        assertEquals(0xFF, bytes[2].toInt() and 0xFF)
        assertEquals(0xFF, bytes[3].toInt() and 0xFF)
    }

    @Test
    fun encodesEightByteLengthForPayloadAbove65535() {
        val bytes = WsFrameCodec.encode(WsOpcode.BINARY, ByteArray(65_536))
        assertEquals(0x7F, bytes[1].toInt() and 0xFF)

        assertEquals(0x01, bytes[7].toInt() and 0xFF)
        for (i in intArrayOf(2, 3, 4, 5, 6, 8, 9)) {
            assertEquals("bytes[$i]", 0, bytes[i].toInt() and 0xFF)
        }
    }

    @Test
    fun maskedEncodingSetsMaskBitAndWritesKey() {
        val key = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val bytes = WsFrameCodec.encode(WsOpcode.TEXT, "Hi".toByteArray(StandardCharsets.US_ASCII), mask = true, maskKey = key)
        assertEquals(0x80, bytes[0].toInt() and 0x80)
        assertEquals(0x82, bytes[1].toInt() and 0xFF)
        assertTrue(bytes.copyOfRange(2, 6).contentEquals(key))
    }

    @Test
    fun decodesMaskedClientFrameWithHandComputedBytes() {

        val frame = decode(byteArrayOf(0x81.toByte(), 0x82.toByte(), 0x01, 0x02, 0x03, 0x04, 0x40, 0x40))
        assertEquals(WsOpcode.TEXT, frame?.opcode)
        assertTrue(frame?.isFinal == true)
        assertEquals("AB", String(frame!!.payload, StandardCharsets.US_ASCII))
    }

    @Test
    fun decodeRoundTripsMaskedPayload() {
        val payload = ByteArray(300) { (it % 251).toByte() }
        val key = byteArrayOf(0x2A, 0x0F, 0x11, 0x7E)
        val encoded = WsFrameCodec.encode(WsOpcode.BINARY, payload, mask = true, maskKey = key)
        val decoded = decode(encoded)
        assertEquals(WsOpcode.BINARY, decoded?.opcode)
        assertTrue(decoded!!.payload.contentEquals(payload))
    }

    @Test
    fun cleanEofAtFrameBoundaryReturnsNull() {
        assertNull(decode(ByteArray(0)))
    }

    @Test
    fun truncatedHeaderThrowsEof() {
        try {
            decode(byteArrayOf(0x81.toByte()))
        } catch (e: EOFException) {
            return
        }
        throw AssertionError("应抛出 EOFException")
    }

    @Test
    fun truncatedPayloadThrowsEof() {
        try {

            decode(byteArrayOf(0x81.toByte(), 0x85.toByte(), 0x01, 0x02, 0x03, 0x04, 0x41))
        } catch (e: EOFException) {
            return
        }
        throw AssertionError("应抛出 EOFException")
    }

    @Test
    fun unmaskedClientFrameIsProtocolError1002() {
        assertEquals(1002, failCode { decode(byteArrayOf(0x81.toByte(), 0x02, 0x48, 0x69)) })
    }

    @Test
    fun unknownOpcodeIsProtocolError1002() {
        val key = byteArrayOf(0, 0, 0, 0)
        assertEquals(
            1002,
            failCode { decode(WsFrameCodec.encode(WsOpcode.TEXT, ByteArray(0), mask = true, maskKey = key).also { it[0] = (0x80 or 0x3).toByte() }) },
        )
    }

    @Test
    fun oversizedPayloadIsRejectedWith1009() {
        val key = byteArrayOf(0, 0, 0, 0)
        val big = ByteArray(WsFrameCodec.MAX_PAYLOAD_BYTES + 1)
        assertEquals(
            1009,
            failCode { decode(WsFrameCodec.encode(WsOpcode.BINARY, big, mask = true, maskKey = key)) },
        )
    }

    @Test
    fun fragmentedControlFrameIsProtocolError() {
        val key = byteArrayOf(0, 0, 0, 0)
        val encoded = WsFrameCodec.encode(WsOpcode.PING, ByteArray(0), mask = true, maskKey = key)
        encoded[0] = 0x09
        assertEquals(1002, failCode { decode(encoded) })
    }

    @Test
    fun oversizeControlPayloadIsProtocolError() {
        val key = byteArrayOf(0, 0, 0, 0)
        assertEquals(
            1002,
            failCode { decode(WsFrameCodec.encode(WsOpcode.PING, ByteArray(126), mask = true, maskKey = key)) },
        )
    }

    @Test
    fun singleFinalTextFrameCompletesImmediately() {
        val assembler = WsMessageAssembler()
        val frame = WsFrameData(WsOpcode.TEXT, "hello".toByteArray(StandardCharsets.UTF_8), isFinal = true)
        val message = assembler.feed(frame)
        assertEquals("hello", String(message!!.payload, StandardCharsets.UTF_8))
        assertEquals(WsOpcode.TEXT, message.opcode)
    }

    @Test
    fun fragmentsAssembleInOrder() {
        val assembler = WsMessageAssembler()
        assertNull(assembler.feed(WsFrameData(WsOpcode.TEXT, "frag-".toByteArray(), isFinal = false)))
        assertNull(assembler.feed(WsFrameData(WsOpcode.CONTINUATION, "men".toByteArray(), isFinal = false)))
        val message = assembler.feed(WsFrameData(WsOpcode.CONTINUATION, "t-ok".toByteArray(), isFinal = true))
        assertEquals("frag-ment-ok", String(message!!.payload, StandardCharsets.US_ASCII))
    }

    @Test
    fun controlFramesDoNotDisturbFragmentAssembly() {
        val assembler = WsMessageAssembler()
        assertNull(assembler.feed(WsFrameData(WsOpcode.TEXT, "abc".toByteArray(), isFinal = false)))
        assertNull(assembler.feed(WsFrameData(WsOpcode.PING, "p".toByteArray(), isFinal = true)))
        assertNull(assembler.feed(WsFrameData(WsOpcode.PONG, ByteArray(0), isFinal = true)))
        val message = assembler.feed(WsFrameData(WsOpcode.CONTINUATION, "def".toByteArray(), isFinal = true))
        assertEquals("abcdef", String(message!!.payload, StandardCharsets.US_ASCII))
    }

    @Test
    fun newDataFrameDuringFragmentationIsProtocolError() {
        val assembler = WsMessageAssembler()
        assembler.feed(WsFrameData(WsOpcode.TEXT, "a".toByteArray(), isFinal = false))
        assertEquals(
            1002,
            failCode { assembler.feed(WsFrameData(WsOpcode.TEXT, "b".toByteArray(), isFinal = true)) },
        )
    }

    @Test
    fun continuationWithoutStartIsProtocolError() {
        val assembler = WsMessageAssembler()
        assertEquals(
            1002,
            failCode { assembler.feed(WsFrameData(WsOpcode.CONTINUATION, "x".toByteArray(), isFinal = true)) },
        )
    }

    @Test
    fun messageBeyondLimitIsRejectedWith1009() {
        val half = ByteArray(WsFrameCodec.MAX_PAYLOAD_BYTES / 2 + 10)
        val assembler = WsMessageAssembler(maxMessageBytes = WsFrameCodec.MAX_PAYLOAD_BYTES)
        assertNull(assembler.feed(WsFrameData(WsOpcode.BINARY, half, isFinal = false)))
        assertEquals(1009, failCode { assembler.feed(WsFrameData(WsOpcode.CONTINUATION, half, isFinal = true)) })
    }
}
