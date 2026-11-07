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
// T15 增量：会话工厂（每连接独立委托实例，业务消息只分发给已配对连接）；
// GET / 主页字节由注入的 mainPageProvider 提供（决策 20：APK assets 内 H5 页，默认占位页）；
// awaitPeerClose 等待期加临时 soTimeout（T14 评审建议：半帧停滞不得越过 1s 截止）。
// T16 增量（FR-B6 commit 回执）：每连接单线程写通道 ConnectionWriter——全部帧写（读线程应答 +
// 任意线程回执）投递到 writer 线程串行执行：帧不交错，且网络 IO 恒离主线程（StrictMode 主线程
// 网络 IO 会使 IME 进程崩溃，见 issue T16 坑 1）；attachSender 提供连接级主动回发通道。
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

/**
 * 发起 WS 关闭后等待对端 CLOSE 帧的最长等待（ms，RFC 6455 §7.1.1 服务器关闭握手）。
 */
private const val PEER_CLOSE_WAIT_MS = 1_000L

/**
 * 主动回发单帧写的最长等待（ms，ConnectionWriter.writeTextMessage 同步等待上限）：
 * writer 线程停滞（对端不读、连接僵死）时回发方按超时放弃，不无限阻塞调用线程。
 */
private const val WRITE_ACK_TIMEOUT_MS = 2_000L

/**
 * 关闭等待期单次阻塞读的套接字读超时（ms，T14 评审建议落实）：
 * 对端发半帧后停滞时，阻塞读不再无限等待——单次读超时即结束整个等待期，截止时间不被越过。
 */
private const val PEER_CLOSE_READ_TIMEOUT_MS = 100

/**
 * /ws 会话委托（T14）：接管文本消息的应答决策。
 * 服务缺省无委托 = T13 骨架回环口径（文本/二进制聚合后原样回显，既有单测回归基线）；
 * 生产由 Manager 装配 [PairingSession]（hello/state 握手）。
 */
interface WsSessionDelegate {
    /** 收到一条完整文本消息（已校验合法 UTF-8）；返回应答动作。 */
    fun onTextMessage(text: String): WsReply

    /**
     * 会话主动回发通道注入（T16 · FR-B6 commit 回执）：WS 升级完成后服务把本连接的
     * 回发器交给委托；委托保存后可在任意线程回发消息（如 commit 处理完成后的
     * committed 回执）。默认空实现：T13/T14 既有委托与测试基线不受影响。
     */
    fun attachSender(sender: WsMessageSender) {}
}

/**
 * 会话主动回发器（T16）：向本连接对端写一条文本帧（与请求-应答式 [WsReply] 相对）。
 * 线程安全：实现须与该连接的帧写入互斥（防帧交错）；连接已断返回 false。
 */
