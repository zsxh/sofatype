package com.sofatype.ime.update

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException

class UpdateHttpLoopbackTest {

    private val loopback: InetAddress = InetAddress.getLoopbackAddress()
    private val servers = mutableListOf<TinyServer>()
    private val tempDir = createTempDir()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
        tempDir.deleteRecursively()
    }

    @Test
    fun fetchTextReturnsBodyFromLoopbackServer() {
        val body = """[{"tag_name": "v0.2.0", "body": "更新说明"}]"""
        val port = startServer { path ->
            if (path == "/releases.json") TinyResponse("200 OK", body.toByteArray(Charsets.UTF_8), "application/json")
            else TinyResponse("404 Not Found", ByteArray(0), "text/plain")
        }
        val text = UpdateHttp.fetchText("http://${loopback.hostAddress}:$port/releases.json")
        assertEquals(body, text)
    }

    @Test
    fun fetchNon2xxThrowsCheckHttpWithStatus() {
        val port = startServer { TinyResponse("503 Service Unavailable", ByteArray(0), "text/plain") }
        val e = expectHttp {
            UpdateHttp.fetchText("http://${loopback.hostAddress}:$port/releases.json")
        }
        assertEquals(UpdateFailureReason.CHECK_HTTP, e.reason)
        assertEquals(503, e.httpStatus)
    }

    @Test
    fun fetchConnectionRefusedThrowsCheckNetwork() {

        val socket = ServerSocket().also { it.bind(InetSocketAddress(loopback, 0)) }
        val port = socket.localPort
        socket.close()
        val e = expectHttp {
            UpdateHttp.fetchText("http://${loopback.hostAddress}:$port/releases.json")
        }
        assertEquals(UpdateFailureReason.CHECK_NETWORK, e.reason)
        assertNull(e.httpStatus)
    }

    @Test
    fun downloadWritesExactBytesWithMonotonicProgress() {
        val payload = ByteArray(1 shl 20) { i -> (i % 251).toByte() }
        val port = startServer { path ->
            if (path == "/app.apk") TinyResponse("200 OK", payload, "application/vnd.android.package-archive")
            else TinyResponse("404 Not Found", ByteArray(0), "text/plain")
        }
        val dest = File(tempDir, "update_download.apk")
        var lastRead = 0L
        var lastTotal: Long? = null
        var callbacks = 0
        UpdateHttp.downloadToFile(
            url = "http://${loopback.hostAddress}:$port/app.apk",
            dest = dest,
            onProgress = { read, total ->
                callbacks++
                assertTrue(read >= lastRead)
                lastRead = read
                lastTotal = total
            },
            isCancelled = { false },
        )
        assertArrayEquals(payload, dest.readBytes())
        assertEquals(payload.size.toLong(), lastRead)
        assertEquals(payload.size.toLong(), lastTotal)
        assertTrue("进度回调应发生多次", callbacks > 1)
    }

    @Test
    fun downloadNon2xxThrowsDownloadNetworkAndCleansFile() {
        val port = startServer { TinyResponse("404 Not Found", ByteArray(0), "text/plain") }
        val dest = File(tempDir, "update_download.apk")
        val e = expectHttp {
            UpdateHttp.downloadToFile("http://${loopback.hostAddress}:$port/app.apk", dest, { _, _ -> }, { false })
        }
        assertEquals(UpdateFailureReason.DOWNLOAD_NETWORK, e.reason)
        assertEquals(404, e.httpStatus)
        assertFalse(dest.exists())
    }

    @Test
    fun cancelledDownloadThrowsCancelledAndCleansFile() {
        val payload = ByteArray(1 shl 20)
        val port = startServer { TinyResponse("200 OK", payload, "application/vnd.android.package-archive") }
        val dest = File(tempDir, "update_download.apk")
        try {
            UpdateHttp.downloadToFile(
                "http://${loopback.hostAddress}:$port/app.apk",
                dest,
                { _, _ -> },
                { true },
            )
            throw AssertionError("应抛 CancelledException")
        } catch (expected: UpdateHttp.CancelledException) {
            assertFalse(dest.exists())
        }
    }

    @Test
    fun unwritableDestinationFailsAsDownloadIo() {
        val port = startServer { TinyResponse("200 OK", ByteArray(64), "application/vnd.android.package-archive") }
        val dest = File(File(tempDir, "not-exist"), "update_download.apk")
        val e = expectHttp {
            UpdateHttp.downloadToFile("http://${loopback.hostAddress}:$port/app.apk", dest, { _, _ -> }, { false })
        }
        assertEquals(UpdateFailureReason.DOWNLOAD_IO, e.reason)
    }

    private class TinyResponse(val statusLine: String, val body: ByteArray, val contentType: String)

    private class TinyServer(private val handler: (String) -> TinyResponse) {
        private val server = ServerSocket()
        private val thread: Thread

        init {
            server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
            server.soTimeout = 10_000
            thread = Thread {
                while (true) {
                    val socket = try {
                        server.accept()
                    } catch (e: SocketException) {
                        return@Thread
                    }
                    try {
                        socket.use { s ->
                            s.soTimeout = 10_000
                            val reader = BufferedReader(
                                InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1),
                            )
                            val requestLine = reader.readLine() ?: return@use
                            while (reader.readLine()?.isNotEmpty() == true) {

                            }
                            val path = requestLine.split(" ").getOrNull(1) ?: "/"
                            val response = handler(path)
                            val output = s.getOutputStream()
                            output.write(
                                ("HTTP/1.1 ${response.statusLine}\r\n" +
                                    "Content-Type: ${response.contentType}\r\n" +
                                    "Content-Length: ${response.body.size}\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n").toByteArray(Charsets.ISO_8859_1),
                            )
                            output.write(response.body)
                            output.flush()
                        }
                    } catch (e: IOException) {

                    }
                }
            }
            thread.isDaemon = true
            thread.start()
        }

        val port: Int get() = server.localPort

        fun stop() {
            server.close()
        }
    }

    private fun startServer(handler: (String) -> TinyResponse): Int {
        val server = TinyServer(handler)
        servers.add(server)
        return server.port
    }

    private fun expectHttp(block: () -> Unit): UpdateHttp.HttpException =
        try {
            block()
            throw AssertionError("应抛 UpdateHttp.HttpException")
        } catch (expected: UpdateHttp.HttpException) {
            expected
        }
}
