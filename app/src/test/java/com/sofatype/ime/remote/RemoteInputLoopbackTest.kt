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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class RemoteInputLoopbackTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val servers = mutableListOf<RemoteInputServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
    }

    private class Biz(
        private val bindAddress: InetAddress,
        private val registry: MutableList<RemoteInputServer>,

        private val pairingUrl: String? = null,
    ) {
        val inputs = CopyOnWriteArrayList<String>()
        val clears = AtomicInteger(0)
        val commits = CopyOnWriteArrayList<String>()
        val commitResults = CopyOnWriteArrayList<Boolean>()
        val mainPageHits = AtomicInteger(0)

        fun newServer(): Triple<RemoteInputServer, Int, String> {
            val store = PairingTokenStore()
            val token = store.issue()
            val server = RemoteInputServer(
                bindAddress,
                0,
                wsDelegateFactory = {
                    PairingSession(
                        tokens = store,
                        tvName = "回环电视",
                        onInput = { inputs.add(it) },
                        onClear = { clears.incrementAndGet() },
                        onCommit = { text, ack ->
                            commits.add(text)
                            val ok = commitResults.removeAt(0)
                            ack(ok)
                        },
                        pairingUrlProvider = { pairingUrl },
                    )
                },
                mainPageProvider = {
                    mainPageHits.incrementAndGet()
                    "<html>T15-assets-page</html>".toByteArray(StandardCharsets.UTF_8)
                },
            ).also { registry.add(it) }
            val port = (server.start() as RemoteInputServer.StartResult.Started).port
            return Triple(server, port, token)
        }
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

        fun sendHalfFrameByte() {
            output.write(0x88)
            output.flush()
        }

        fun readText(): String {
            val frame = WsFrameCodec.readFrom(input, requireMask = false) ?: throw EOFException("服务端已关闭")
            assertEquals(WsOpcode.TEXT, frame.opcode)
            return String(frame.payload, StandardCharsets.UTF_8)
        }

        fun readUntilEof(): Long {
            val start = System.currentTimeMillis()
            while (input.read() != -1) {

            }
            return System.currentTimeMillis() - start
        }

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

    private fun hello(token: String) = """{"type":"hello","token":"$token","deviceName":"browser"}"""

    @Test
    fun pairedConnectionDeliversInputAndClearToCallbacks() {
        val biz = Biz(loopback, servers)
        val (_, port, token) = biz.newServer()
        openWs(port).use { client ->
            client.sendText(hello(token))
            assertTrue(client.readText().contains(""""connected":true"""))

            client.sendText("""{"type":"input","text":"客厅"}""")
            client.sendText("""{"type":"input","text":"客厅的小米盒子"}""")
            client.sendText("""{"type":"clear"}""")
            client.sendText("""{"type":"input","text":"重新来"}""")

            val deadline = System.currentTimeMillis() + 3_000
            while (biz.inputs.size < 3 || biz.clears.get() < 1) {
                if (System.currentTimeMillis() > deadline) break
                Thread.sleep(10)
            }
            assertEquals(listOf("客厅", "客厅的小米盒子", "重新来"), biz.inputs.toList())
            assertEquals(1, biz.clears.get())
            client.closeSocket()
        }
    }

    @Test
    fun unpairedConnectionBusinessMessagesAreNotDelivered() {
        val biz = Biz(loopback, servers)
        val (_, port, token) = biz.newServer()

        val a = openWs(port)
        a.sendText("""{"type":"input","text":"早到的注入"}""")
        a.sendText(hello(token))
        assertTrue(a.readText().contains(""""connected":true"""))
        a.sendText("""{"type":"input","text":"配对后合法"}""")
        val deadline = System.currentTimeMillis() + 3_000
        while (biz.inputs.size < 1) {
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(10)
        }
        assertEquals(listOf("配对后合法"), biz.inputs.toList())
        a.closeSocket()
    }

    @Test
    fun secondConnectionCannotPairAfterTokenConsumedAndCannotInject() {
        val biz = Biz(loopback, servers)
        val (_, port, token) = biz.newServer()
        val first = openWs(port)
        first.sendText(hello(token))
        assertTrue(first.readText().contains(""""connected":true"""))

        val second = openWs(port)
        second.sendText("""{"type":"input","text":"第二连接"}""")
        second.sendText(hello(token))
        assertTrue(second.readText().contains(""""connected":false"""))
        assertEquals(0, biz.inputs.size)
        second.closeSocket()
        first.closeSocket()
    }

    @Test
    fun mainPageIsServedFromInjectedProvider() {
        val biz = Biz(loopback, servers)
        val (server, port, _) = biz.newServer()
        Socket(loopback, port).use { socket ->
            socket.soTimeout = 5_000
            val output = BufferedOutputStream(socket.getOutputStream())
            output.write("GET /?token=x HTTP/1.1\r\nHost: t\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.flush()
            val input = BufferedInputStream(socket.getInputStream())
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val b = input.read()
                if (b < 0) throw EOFException("响应头阶段流结束")
                head.append(b.toChar())
            }
            val body = input.readBytes().toString(StandardCharsets.UTF_8)
            assertTrue(head.toString(), head.toString().startsWith("HTTP/1.1 200 OK"))
            assertTrue(head.toString(), head.toString().contains("Content-Type: text/html; charset=utf-8"))
            assertTrue(body, body.contains("T15-assets-page"))
            assertEquals(1, biz.mainPageHits.get())
        }
        server.stop()
    }

    @Test
    fun closeAwaitDeadlineSurvivesHalfFrameStall() {

        val store = PairingTokenStore()
        store.issue()
        val server = RemoteInputServer(
            loopback,
            0,
            wsDelegate = PairingSession(store, tvName = "回环电视"),
        ).also { servers.add(it) }
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        val client = openWs(port)
        client.sendText("""{"type":"hello","token":"wrong"}""")
        assertTrue(client.readText().contains(""""connected":false"""))
        client.sendHalfFrameByte()
        val elapsed = client.readUntilEof()
        assertTrue("关闭等待应在截止附近结束（实测 ${elapsed}ms）", elapsed < 3_000)
        client.closeSocket()
    }

    @Test
    fun commitReachesCallbackAndAckRoundTripsCommitted() {
        val biz = Biz(loopback, servers)
        biz.commitResults.add(true)
        val (_, port, token) = biz.newServer()
        openWs(port).use { client ->
            client.sendText(hello(token))
            assertTrue(client.readText().contains(""""connected":true"""))
            client.sendText("""{"type":"commit","text":"客厅"}""")

            val ack = client.readText()
            assertTrue(ack, ack.contains(""""type":"committed""""))
            assertTrue(ack, ack.contains(""""ok":true"""))
            assertEquals(listOf("客厅"), biz.commits.toList())
        }
    }

    @Test
    fun commitFailureAckCarriesOkFalse() {
        val biz = Biz(loopback, servers)
        biz.commitResults.add(false)
        val (_, port, token) = biz.newServer()
        openWs(port).use { client ->
            client.sendText(hello(token))
            assertTrue(client.readText().contains(""""connected":true"""))
            client.sendText("""{"type":"commit","text":"客厅"}""")
            val ack = client.readText()
            assertTrue(ack, ack.contains(""""type":"committed""""))
            assertTrue(ack, ack.contains(""""ok":false"""))
        }
    }

    @Test
    fun unpairedCommitDoesNotReachCallbackAndConnectionStaysUsable() {
        val biz = Biz(loopback, servers)
        biz.commitResults.add(true)
        val (_, port, token) = biz.newServer()
        openWs(port).use { client ->

            client.sendText("""{"type":"commit","text":"早到的上屏"}""")
            client.sendText(hello(token))
            assertTrue(client.readText().contains(""""connected":true"""))
            client.sendText("""{"type":"commit","text":"配对后合法"}""")
            val deadline = System.currentTimeMillis() + 3_000
            while (biz.commits.size < 1) {
                if (System.currentTimeMillis() > deadline) break
                Thread.sleep(10)
            }
            assertEquals(listOf("配对后合法"), biz.commits.toList())
            assertEquals(1, biz.commits.size)
        }
    }

    @Test
    fun stateCarriesRollingPairingUrlEndToEnd() {

        val biz = Biz(loopback, servers, pairingUrl = "http://10.0.2.15:40000/?token=rolled")
        val (_, port, token) = biz.newServer()
        openWs(port).use { client ->
            client.sendText(hello(token))
            val state = client.readText()
            assertTrue(state, state.contains(""""connected":true"""))
            assertTrue(state, state.contains(""""url":"http://10.0.2.15:40000/?token=rolled""""))
        }
    }

    @Test
    fun disconnectMessageIsAcceptedAndConnectionStaysUsable() {

        val biz = Biz(loopback, servers)
        val (_, port, token) = biz.newServer()
        openWs(port).use { client ->
            client.sendText(hello(token))
            assertTrue(client.readText().contains(""""connected":true"""))
            client.sendText("""{"type":"disconnect","reason":"page-unload"}""")
            client.sendText("""{"type":"input","text":"通知后仍在线"}""")
            val deadline = System.currentTimeMillis() + 3_000
            while (biz.inputs.size < 1) {
                if (System.currentTimeMillis() > deadline) break
                Thread.sleep(10)
            }
            assertEquals(listOf("通知后仍在线"), biz.inputs.toList())
        }
    }

    private inline fun WsClient.use(block: (WsClient) -> Unit) {
        try {
            block(this)
        } finally {
            closeSocket()
        }
    }
}
