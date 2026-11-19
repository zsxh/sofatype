package com.sofatype.ime.remote

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

class PairingHandshakeLoopbackTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val servers = mutableListOf<RemoteInputServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
    }

    private fun newServer(store: PairingTokenStore): Pair<RemoteInputServer, Int> {
        val server = RemoteInputServer(
            loopback,
            0,
            PairingSession(store, tvName = "回环电视", charLimit = 200),
        ).also { servers.add(it) }
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        return server to port
    }

    private class WsClient(private val socket: Socket) {
        private val input = BufferedInputStream(socket.getInputStream())
        private val output = BufferedOutputStream(socket.getOutputStream())
        private val key = "dGhlIHNhbXBsZSBub25jZQ=="
        private val maskKey = byteArrayOf(0x11, 0x22, 0x33, 0x44)

        fun handshake() {
            output.write(
                ("GET /ws HTTP/1.1\r\nHost: t\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n")
                    .toByteArray(StandardCharsets.US_ASCII),
            )
            output.flush()
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val b = input.read()
                if (b < 0) throw EOFException("握手阶段流结束")
                head.append(b.toChar())
            }
            assertTrue(head.toString(), head.toString().startsWith("HTTP/1.1 101"))
        }

        fun sendText(payload: String) {
            val bytes = payload.toByteArray(StandardCharsets.UTF_8)
            val masked = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor maskKey[i % 4].toInt()).toByte() }
            output.write(0x81)
            output.write(0x80 or bytes.size)
            output.write(maskKey, 0, 4)
            output.write(masked)
            output.flush()
        }

        fun sendPing(payload: String) {
            val bytes = payload.toByteArray(StandardCharsets.US_ASCII)
            val masked = ByteArray(bytes.size) { i -> (bytes[i].toInt() xor maskKey[i % 4].toInt()).toByte() }
            output.write(0x89)
            output.write(0x80 or bytes.size)
            output.write(maskKey, 0, 4)
            output.write(masked)
            output.flush()
        }

        fun readFrame(): WsFrameData =
            WsFrameCodec.readFrom(input, requireMask = false) ?: throw EOFException("服务端已关闭")

        fun readText(): String {
            val frame = readFrame()
            assertEquals(WsOpcode.TEXT, frame.opcode)
            return String(frame.payload, StandardCharsets.UTF_8)
        }

        fun closeCode(): Int {
            val frame = readFrame()
            assertEquals(WsOpcode.CLOSE, frame.opcode)
            return ((frame.payload[0].toInt() and 0xFF) shl 8) or (frame.payload[1].toInt() and 0xFF)
        }

        fun rawRead(): Int = input.read()

        fun closeSocket() {
            socket.close()
        }
    }

    private fun openWs(port: Int): WsClient {
        val socket = Socket(loopback, port)
        socket.soTimeout = 5_000
        val client = WsClient(socket)
        client.handshake()
        return client
    }

    @Test
    fun validTokenHandshakeReturnsConnectedStateAndKeepsConnectionOpen() {
        val store = PairingTokenStore()
        val token = store.issue()
        val (_, port) = newServer(store)
        openWs(port).useClient { client ->
            client.sendText("""{"type":"hello","token":"$token","deviceName":"browser"}""")
            val state = client.readText()
            assertTrue(state, state.contains(""""connected":true"""))
            assertTrue(state, state.contains(""""tvName":"回环电视""""))
            assertTrue(state, state.contains(""""charLimit":200"""))

            client.sendPing("keep-alive")
            val pong = client.readFrame()
            assertEquals(WsOpcode.PONG, pong.opcode)
            assertEquals("keep-alive", String(pong.payload, StandardCharsets.US_ASCII))
            client.closeSocket()
        }
    }

    @Test
    fun replayedTokenGetsRejectedStateThenClose4001() {
        val store = PairingTokenStore()
        val token = store.issue()
        val (_, port) = newServer(store)

        val first = openWs(port)
        first.sendText("""{"type":"hello","token":"$token"}""")
        assertTrue(first.readText().contains(""""connected":true"""))
        first.closeSocket()

        val second = openWs(port)
        second.sendText("""{"type":"hello","token":"$token"}""")
        assertTrue(second.readText().contains(""""connected":false"""))
        assertEquals(PairingSession.CLOSE_POLICY_REJECTED, second.closeCode())
        second.closeSocket()
    }

    @Test
    fun wrongTokenGetsRejectedStateThenClose4001AndSocketCloses() {
        val store = PairingTokenStore()
        store.issue()
        val (_, port) = newServer(store)
        val client = openWs(port)
        client.sendText("""{"type":"hello","token":"guess-me"}""")
        assertTrue(client.readText().contains(""""connected":false"""))
        assertEquals(4001, client.closeCode())

        val deadline = System.currentTimeMillis() + 5_000
        var eof = false
        while (System.currentTimeMillis() < deadline) {
            if (client.rawRead() == -1) {
                eof = true
                break
            }
        }
        assertTrue("被拒后服务端应关闭连接", eof)
        client.closeSocket()
    }

    @Test
    fun directUrlAccessWithoutTokenGetsRejectedStateThenClose4001() {

        val store = PairingTokenStore()
        store.issue()
        val (_, port) = newServer(store)
        val client = openWs(port)
        client.sendText("""{"type":"hello","token":"","deviceName":"browser"}""")
        val state = client.readText()
        assertTrue(state, state.contains(""""type":"state""""))
        assertTrue(state, state.contains(""""connected":false"""))
        assertEquals(PairingSession.CLOSE_POLICY_REJECTED, client.closeCode())
        client.closeSocket()
    }

    private inline fun WsClient.useClient(block: (WsClient) -> Unit) {
        try {
            block(this)
        } finally {
            closeSocket()
        }
    }
}