fun interface WsMessageSender {
    /** 写一条文本消息帧；写入成功返回 true，连接断开 / 写失败返回 false。 */
    fun send(text: String): Boolean
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
    /** WS 会话委托；null = T13 回环口径（骨架缺省）。与 [wsDelegateFactory] 二选一。 */
    private val wsDelegate: WsSessionDelegate? = null,
    /**
     * 会话工厂（T15）：每个 WS 连接创建独立委托实例（持每连接配对态，业务消息只分发给
     * 已配对连接）。非空时优先生效；null = 用共享 [wsDelegate]（T13/T14 既有口径与测试基线）。
     */
    private val wsDelegateFactory: (() -> WsSessionDelegate)? = null,
    /**
     * 主页字节提供者（T15 · 决策 20）：GET / 下发页面由此取得（生产 = APK assets 内 H5 页，
     * 由 Manager 注入 assets 读取）。默认 = T14 配对占位页（JVM 测试与缺省回退口径）。
     */
    private val mainPageProvider: () -> ByteArray = { PlaceholderPage.HTML.toByteArray(StandardCharsets.UTF_8) },
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
        // 会话委托按连接解析（T15）：工厂非空 = 每连接独立实例（配对态互不串扰）；
        // 否则回退共享委托（T13/T14 口径）。
        val delegate = wsDelegateFactory?.invoke() ?: wsDelegate
        when (val route = HttpRouter.route(request, mainPageProvider)) {
            is RouteResult.Respond -> {
                output.write(route.bytes)
                output.flush()
            }
            RouteResult.UpgradeWebSocket -> {
                output.write(WsHandshake.upgradeResponse(request.headers["sec-websocket-key"].orEmpty()))
                output.flush()
                // T16：每连接写通道（单线程 writer，帧写串行化 + 网络 IO 不落主线程），
                // 并把主动回发器交给委托（commit 回执经 writer 线程回发）
                val writer = ConnectionWriter(output)
                try {
                    delegate?.attachSender { text -> writer.writeTextMessage(text) }
                    wsLoop(input, output, delegate, socket, writer)
                } finally {
                    writer.shutdown()
                }
            }
        }
    }

    /**
     * 单连接帧写通道（T16）：该连接全部 WS 帧写（读线程应答 + 主线程主动回执）统一投递到
     * 单线程 writer 执行——一、帧级串行化（并发写不交错）；二、实际网络 IO 恒在 writer 线程，
     * 主线程回执不再触碰 socket（debug 构建 StrictMode NetworkOnMainThreadException 实测崩溃，
     * 见 issue T16 坑 1；可靠性红线：IME 崩溃不得阻塞宿主）。
     * writeRaw / writeTextMessage 均同步等待写完成后返回：调用方时序不变
     *（TextThenClose 路径「写 CLOSE 再等对端回执」、commit 路径「回执 flush 完再 stop 关连接」均依赖）。
     */
    private class ConnectionWriter(private val output: java.io.OutputStream) {
        private val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "sofatype-remote-writer").apply { isDaemon = true }
        }

        /** 写原始字节（已编码帧），写完成后返回；写失败以异常传播（与直写 socket 行为等价）。 */
        fun writeRaw(bytes: ByteArray) {
            executor.submit {
                output.write(bytes)
                output.flush()
            }.get()
        }

        /** 写一条文本消息帧（主动回发通道）；写成功返回 true，失败 / 超时返回 false，不抛异常。 */
        fun writeTextMessage(text: String): Boolean = try {
            executor.submit {
                output.write(WsFrameCodec.encode(WsOpcode.TEXT, text.toByteArray(StandardCharsets.UTF_8)))
                output.flush()
            }.get(WRITE_ACK_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            true
        } catch (_: Exception) {
            false
        }

        /** 关闭写通道（连接结束时调用）：丢弃排队写，writer 线程随 daemon 退出，不留残留线程。 */
        fun shutdown() {
            executor.shutdownNow()
        }
    }

    /**
     * WebSocket 会话循环：[delegate] 为空 = T13 回环口径（文本/二进制分片聚合后原样回显，
     * 骨架缺省与既有单测回归基线）；非空 = 生产会话（T14 [PairingSession] 握手）：
     * 文本消息交委托应答，二进制消息忽略（协议第 7 章全部为 JSON 文本）。
     * PING 回 PONG；CLOSE 回显状态码；协议错误回对应关闭码后断开。
     * 帧写一律经 [writer]（T16：与主动回发互斥）。
     */
    private fun wsLoop(
        input: BufferedInputStream,
        output: java.io.OutputStream,
        delegate: WsSessionDelegate?,
        socket: Socket,
        writer: ConnectionWriter,
    ) {
        val assembler = WsMessageAssembler()
        try {
            while (true) {
                val frame = WsFrameCodec.readFrom(input) ?: break // 对端关闭
                when (frame.opcode) {
                    WsOpcode.PING -> writer.writeRaw(WsFrameCodec.encode(WsOpcode.PONG, frame.payload))
                    WsOpcode.PONG -> Unit // 收到 PONG 无动作
                    WsOpcode.CLOSE -> {
                        writer.writeRaw(WsFrameCodec.encode(WsOpcode.CLOSE, closeReplyPayload(frame.payload)))
                        return
                    }
                    else -> assembler.feed(frame)?.let { message ->
                        when {
                            delegate == null ->
                                writer.writeRaw(WsFrameCodec.encode(message.opcode, validatedPayload(message)))
                            message.opcode == WsOpcode.TEXT -> {
                                val reply = delegate.onTextMessage(validatedText(message))
                                if (reply is WsReply.Text || reply is WsReply.TextThenClose) {
                                    writer.writeRaw(
                                        WsFrameCodec.encode(
                                            WsOpcode.TEXT,
                                            replyText(reply).toByteArray(StandardCharsets.UTF_8),
                                        ),
                                    )
                                }
                                if (reply is WsReply.TextThenClose) {
                                    writer.writeRaw(WsFrameCodec.encode(WsOpcode.CLOSE, closeCodeBytes(reply.closeCode)))
                                    // RFC 6455 §7.1.1：发起关闭方应等待对端 CLOSE 帧再断 TCP；
                                    // 立即断开会让浏览器端关闭握手被中断（页面 loading 不结束）。
                                    awaitPeerClose(input, socket)
                                    return
                                }
                            }
                            // 配对会话忽略二进制消息（协议第 7 章无二进制形态）
                            else -> Unit
                        }
                    }
                }
            }
        } catch (e: WsProtocolException) {
            // 协议错误：回发对应关闭码后断开（best-effort，写失败不影响进程）
            runCatching {
                writer.writeRaw(WsFrameCodec.encode(WsOpcode.CLOSE, closeCodeBytes(e.closeCode)))
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
     * 非阻塞轮询 + 等待期临时 soTimeout（T14 评审建议落实）：会话常规读不设超时（空闲会话
     * 不误断），仅此等待期把单次阻塞读限制在 [PEER_CLOSE_READ_TIMEOUT_MS] 内——对端发半帧后
     * 停滞时读抛 SocketTimeoutException，等待立即结束，截止 [PEER_CLOSE_WAIT_MS] 不被越过。
     */
    private fun awaitPeerClose(input: BufferedInputStream, socket: Socket) {
        val deadline = System.currentTimeMillis() + PEER_CLOSE_WAIT_MS
        val previousTimeout = runCatching { socket.soTimeout }.getOrDefault(0)
        runCatching { socket.soTimeout = PEER_CLOSE_READ_TIMEOUT_MS }
        try {
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {
                    // 有对端数据可读：读到 CLOSE 帧（正常关闭握手回执）或对端 EOF 即结束；
                    // 读异常（半帧停滞读超时 / 对端已断开 / 中断）同样直接结束等待
                    val frame = try {
                        WsFrameCodec.readFrom(input)
                    } catch (_: java.io.IOException) {
                        return
                    } ?: return
                    if (frame.opcode == WsOpcode.CLOSE) return
                    // 关闭等待期收到其他帧：忽略继续等（对端未按序处理的兜底）
                    continue
                }
                Thread.sleep(10)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            runCatching { socket.soTimeout = previousTimeout }
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
