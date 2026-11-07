// T13 · 局域网服务（HTTP + WebSocket 单端口，TCP 服务生命周期）。
// 纯 Kotlin/JVM（java.net / java.io），不依赖 android.*，可被 JVM 单元测试覆盖。
//
// 口径（TASKS T13 / PRD 第 7 章、决策 16、第 8 章可靠性）：
// - 端口随机：默认 requestedPort = 0，由系统分配可用端口；
// - 仅监听传入的局域网 IPv4 地址，不监听 0.0.0.0；
// - 任何单连接异常只影响该连接；stop() 关闭监听与全部连接，退出无端口残留；
// - 全部线程为 daemon，服务异常不阻塞、不拖垮宿主（IME 进程）；
// - 不持久化任何数据（隐私红线）；日志只记事件，不记内容。
// T14 增量：/ws 会话可装配委托（WsSessionDelegate，配对 hello/state 握手）；
// 无委托 = T13 回环口径（骨架缺省，既有回环单测回归基线不变）。
package com.sofatype.ime.remote

import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/** 发起 WS 关闭后等待对端 CLOSE 帧的最长等待（ms，RFC 6455 §7.1.1 服务器关闭握手）。 */
private const val PEER_CLOSE_WAIT_MS = 1_000L

/**
 * /ws 会话委托（T14）：接管文本消息的应答决策。
 * 服务缺省无委托 = T13 骨架回环口径（文本/二进制聚合后原样回显，既有单测回归基线）；
 * 生产由 Manager 装配 [PairingSession]（hello/state 握手）。
 */
interface WsSessionDelegate {
    /** 收到一条完整文本消息（已校验合法 UTF-8）；返回应答动作。 */
    fun onTextMessage(text: String): WsReply
}

/** 文本消息的应答动作（T14）。 */
sealed class WsReply {
    /** 不回发消息，连接保持。 */
    object None : WsReply()

    /** 回发一条文本消息，连接保持（配对成功：state connected=true）。 */
    class Text(val text: String) : WsReply()

    /** 回发一条文本消息后按给定状态码关闭连接（配对被拒：state connected=false + 4001）。 */
    class TextThenClose(val text: String, val closeCode: Int) : WsReply()
}

