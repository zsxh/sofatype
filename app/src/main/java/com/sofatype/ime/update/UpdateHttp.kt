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

    class HttpException(
        val reason: UpdateFailureReason,
        val httpStatus: Int? = null,
        message: String,
    ) : Exception(message)

    class CancelledException : Exception("下载已取消")

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
