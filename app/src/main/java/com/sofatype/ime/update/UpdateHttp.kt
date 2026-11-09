// T21 · 更新检查 / APK 下载的 HTTP 实现（java.net.HttpURLConnection，纯 Android SDK，无新依赖）。
// 仅在后台线程调用（debug 构建 StrictMode 主线程网络 IO 为 death penalty，见 HANDOFF 坑 14）。
package com.sofatype.ime.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object UpdateHttp {

    /** 网络 / 状态码类失败；[reason] 区分检查与下载阶段，[httpStatus] 携带非 2xx 状态码。 */
    class HttpException(
        val reason: UpdateFailureReason,
        val httpStatus: Int? = null,
        message: String,
    ) : Exception(message)

    /** 下载被取消（Activity 销毁或新操作取代旧操作）。 */
    class CancelledException : Exception("下载已取消")

    /** GET 取回响应正文（UTF-8 文本），非 2xx 抛 CHECK_HTTP / 网络异常抛 CHECK_NETWORK。 */
    fun fetchText(
        url: String,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
    ): String {
        val conn = open(url, connectTimeoutMs, readTimeoutMs)
        try {
            val status = conn.responseCode
            if (status !in 200..299) {
                throw HttpException(UpdateFailureReason.CHECK_HTTP, status, "HTTP $status")
            }
            return conn.inputStream.use { input -> input.readBytes().toString(Charsets.UTF_8) }
        } catch (e: HttpException) {
            throw e
        } catch (e: IOException) {
            throw HttpException(UpdateFailureReason.CHECK_NETWORK, null, e.message ?: "网络错误")
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 下载文件到 [dest]，按块回调进度（[onProgress](已读字节, 总字节；未知为 null)）；
     * [isCancelled] 返回 true 时抛 [CancelledException]。任何失败路径删除半成品文件。
     */
    fun downloadToFile(
        url: String,
        dest: File,
        onProgress: (bytesRead: Long, totalBytes: Long?) -> Unit,
        isCancelled: () -> Boolean,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS,
    ) {
        val conn = open(url, connectTimeoutMs, readTimeoutMs)
        try {
            val status = conn.responseCode
            if (status !in 200..299) {
                throw HttpException(UpdateFailureReason.DOWNLOAD_NETWORK, status, "HTTP $status")
            }
            // T46 · minSdk 23 兼容：getContentLengthLong() 为 API 24+ 方法，基线设备（Android 6.0.1）
            // 上必抛 NoSuchMethodError 且为 Error 不入本方法异常链（真机 2026-11-09 实证 100% 崩溃）。
            // 改用 API 9+ 的 int 版 getContentLength()：2GB 内等价（本项目更新包为 MB 量级，远低于界），
            // 超界/未知时返回 -1 → total=null → 进度回调按未知总长口径（onProgress 已支持 null）。
            val total = conn.contentLength.takeIf { it >= 0 }?.toLong()
            var copied = 0L
            conn.inputStream.use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        if (isCancelled()) throw CancelledException()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(copied, total)
                    }
                    output.flush()
                }
            }
            if (isCancelled()) throw CancelledException()
        } catch (e: CancelledException) {
            dest.delete()
            throw e
        } catch (e: HttpException) {
            dest.delete()
            throw e
        } catch (e: IOException) {
            dest.delete()
            throw HttpException(
                reason = if (e.isNetworkShape) UpdateFailureReason.DOWNLOAD_NETWORK else UpdateFailureReason.DOWNLOAD_IO,
                httpStatus = null,
                message = e.message ?: "下载失败",
            )
        } finally {
            conn.disconnect()
        }
    }

    private fun open(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", USER_AGENT)
        return conn
    }

    private val IOException.isNetworkShape: Boolean
        get() = this is SocketException || this is SocketTimeoutException ||
            this is UnknownHostException || this is SSLException

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val BUFFER_BYTES = 8 * 1024
    private const val USER_AGENT = "sofatype-ime-update"
}