class RemoteInputServer(
    private val bindAddress: InetAddress,
    /** 0 = 端口随机取系统可用端口（决策 16）。 */
    private val requestedPort: Int = 0,
    /** WS 会话委托；null = T13 回环口径（骨架缺省）。 */
    private val wsDelegate: WsSessionDelegate? = null,
) {
    sealed class StartResult {
        /** 启动成功；port 为实际监听端口。 */
        class Started(val port: Int) : StartResult()

        /** 启动失败；reason 为可直接记录的原因描述。 */
        class Failed(val reason: String) : StartResult()
    }

    private val lock = Any()
    private val connections = CopyOnWriteArrayList<Socket>()

    @Volatile
    private var running = false

    @Volatile
    private var boundPort: Int = -1

    /** 监听套接字引用：stop() 必须显式关闭，解除 accept 阻塞并释放端口（退出无端口残留）。 */
    private var listeningSocket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    val isRunning: Boolean
        get() = synchronized(lock) { running }

    /** 启动成功后的实际监听端口；未启动返回 null。 */
    val port: Int?
        get() = synchronized(lock) { if (running) boundPort else null }

    /** 启动监听；失败只返回 [StartResult.Failed]，不抛异常。重复启动返回失败，不影响已在运行的服务。 */
    fun start(): StartResult = synchronized(lock) {
        if (running) {
            return StartResult.Failed("服务已在运行（端口 $boundPort）")
        }
        val serverSocket = ServerSocket()
        try {
            serverSocket.bind(InetSocketAddress(bindAddress, requestedPort))
        } catch (e: IOException) {
            runCatching { serverSocket.close() }
            return StartResult.Failed("绑定 ${bindAddress.hostAddress}:$requestedPort 失败：${e.message}")
        }
        listeningSocket = serverSocket
        boundPort = serverSocket.localPort
        running = true
        val thread = Thread({ acceptLoop(serverSocket) }, "sofatype-remote-accept")
        thread.isDaemon = true
        acceptThread = thread
        thread.start()
        return StartResult.Started(boundPort)
    }

    /** 停止监听并关闭全部连接；幂等，可重复调用。 */
    fun stop() {
        val socket: ServerSocket?
        val thread: Thread?
        synchronized(lock) {
            running = false
            boundPort = -1
            socket = listeningSocket
            listeningSocket = null
            thread = acceptThread
            acceptThread = null
        }
        // 先关闭监听：close 会解除 accept 阻塞（accept 抛 SocketException 退出循环）并释放端口，
        // 保证 accept 线程已阻塞在 accept() 时 stop() 同样立即释放端口（无残留）
        closeQuietly(socket)
        connections.forEach { closeQuietly(it) }
        connections.clear()
        if (thread != null && thread.isAlive) {
            try {
                thread.join(1_000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun acceptLoop(serverSocket: ServerSocket) {
        while (running) {
            val client = try {
                serverSocket.accept()
            } catch (_: IOException) {
                break // stop() 关闭监听后走此处
            }
            connections.add(client)
            val handler = Thread({ handleConnection(client) }, "sofatype-remote-conn")
            handler.isDaemon = true
            handler.start()
        }
        closeQuietly(serverSocket)
    }

    /** 单连接处理：任何异常只关闭该连接，不影响服务与其余连接（第 8 章可靠性）。 */
    private fun handleConnection(client: Socket) {
        try {
            client.tcpNoDelay = true
            client.use { serve(it) }
        } catch (_: Exception) {
            // 吞掉单连接异常（连接中断 / 协议错误 / 客户端断开），仅关闭该连接
        } finally {
            connections.remove(client)
        }
    }

    private fun serve(socket: Socket) {
        val input = BufferedInputStream(socket.getInputStream())
        val output = socket.getOutputStream()
        val request = HttpRequestParser.parse(input)
        if (request == null) {
            output.write(
                HttpResponses.build(
                    "400 Bad Request",
                    "text/plain; charset=utf-8",
                    "请求格式错误".toByteArray(StandardCharsets.UTF_8),
                ),
            )
            output.flush()
            return
        }
        when (val route = HttpRouter.route(request)) {
            is RouteResult.Respond -> {
                output.write(route.bytes)
                output.flush()
            }
            RouteResult.UpgradeWebSocket -> {
                output.write(WsHandshake.upgradeResponse(request.headers["sec-websocket-key"].orEmpty()))
                output.flush()
                wsLoop(input, output, wsDelegate)
            }
        }
    }

    /**
     * WebSocket 会话循环：[delegate] 为空 = T13 回环口径（文本/二进制分片聚合后原样回显，
     * 骨架缺省与既有单测回归基线）；非空 = 生产会话（T14 [PairingSession] 握手）：
     * 文本消息交委托应答，二进制消息忽略（协议第 7 章全部为 JSON 文本）。
     * PING 回 PONG；CLOSE 回显状态码；协议错误回对应关闭码后断开。
     */
    private fun wsLoop(input: BufferedInputStream, output: java.io.OutputStream, delegate: WsSessionDelegate?) {
        val assembler = WsMessageAssembler()
        try {
            while (true) {
                val frame = WsFrameCodec.readFrom(input) ?: break // 对端关闭
                when (frame.opcode) {
                    WsOpcode.PING -> output.write(WsFrameCodec.encode(WsOpcode.PONG, frame.payload))
                    WsOpcode.PONG -> Unit // 收到 PONG 无动作
                    WsOpcode.CLOSE -> {
                        output.write(WsFrameCodec.encode(WsOpcode.CLOSE, closeReplyPayload(frame.payload)))
                        output.flush()
                        return
                    }
                    else -> assembler.feed(frame)?.let { message ->
                        when {
                            delegate == null ->
                                output.write(WsFrameCodec.encode(message.opcode, validatedPayload(message)))
                            message.opcode == WsOpcode.TEXT -> {
                                val reply = delegate.onTextMessage(validatedText(message))
                                if (reply is WsReply.Text || reply is WsReply.TextThenClose) {
                                    output.write(
                                        WsFrameCodec.encode(WsOpcode.TEXT, replyText(reply).toByteArray(StandardCharsets.UTF_8)),
                                    )
                                }
                                if (reply is WsReply.TextThenClose) {
                                    output.write(WsFrameCodec.encode(WsOpcode.CLOSE, closeCodeBytes(reply.closeCode)))
                                    output.flush()
                                    // RFC 6455 §7.1.1：发起关闭方应等待对端 CLOSE 帧再断 TCP；
                                    // 立即断开会让浏览器端关闭握手被中断（页面 loading 不结束）。
                                    awaitPeerClose(input)
                                    return
                                }
                            }
                            // 配对会话忽略二进制消息（协议第 7 章无二进制形态）
                            else -> Unit
                        }
                    }
                }
                output.flush()
            }
        } catch (e: WsProtocolException) {
            // 协议错误：回发对应关闭码后断开（best-effort，写失败不影响进程）
            runCatching {
                output.write(WsFrameCodec.encode(WsOpcode.CLOSE, closeCodeBytes(e.closeCode)))
                output.flush()
            }
        }
    }

    /** 取应答动作携带的文本（Text / TextThenClose）。 */
    private fun replyText(reply: WsReply): String = when (reply) {
        is WsReply.Text -> reply.text
        is WsReply.TextThenClose -> reply.text
        WsReply.None -> ""
    }

    /**
     * 发起关闭后短暂等待对端 CLOSE（RFC 6455 §7.1.1 服务器关闭握手）。
     * 非阻塞轮询（accepted 套接字保持默认阻塞读，不能设全局 soTimeout，否则会话空闲读会超时断连）；
     * 超时 [PEER_CLOSE_WAIT_MS] 或对端断开即返回，线程随后正常关闭套接字。
     */
    private fun awaitPeerClose(input: BufferedInputStream) {
        val deadline = System.currentTimeMillis() + PEER_CLOSE_WAIT_MS
        try {
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    // 有对端数据可读：读到 CLOSE 帧（正常关闭握手回执）或对端 EOF 即结束
                    val frame = WsFrameCodec.readFrom(input) ?: return
                    if (frame.opcode == WsOpcode.CLOSE) return
                    // 关闭等待期收到其他帧：忽略继续等（对端未按序处理的兜底）
                    continue
                }
                Thread.sleep(10)
            }
        } catch (_: Exception) {
            // 读异常（对端已断开 / 中断）：直接结束等待
        }
    }

    /** 文本消息必须是合法 UTF-8（RFC 6455），否则关闭 1007；返回解码文本。 */
    private fun validatedText(message: WsMessage): String {
        if (!isValidUtf8(message.payload)) {
            throw WsProtocolException(1007, "文本消息不是合法 UTF-8")
        }
        return String(message.payload, StandardCharsets.UTF_8)
    }

    /** 文本消息必须是合法 UTF-8（RFC 6455），否则关闭 1007；二进制原样。 */
    private fun validatedPayload(message: WsMessage): ByteArray {
        if (message.opcode == WsOpcode.TEXT && !isValidUtf8(message.payload)) {
            throw WsProtocolException(1007, "文本消息不是合法 UTF-8")
        }
        return message.payload
    }

    /** 处理对端 CLOSE 帧负载：空 → 空回复；1 字节 → 协议错误；否则回显状态码（丢弃 reason）。 */
    private fun closeReplyPayload(payload: ByteArray): ByteArray {
        if (payload.isEmpty()) return ByteArray(0)
        if (payload.size == 1) throw WsProtocolException(1002, "CLOSE 帧负载长度非法（1 字节）")
        val code = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        if (code < 1000 || code > 4999 || code == 1005 || code == 1006) {
            throw WsProtocolException(1002, "CLOSE 状态码非法：$code")
        }
        return byteArrayOf(payload[0], payload[1])
    }

    private fun closeCodeBytes(code: Int): ByteArray =
        byteArrayOf(((code ushr 8) and 0xFF).toByte(), (code and 0xFF).toByte())

    private fun isValidUtf8(bytes: ByteArray): Boolean = try {
        StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }

    private fun closeQuietly(target: AutoCloseable?) {
        if (target != null) runCatching { target.close() }
    }
}
