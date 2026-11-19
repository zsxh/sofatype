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

class RemoteFullChainIntegrationTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val servers = mutableListOf<RemoteInputServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
    }

    private class ChainBus(private val loopback: InetAddress, var port: Int = 0) {
        val tokens = PairingTokenStore()
        val buffer = RemoteTextBuffer()
        val registry = PairedSessionRegistry()
        val codes = ClientCodeRegistry()
        val mainPageHits = CopyOnWriteArrayList<String>()

        var pairingUrl: String = initialUrl()
        var pairedClientCode: ClientCode? = null
        var pinnedKey: String? = null
        val sessionControls = HashMap<PairingSession, PairedConnection>()
        val commitOkQueue = CopyOnWriteArrayList<Boolean>()

        fun initialUrl(): String = "http://10.0.2.15:$port/?token=${tokens.peek() ?: "none"}"

        fun buildServer(): RemoteInputServer =
            RemoteInputServer(
                loopback,
                port,
                wsDelegateFactory = {
                    lateinit var session: PairingSession
                    session = PairingSession(
                        tokens = tokens,
                        tvName = "回环电视",
                        onInput = { text ->
                            buffer.set(text)
                            codes.touch(pinnedKey, System.currentTimeMillis())
                        },
                        onClear = { buffer.clear() },
                        onCommit = { text, ack ->
                            val ok = commitOkQueue.removeAt(0)
                            val aftermath = RemoteCommitAftermath.of(ok)
                            ack(aftermath.acknowledge)
                            if (aftermath.clearRemoteBuffer) buffer.clear()
                        },
                        onPaired = { _, deviceType, deviceKey ->
                            val connection = sessionControls[session]
                                ?: throw AssertionError("配对回调先于连接挂载（onConnectionAttached 未生效）")
                            val replaced = registry.attach(session, connection)
                            replaced?.let { old ->
                                old.sender.send("""{"type":"${PairingSession.MSG_REPLACED}"}""")
                                old.close(PairingSession.CLOSE_REPLACED)
                            }
                            val token = tokens.issue()
                            pairingUrl = "http://10.0.2.15:$port/?token=$token"
                            pairedClientCode = codes.issue(deviceKey, deviceType, System.currentTimeMillis())
                            pinnedKey = deviceKey
                            codes.pin(deviceKey)
                        },
                        onPairedSessionClosed = {
                            if (registry.detachIfCurrent(session)) {
                                pairedClientCode = null
                                pinnedKey = null
                                codes.unpin()
                                buffer.clear()
                            }
                        },
                        onConnectionAttached = { sender, close ->
                            sessionControls[session] = PairedConnection(sender, close)
                        },
                        pairingUrlProvider = { pairingUrl },
                        clientCodeProvider = { pairedClientCode?.text },
                    )
                    session
                },
                mainPageProvider = {
                    mainPageHits.add("hit")
                    "<html>T91-chain-page</html>".toByteArray(StandardCharsets.UTF_8)
                },
            )
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

    private fun fetchMainPage(port: Int): Pair<String, String> {
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
            return head.toString() to body
        }
    }

    private fun startChain(): Pair<ChainBus, Pair<String, Int>> {
        val bus = ChainBus(loopback)
        val token = bus.tokens.issue()
        val server = bus.buildServer().also { servers.add(it) }
        val started = server.start() as RemoteInputServer.StartResult.Started
        bus.port = started.port
        bus.pairingUrl = "http://10.0.2.15:${started.port}/?token=$token"
        return bus to (token to started.port)
    }

    @Test
    fun `HTTP 主页到 WS 握手到配对到输入缓冲到上屏回执全链`() {
        val (bus, pair) = startChain()
        val (token, port) = pair
        val (head, body) = fetchMainPage(port)
        assertTrue(head, head.startsWith("HTTP/1.1 200 OK"))
        assertTrue(body, body.contains("T91-chain-page"))

        val client = openWs(port)
        client.sendText(
            """{"type":"hello","token":"$token","deviceName":"browser","deviceType":"browser","deviceKey":"phone-a"}""",
        )
        val state = client.readText()
        assertTrue(state, state.contains(""""connected":true"""))
        assertTrue(state, state.contains(""""tvName":"回环电视""""))
        assertTrue(state, state.contains(""""clientCode":"浏览器设备 01""""))
        assertTrue(state, state.contains(""""url":"http://10.0.2.15:$port/?token="""))
        assertTrue("配对后应滚动签发新 token", bus.pairingUrl != "http://10.0.2.15:$port/?token=$token")

        client.sendText("""{"type":"input","text":"客厅"}""")
        client.sendText("""{"type":"input","text":"客厅的小米盒子"}""")
        val deadline = System.currentTimeMillis() + 3_000
        while (bus.buffer.text != "客厅的小米盒子") {
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(10)
        }
        assertEquals("客厅的小米盒子", bus.buffer.text)

        bus.commitOkQueue.add(true)
        client.sendText("""{"type":"commit","text":"客厅的小米盒子"}""")
        val ack = client.readText()
        assertTrue(ack, ack.contains(""""type":"committed""""))
        assertTrue(ack, ack.contains(""""ok":true"""))
        val clearedDeadline = System.currentTimeMillis() + 3_000
        while (bus.buffer.text.isNotEmpty()) {
            if (System.currentTimeMillis() > clearedDeadline) break
            Thread.sleep(10)
        }
        assertEquals("", bus.buffer.text)
        client.closeSocket()
    }

    @Test
    fun `上屏失败回执 ok false 且缓冲保留`() {
        val (bus, pair) = startChain()
        val (token, port) = pair
        val client = openWs(port)
        client.sendText("""{"type":"hello","token":"$token","deviceType":"wechat","deviceKey":"phone-b"}""")
        val state = client.readText()
        assertTrue(state, state.contains(""""connected":true"""))
        assertTrue(state, state.contains(""""clientCode":"微信设备 01""""))

        client.sendText("""{"type":"input","text":"草稿保留"}""")
        val deadline = System.currentTimeMillis() + 3_000
        while (bus.buffer.text.isEmpty()) {
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(10)
        }
        bus.commitOkQueue.add(false)
        client.sendText("""{"type":"commit","text":"草稿保留"}""")
        val ack = client.readText()
        assertTrue(ack, ack.contains(""""ok":false"""))
        Thread.sleep(200)
        assertEquals("失败后缓冲应保留", "草稿保留", bus.buffer.text)
        client.closeSocket()
    }

    @Test
    fun `换设备配对顶替旧连接_旧连接收 replaced 与 4002_缓冲清空`() {
        val (bus, pair) = startChain()
        val (token, port) = pair
        val first = openWs(port)
        first.sendText("""{"type":"hello","token":"$token","deviceType":"android","deviceKey":"phone-a"}""")
        val firstState = first.readText()
        assertTrue(firstState, firstState.contains(""""connected":true"""))
        assertTrue(firstState, firstState.contains(""""clientCode":"安卓设备 01""""))

        first.sendText("""{"type":"input","text":"旧设备草稿"}""")
        val fillDeadline = System.currentTimeMillis() + 3_000
        while (bus.buffer.text.isEmpty()) {
            if (System.currentTimeMillis() > fillDeadline) break
            Thread.sleep(10)
        }
        assertEquals("旧设备草稿", bus.buffer.text)

        val rolledToken = Regex("token=([^\"]+)").find(bus.pairingUrl)?.groupValues?.getOrNull(1)
            ?: throw AssertionError("滚动签发的 token 应出现在配对地址中")
        val second = openWs(port)
        second.sendText("""{"type":"hello","token":"$rolledToken","deviceType":"apple","deviceKey":"phone-c"}""")
        val secondState = second.readText()
        assertTrue(secondState, secondState.contains(""""connected":true"""))
        assertTrue(secondState, secondState.contains(""""clientCode":"苹果设备 02""""))

        assertEquals("""{"type":"${PairingSession.MSG_REPLACED}"}""", first.readText())
        assertEquals(PairingSession.CLOSE_REPLACED, first.closeCode())

        Thread.sleep(300)
        assertEquals("换设备顶替不清缓冲（草稿交接新设备）", "旧设备草稿", bus.buffer.text)
        first.closeSocket()

        second.sendText("""{"type":"input","text":"新设备接续"}""")
        val swapDeadline = System.currentTimeMillis() + 3_000
        while (bus.buffer.text != "新设备接续") {
            if (System.currentTimeMillis() > swapDeadline) break
            Thread.sleep(10)
        }
        assertEquals("新设备接续", bus.buffer.text)
        second.closeSocket()
    }

    @Test
    fun `未配对连接业务消息不落缓冲`() {
        val (bus, pair) = startChain()
        val (token, port) = pair
        val client = openWs(port)
        client.sendText("""{"type":"input","text":"早到的注入"}""")
        client.sendText("""{"type":"hello","token":"$token","deviceType":"browser","deviceKey":"phone-a"}""")
        assertTrue(client.readText().contains(""""connected":true"""))
        client.sendText("""{"type":"input","text":"配对后合法"}""")
        val deadline = System.currentTimeMillis() + 3_000
        while (bus.buffer.text.isEmpty()) {
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(10)
        }
        assertEquals("配对后合法", bus.buffer.text)
        client.closeSocket()
    }
}
