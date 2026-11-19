package com.sofatype.ime.update

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
import android.view.KeyEvent
import com.sofatype.ime.SettingsKeyRouter

class UpdateSettingsChainIntegrationTest {

    private val tempDir = createTempDir()
    private val servers = mutableListOf<TinyServer>()

    @After
    fun tearDown() {
        servers.forEach { it.stop() }
        tempDir.deleteRecursively()
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
                            val host = readHeaderHost(reader)
                            while (reader.readLine()?.isNotEmpty() == true) {
                            }
                            val path = requestLine.split(" ").getOrNull(1) ?: "/"
                            val response = handler(path + "|" + host)
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

        private fun readHeaderHost(reader: BufferedReader): String {
            while (true) {
                val line = reader.readLine() ?: return ""
                if (line.startsWith("Host:", ignoreCase = true)) {
                    return line.substringAfter(':').trim()
                }
            }
        }

        val port: Int get() = server.localPort

        fun stop() {
            server.close()
        }
    }

    private fun serveReleases(tag: String, apkPayload: ByteArray): String {
        val apkName = "sofatype-${tag.removePrefix("v")}.apk"
        val json = """
            [
              {
                "url": "https://api.github.com/repos/sofatype/sofatype/releases/2",
                "tag_name": "$tag",
                "name": "$tag",
                "draft": false,
                "prerelease": false,
                "body": "T91 全链 mock 演示数据。",
                "assets": [
                  {
                    "name": "$apkName",
                    "content_type": "application/vnd.android.package-archive",
                    "browser_download_url": "http://__HOSTPORT__/$apkName"
                  }
                ]
              }
            ]
        """.trimIndent()
        val server = TinyServer { pathAndHost ->
            val path = pathAndHost.substringBefore('|')
            val host = pathAndHost.substringAfter('|')
            when {
                path == "/releases.json" -> TinyResponse(
                    "200 OK",
                    json.replace("__HOSTPORT__", host).toByteArray(Charsets.UTF_8),
                    "application/json",
                )
                path == "/$apkName" -> TinyResponse(
                    "200 OK",
                    apkPayload,
                    "application/vnd.android.package-archive",
                )
                else -> TinyResponse("404 Not Found", ByteArray(0), "text/plain")
            }
        }
        servers.add(server)
        return "http://${InetAddress.getLoopbackAddress().hostAddress}:${server.port}/releases.json"
    }

    @Test
    fun `设置路由确认键到检查到解析到下载到渠道到收据全链`() {
        assertEquals(SettingsKeyRouter.Route.CONFIRM, SettingsKeyRouter.route(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(SettingsKeyRouter.Route.BACK, SettingsKeyRouter.route(KeyEvent.KEYCODE_BACK))
        assertEquals(SettingsKeyRouter.Route.FOCUS_DOWN, SettingsKeyRouter.route(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(0, SettingsKeyRouter.focusDirection(SettingsKeyRouter.Route.PASS))
        assertEquals(android.view.View.FOCUS_DOWN, SettingsKeyRouter.focusDirection(SettingsKeyRouter.Route.FOCUS_DOWN))

        val apkPayload = ByteArray(512 * 1024) { i -> (i % 251).toByte() }
        val endpoint = serveReleases("v0.3.0", apkPayload)
        val result = UpdateCheck.check(endpoint, "0.2.0") { url -> UpdateHttp.fetchText(url) }
        val release = (result as UpdateCheckResult.Newer).release
        assertEquals("v0.3.0", release.tag)
        assertEquals("T91 全链 mock 演示数据。", release.notes)
        assertTrue(release.apkUrl, release.apkUrl.endsWith("/sofatype-0.3.0.apk"))

        val dest = File(tempDir, "update_download.apk")
        var progressCalls = 0
        UpdateHttp.downloadToFile(
            url = release.apkUrl,
            dest = dest,
            onProgress = { _, _ -> progressCalls++ },
            isCancelled = { false },
        )
        assertArrayEquals(apkPayload, dest.readBytes())
        assertTrue("下载进度回调应发生多次", progressCalls > 1)

        assertEquals(InstallChannel.UI_INTENT, InstallChannels.channelFor(23))
        assertEquals(InstallChannel.SESSION, InstallChannels.channelFor(24))

        assertEquals(
            InstallReceiptDecision.PENDING_USER_ACTION,
            InstallReceipt.decisionFor(InstallReceipt.STATUS_PENDING_USER_ACTION),
        )
        assertEquals(InstallReceiptDecision.SUCCESS, InstallReceipt.decisionFor(InstallReceipt.STATUS_SUCCESS))
        assertEquals(InstallReceiptDecision.FAILURE, InstallReceipt.decisionFor(InstallReceipt.STATUS_FAILURE))

        val marker = UiInstallMarker.encode(2)
        assertEquals("before=2", marker)
        assertEquals(2, UiInstallMarker.decode(marker))
        assertEquals(null, UiInstallMarker.decode("before=abc"))
        assertEquals(UiInstallDecision.UPDATED, UiInstallReceipt.decisionFor(2, 3))
        assertEquals(UiInstallDecision.NOT_UPDATED, UiInstallReceipt.decisionFor(2, 2))
    }

    @Test
    fun `同版本检查报已是最新`() {
        val endpoint = serveReleases("v0.2.0", ByteArray(1024))
        val result = UpdateCheck.check(endpoint, "0.2.0") { url -> UpdateHttp.fetchText(url) }
        assertTrue(result is UpdateCheckResult.UpToDate)
    }

    @Test
    fun `检查失败链路传回失败原因与状态码`() {
        assertEquals(UpdateCheckResult.Failed(UpdateFailureReason.EMPTY_ENDPOINT), UpdateCheck.check(" ", "0.2.0") { url -> UpdateHttp.fetchText(url) })

        val apkPayload = ByteArray(1024)
        val endpoint = serveReleases("v0.3.0", apkPayload)
        val brokenEndpoint = endpoint.replace("/releases.json", "/missing.json")
        val failed = UpdateCheck.check(brokenEndpoint, "0.2.0") { url -> UpdateHttp.fetchText(url) }
        assertTrue(failed is UpdateCheckResult.Failed)
        val failure = failed as UpdateCheckResult.Failed
        assertEquals(UpdateFailureReason.CHECK_HTTP, failure.reason)
        assertEquals(404, failure.httpStatus)
    }
}
