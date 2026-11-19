package com.sofatype.ime.remote

import java.io.BufferedInputStream
import java.io.IOException
import java.net.BindException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

private const val PEER_CLOSE_WAIT_MS = 1_000L

private const val WRITE_ACK_TIMEOUT_MS = 2_000L

private const val PEER_CLOSE_READ_TIMEOUT_MS = 100

interface WsSessionDelegate {

    fun onTextMessage(text: String): WsReply

    fun attachConnection(sender: WsMessageSender, close: (Int) -> Unit) {}

    fun onSessionClosed() {}
}

fun interface WsMessageSender {

    fun send(text: String): Boolean
}

sealed class WsReply {

    object None : WsReply()

    class Text(val text: String) : WsReply()

    class TextThenClose(val text: String, val closeCode: Int) : WsReply()
}

class RemoteInputServer(
    private val bindAddress: InetAddress,

    private val requestedPort: Int = 0,

    private val wsDelegate: WsSessionDelegate? = null,

    private val wsDelegateFactory: (() -> WsSessionDelegate)? = null,

    private val mainPageProvider: () -> ByteArray = { PlaceholderPage.HTML.toByteArray(StandardCharsets.UTF_8) },
) {
    sealed class StartResult {

        class Started(val port: Int) : StartResult()

        class Failed(val reason: String, val portInUse: Boolean = false) : StartResult()
    }

    private val lock = Any()
    private val connections = CopyOnWriteArrayList<Socket>()

    @Volatile
    private var running = false

    @Volatile
    private var boundPort: Int = -1

    private var listeningSocket: ServerSocket? = null

    @Volatile
    private var acceptThread: Thread? = null

    val isRunning: Boolean
        get() = synchronized(lock) { running }

    val port: Int?
        get() = synchronized(lock) { if (running) boundPort else null }

    fun start(): StartResult = synchronized(lock) {
        if (running) {
            return StartResult.Failed("服务已在运行（端口 $boundPort）")
        }
        val serverSocket = ServerSocket()
        try {

            serverSocket.setReuseAddress(true)
            serverSocket.bind(InetSocketAddress(bindAddress, requestedPort))
        } catch (e: BindException) {
            runCatching { serverSocket.close() }
            return StartResult.Failed(
                "绑定 ${bindAddress.hostAddress}:$requestedPort 失败（端口被占）：${e.message}",
                portInUse = true,
            )
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
                break
            }
            connections.add(client)
            val handler = Thread({ handleConnection(client) }, "sofatype-remote-conn")
            handler.isDaemon = true
            handler.start()
        }
        closeQuietly(serverSocket)
    }

    private fun handleConnection(client: Socket) {
        try {
            client.tcpNoDelay = true
            client.use { serve(it) }
        } catch (_: Exception) {

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

        val delegate = wsDelegateFactory?.invoke() ?: wsDelegate
        when (val route = HttpRouter.route(request, mainPageProvider)) {
            is RouteResult.Respond -> {
                output.write(route.bytes)
                output.flush()
            }
            RouteResult.UpgradeWebSocket -> {
                output.write(WsHandshake.upgradeResponse(request.headers["sec-websocket-key"].orEmpty()))
                output.flush()

                val writer = ConnectionWriter(output)
                try {
                    delegate?.attachConnection(
                        { text -> writer.writeTextMessage(text) },
                        { closeCode ->
                            runCatching {
                                writer.writeRaw(WsFrameCodec.encode(WsOpcode.CLOSE, closeCodeBytes(closeCode)))
                            }

                            runCatching { socket.close() }
                        },
                    )
                    wsLoop(input, output, delegate, socket, writer)
                } finally {
                    writer.shutdown()

                    delegate?.onSessionClosed()
                }
            }
        }
    }

    private class ConnectionWriter(private val output: java.io.OutputStream) {
        private val executor = java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "sofatype-remote-writer").apply { isDaemon = true }
        }

        fun writeRaw(bytes: ByteArray) {
            executor.submit {
                output.write(bytes)
                output.flush()
            }.get()
        }

        fun writeTextMessage(text: String): Boolean = try {
            executor.submit {
                output.write(WsFrameCodec.encode(WsOpcode.TEXT, text.toByteArray(StandardCharsets.UTF_8)))
                output.flush()
            }.get(WRITE_ACK_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
            true
        } catch (_: Exception) {
            false
        }

        fun shutdown() {
            executor.shutdownNow()
        }
    }

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
                val frame = WsFrameCodec.readFrom(input) ?: break
                when (frame.opcode) {
                    WsOpcode.PING -> writer.writeRaw(WsFrameCodec.encode(WsOpcode.PONG, frame.payload))
                    WsOpcode.PONG -> Unit
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

                                    awaitPeerClose(input, socket)
                                    return
                                }
                            }

                            else -> Unit
                        }
                    }
                }
            }
        } catch (e: WsProtocolException) {

            runCatching {
                writer.writeRaw(WsFrameCodec.encode(WsOpcode.CLOSE, closeCodeBytes(e.closeCode)))
            }
        }
    }

    private fun replyText(reply: WsReply): String = when (reply) {
        is WsReply.Text -> reply.text
        is WsReply.TextThenClose -> reply.text
        WsReply.None -> ""
    }

    private fun awaitPeerClose(input: BufferedInputStream, socket: Socket) {
        val deadline = System.currentTimeMillis() + PEER_CLOSE_WAIT_MS
        val previousTimeout = runCatching { socket.soTimeout }.getOrDefault(0)
        runCatching { socket.soTimeout = PEER_CLOSE_READ_TIMEOUT_MS }
        try {
            while (System.currentTimeMillis() < deadline) {
                if (input.available() > 0) {

                    val frame = try {
                        WsFrameCodec.readFrom(input)
                    } catch (_: java.io.IOException) {
                        return
                    } ?: return
                    if (frame.opcode == WsOpcode.CLOSE) return

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

    private fun validatedText(message: WsMessage): String {
        if (!isValidUtf8(message.payload)) {
            throw WsProtocolException(1007, "文本消息不是合法 UTF-8")
        }
        return String(message.payload, StandardCharsets.UTF_8)
    }

    private fun validatedPayload(message: WsMessage): ByteArray {
        if (message.opcode == WsOpcode.TEXT && !isValidUtf8(message.payload)) {
            throw WsProtocolException(1007, "文本消息不是合法 UTF-8")
        }
        return message.payload
    }

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
