// T13 · 服务生命周期集成单元测试（JVM 真实套接字，127.0.0.1 回环）：
// 启停端口生命周期、HTTP 回环、WS 握手+回显+ping/pong+分片+关闭握手、
// 协议错误关闭码、端口随机、重复启动、绑定失败路径。
package com.sofatype.ime.remote

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeoutException

class RemoteInputServerLifecycleTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val servers = mutableListOf<RemoteInputServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
    }

    private fun newServer(port: Int = 0): RemoteInputServer =
        RemoteInputServer(loopback, port).also { servers.add(it) }

    private fun readLine(input: BufferedInputStream): String {
        val out = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("流提前结束")
            if (b == 0x0A) break
            if (b != 0x0D) out.append(b.toChar())
        }
        return out.toString()
    }

    private fun httpGet(port: Int, target: String = "/"): String =
        Socket(loopback, port).use { socket ->
            socket.soTimeout = 5_000
            val output = BufferedOutputStream(socket.getOutputStream())
            output.write("GET $target HTTP/1.1\r\nHost: t\r\n\r\n".toByteArray(StandardCharsets.US_ASCII))
            output.flush()
            val input = BufferedInputStream(socket.getInputStream())
            val status = readLine(input)
            // 响应体按字节读到连接关闭（服务端 Connection: close），整体按 UTF-8 解码
            val body = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(4 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                body.write(chunk, 0, read)
            }
            "$status\n" + body.toString("UTF-8")
        }

    // ---- 生命周期 ----

    @Test
    fun startBindsEphemeralPortAndReportsIt() {
        val server = newServer()
        val result = server.start()
        assertTrue("应启动成功：${(result as? RemoteInputServer.StartResult.Started)?.port}", result is RemoteInputServer.StartResult.Started)
        val port = (result as RemoteInputServer.StartResult.Started).port
        assertTrue("端口应为系统分配的可用端口（>0）", port > 0)
        assertEquals(port, server.port)
        assertTrue(server.isRunning)
    }

    @Test
    fun consecutiveStartsPickDifferentEphemeralPorts() {
        val firstServer = newServer()
        val first = firstServer.start() as RemoteInputServer.StartResult.Started
        firstServer.stop() // 先停再测第二个，互不干扰
        val second = newServer().start() as RemoteInputServer.StartResult.Started
        assertTrue(first.port > 0)
        assertTrue(second.port > 0)
        assertNotEquals(first.port, second.port)
    }

    @Test
    fun stopClosesListenerSoPortIsFree() {
        val server = newServer()
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        // 先跑一次请求，确保 accept 线程已实际阻塞在 accept() 上，再停（覆盖阻塞态 stop 路径）
        assertEquals("HTTP/1.1 200 OK", httpGet(port).lineSequence().first())
        server.stop()
        assertFalse(server.isRunning)
        assertNull(server.port)
        try {
            Socket(loopback, port).use { it.getOutputStream().write(1) }
            throw AssertionError("停止后端口应拒绝连接")
        } catch (_: Exception) {
            // 连接被拒 = 无端口残留
        }
    }

    @Test
    fun stopIsIdempotentAndSafeWithoutStart() {
        val server = newServer()
        server.stop() // 未启动时 stop
        server.start()
        server.stop()
        server.stop() // 重复 stop
        assertFalse(server.isRunning)
    }

    @Test
    fun startWhileRunningFailsWithoutDisturbingExistingService() {
        val server = newServer()
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        val second = server.start()
        assertTrue(second is RemoteInputServer.StartResult.Failed)
        assertEquals("GET / 仍可用", "HTTP/1.1 200 OK", httpGet(port).lineSequence().first())
    }

    @Test
    fun bindingTakenPortReportsFailure() {
        // 先占住一个回环端口，再让服务绑定同一端口 → 应报失败且不抛异常
        val blocker = ServerSocket()
        blocker.bind(java.net.InetSocketAddress(loopback, 0))
        val taken = blocker.localPort
        try {
            val result = newServer(taken).start()
            assertTrue("应绑定失败", result is RemoteInputServer.StartResult.Failed)
        } finally {
            blocker.close()
        }
    }

    // ---- HTTP 回环 ----

    @Test
    fun httpGetRootServesPlaceholderPageAndCloses() {
        val server = newServer()
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        val response = httpGet(port)
        assertTrue(response, response.startsWith("HTTP/1.1 200 OK\n"))
        assertTrue(response, response.contains("sofatype 跨屏输入"))
    }

    @Test
    fun httpGetUnknownPathReturns404() {
        val server = newServer()
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        assertTrue(httpGet(port, "/missing").startsWith("HTTP/1.1 404 Not Found\n"))
    }

    // ---- WS 回环 ----

    private fun openWs(port: Int): Pair<Socket, WsClient> {
        val socket = Socket(loopback, port)
        socket.soTimeout = 5_000
        val client = WsClient(socket)
        client.handshake()
        return socket to client
    }

    private fun await(action: () -> Unit) {
        val deadline = System.currentTimeMillis() + 5_000
        var last: Exception? = null
        while (System.currentTimeMillis() < deadline) {
            try {
                action()
                return
            } catch (e: Exception) {
                last = e
                Thread.sleep(20)
            }
        }
        throw TimeoutException("等待超时").initCause(last) as TimeoutException
    }

    @Test
    fun wsHandshakeCompletesWithCorrectAcceptKey() {
        val server = newServer()
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        Socket(loopback, port).use { socket ->
            socket.soTimeout = 5_000
            val client = WsClient(socket)
            val status = client.handshake()
            assertEquals("101 Switching Protocols", status.substringAfter(" "))
        }
    }

    @Test
    fun wsEchoesTextMessage() {
        val server = newServer()
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        val (_, client) = openWs(port)
        val payload = "sofatype-t13-回环-12345".toByteArray(StandardCharsets.UTF_8)
        client.sendText(payload)
        val frame = client.readFrame()
        assertEquals(WsOpcode.TEXT, frame.opcode)
        assertTrue(frame.payload.contentEquals(payload))
        client.close(1000)
        client.closeSocket()
    }

    @Test
    fun wsRepliesPongToPing() {
        val server = newServer()
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        val (_, client) = openWs(port)
        client.sendControl(WsOpcode.PING, "ping!".toByteArray(StandardCharsets.US_ASCII))
        val frame = client.readFrame()
        assertEquals(WsOpcode.PONG, frame.opcode)
        assertEquals("ping!", String(frame.payload, StandardCharsets.US_ASCII))
        client.close(1000)
        client.closeSocket()
    }

    @Test
    fun wsAssemblesFragmentsBeforeEcho() {
        val server = newServer()
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        val (_, client) = openWs(port)
        client.sendText("sofa-".toByteArray(StandardCharsets.US_ASCII), fin = false)
        client.sendControl(WsOpcode.PING, ByteArray(0)) // 分片间插入控制帧
        client.sendContinuation("type".toByteArray(StandardCharsets.US_ASCII), fin = false)
        client.sendContinuation("-t13".toByteArray(StandardCharsets.US_ASCII), fin = true)
        val pong = client.readFrame() // PING 的 PONG 先回
        assertEquals(WsOpcode.PONG, pong.opcode)
        val echo = client.readFrame()
        assertEquals("sofa-type-t13", String(echo.payload, StandardCharsets.US_ASCII))
        client.close(1000)
        client.closeSocket()
    }

    @Test
    fun wsCloseHandshakeEchoesStatusCode() {
        val server = newServer()
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        val (_, client) = openWs(port)
        client.sendText("x".toByteArray(StandardCharsets.US_ASCII))
        client.readFrame()
        client.sendControl(WsOpcode.CLOSE, byteArrayOf(0x03, 0xE8.toByte())) // 1000
        val reply = client.readFrame()
        assertEquals(WsOpcode.CLOSE, reply.opcode)
        assertEquals(1000, ((reply.payload[0].toInt() and 0xFF) shl 8) or (reply.payload[1].toInt() and 0xFF))
        // 服务端随后关闭连接
        await { assertEquals(-1, client.rawRead()) }
        client.closeSocket()
    }

    @Test
    fun wsInvalidUtf8TextIsClosedWith1007() {
        val server = newServer()
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        val (_, client) = openWs(port)
        client.sendText(byteArrayOf(0xC3.toByte(), 0x28.toByte())) // 非法 UTF-8
        val frame = client.readFrame()
        assertEquals(WsOpcode.CLOSE, frame.opcode)
        assertEquals(1007, ((frame.payload[0].toInt() and 0xFF) shl 8) or (frame.payload[1].toInt() and 0xFF))
        client.closeSocket()
    }

    @Test
    fun wsUnmaskedClientFrameIsClosedWith1002() {
        val server = newServer()
        val port = (server.start() as RemoteInputServer.StartResult.Started).port
        val (_, client) = openWs(port)
        client.sendUnmaskedText("raw".toByteArray(StandardCharsets.US_ASCII))
        val frame = client.readFrame()
        assertEquals(WsOpcode.CLOSE, frame.opcode)
        assertEquals(1002, ((frame.payload[0].toInt() and 0xFF) shl 8) or (frame.payload[1].toInt() and 0xFF))
        client.closeSocket()
    }

    /** 测试内嵌最小 WS 客户端（RFC 6455）：握手 + 掩码发送 + 读服务端帧。 */
    private class WsClient(private val socket: Socket) {
        private val input = BufferedInputStream(socket.getInputStream())
        private val output = BufferedOutputStream(socket.getOutputStream())
        private val key = "dGhlIHNhbXBsZSBub25jZQ==" // RFC 示例键
        private val maskKey = byteArrayOf(0x11, 0x22, 0x33, 0x44)

        fun handshake(): String {
            output.write(
                ("GET /ws HTTP/1.1\r\n" +
                    "Host: t\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\n" +
                    "Sec-WebSocket-Version: 13\r\n\r\n").toByteArray(StandardCharsets.US_ASCII),
            )
            output.flush()
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val b = input.read()
                if (b < 0) throw EOFException("握手阶段流结束")
                head.append(b.toChar())
            }
            val text = head.toString()
            val expected = WsHandshake.acceptKey(key)
            assertTrue("响应应含正确 Accept 键：$text", text.contains(expected))
            return text.lineSequence().first()
        }

        fun sendText(payload: ByteArray, fin: Boolean = true) = send(WsOpcode.TEXT, payload, fin)
        fun sendContinuation(payload: ByteArray, fin: Boolean) = send(WsOpcode.CONTINUATION, payload, fin)
        fun sendControl(opcode: WsOpcode, payload: ByteArray) = send(opcode, payload, fin = true)

        private fun send(opcode: WsOpcode, payload: ByteArray, fin: Boolean) {
            val masked = ByteArray(payload.size) { i ->
                (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
            }
            val b0 = (if (fin) 0x80 else 0) or opcode.code
            output.write(b0)
            output.write(0x80 or payload.size) // 测试负载 ≤125
            output.write(maskKey, 0, 4)
            output.write(masked)
            output.flush()
        }

        /** 发送未掩码文本帧（用于验证服务端按协议错误关闭）。 */
        fun sendUnmaskedText(payload: ByteArray) {
            output.write(0x81)
            output.write(payload.size)
            output.write(payload)
            output.flush()
        }

        fun readFrame(): WsFrameData =
            WsFrameCodec.readFrom(input, requireMask = false)
                ?: throw EOFException("服务端已关闭")

        fun rawRead(): Int = input.read()

        fun close(code: Int) {
            sendControl(WsOpcode.CLOSE, byteArrayOf(((code shr 8) and 0xFF).toByte(), (code and 0xFF).toByte()))
        }

        fun closeSocket() {
            socket.close()
        }
    }
}
