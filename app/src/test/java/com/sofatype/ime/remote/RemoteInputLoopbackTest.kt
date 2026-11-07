// T15 · 业务链路回环集成测试（JVM 真实套接字，127.0.0.1）：
// 会话工厂按连接装配配对会话——hello 成功后 input 全量文本 / clear 送达业务回调、
// 未配对连接的业务消息不下发、主页字节由注入 Provider 提供（决策 20 assets 下发口径）、
// 关闭等待截止保护（T14 评审建议：对端半帧停滞不越过 1s 截止）。
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

    /** T15 装配：会话工厂（每连接独立 PairingSession，业务回调收集）+ 注入主页 Provider。 */
    private class Biz(
        private val bindAddress: InetAddress,
        private val registry: MutableList<RemoteInputServer>,
    ) {
        val inputs = CopyOnWriteArrayList<String>()
        val clears = AtomicInteger(0)
        val commits = CopyOnWriteArrayList<String>()
        val commitResults = CopyOnWriteArrayList<Boolean>()
        val mainPageHits = AtomicInteger(0)

        /** 启动服务；返回 (server, port, token)。 */
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

    /** 最小 WS 客户端（RFC 6455）：握手 + 掩码文本 + 读服务端帧（同 T14 回环测试口径）。 */
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

        /** 发送半帧（只有帧头首字节）后停滞：验证服务端关闭等待不被无限阻塞。 */
        fun sendHalfFrameByte() {
            output.write(0x88) // CLOSE 操作码 + FIN，无后续长度字节
            output.flush()
        }

        fun readText(): String {
            val frame = WsFrameCodec.readFrom(input, requireMask = false) ?: throw EOFException("服务端已关闭")
            assertEquals(WsOpcode.TEXT, frame.opcode)
            return String(frame.payload, StandardCharsets.UTF_8)
        }

        /** 阻塞读到流结束（服务端关 TCP）；带毫秒级耗时返回。 */
        fun readUntilEof(): Long {
            val start = System.currentTimeMillis()
            while (input.read() != -1) {
                // 消费到 EOF
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
            // 连续 input（每次编辑全量文本）→ 回调按序全收
            client.sendText("""{"type":"input","text":"客厅"}""")
            client.sendText("""{"type":"input","text":"客厅的小米盒子"}""")
            client.sendText("""{"type":"clear"}""")
            client.sendText("""{"type":"input","text":"重新来"}""")
            // 连接保持（下一轮交互可正常收发 = None 路径未断连）：PING/PONG 探活
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
        // 连接 A：先发业务消息（未配对）再配对——早到的业务消息不回调
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
        // 第二连接：token 已消费 → connected:false + 4001；其业务消息不回调
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
        // T14 评审建议落实验证：对端发半帧后停滞，服务端关闭等待按截止结束（~1s + 超时容忍）
        val store = PairingTokenStore()
        store.issue()
        val server = RemoteInputServer(
            loopback,
            0,
            wsDelegate = PairingSession(store, tvName = "回环电视"),
        ).also { servers.add(it) }
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        val client = openWs(port)
        client.sendText("""{"type":"hello","token":"wrong"}""") // 被拒 → TextThenClose 路径
        assertTrue(client.readText().contains(""""connected":false"""))
        client.sendHalfFrameByte() // 半帧停滞（旧实现此处无限阻塞）
        val elapsed = client.readUntilEof()
        assertTrue("关闭等待应在截止附近结束（实测 ${elapsed}ms）", elapsed < 3_000)
        client.closeSocket()
    }

    // ---- T16 · commit 上屏指令与 committed 回执（FR-B6/C6，真实套接字全链路） ----

    @Test
    fun commitReachesCallbackAndAckRoundTripsCommitted() {
        val biz = Biz(loopback, servers)
        biz.commitResults.add(true)
        val (_, port, token) = biz.newServer()
        openWs(port).use { client ->
            client.sendText(hello(token))
            assertTrue(client.readText().contains(""""connected":true"""))
            client.sendText("""{"type":"commit","text":"客厅"}""")
            // 处理方 ack(true) → 服务经连接级回发器写 committed 帧（主动回发通道实证）
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
            // 未配对先发 commit：忽略不回调、不回执；随后配对正常、commit 正常分发
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

    private inline fun WsClient.use(block: (WsClient) -> Unit) {
        try {
            block(this)
        } finally {
            closeSocket()
        }
    }
}
