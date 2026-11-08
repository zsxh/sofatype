// T21 · 检查编排三态单元测试（FR-D4，JUnit 4，纯 JVM，fetch 注入假实现）。
package com.sofatype.ime.update

import com.sofatype.ime.update.UpdateCheckResult.Failed
import com.sofatype.ime.update.UpdateCheckResult.Newer
import com.sofatype.ime.update.UpdateCheckResult.UpToDate
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCheckTest {

    private val validNewerJson =
        """[{"tag_name": "v0.2.0", "body": "更新说明", "assets": [
            {"name": "a.apk", "browser_download_url": "https://e/a.apk"}]}]"""

    @Test
    fun blankEndpointFailsWithEmptyEndpoint() {
        val result = UpdateCheck.check(null, "0.1.0") { "" }
        assertEquals(UpdateFailureReason.EMPTY_ENDPOINT, (result as Failed).reason)
        val blank = UpdateCheck.check("   ", "0.1.0") { "" }
        assertEquals(UpdateFailureReason.EMPTY_ENDPOINT, (blank as Failed).reason)
    }

    @Test
    fun httpNon2xxSurfacesStatus() {
        val result = UpdateCheck.check("http://e/releases.json", "0.1.0") {
            throw UpdateHttp.HttpException(UpdateFailureReason.CHECK_HTTP, 503, "HTTP 503")
        }
        assertEquals(UpdateFailureReason.CHECK_HTTP, (result as Failed).reason)
        assertEquals(503, result.httpStatus)
    }

    @Test
    fun genericFetchFailureSurfacesNetwork() {
        val result = UpdateCheck.check("http://e/releases.json", "0.1.0") {
            throw java.io.IOException("连接超时")
        }
        assertEquals(UpdateFailureReason.CHECK_NETWORK, (result as Failed).reason)
    }

    @Test
    fun malformedBodyFailsAsParse() {
        val result = UpdateCheck.check("http://e/releases.json", "0.1.0") { "not json" }
        assertEquals(UpdateFailureReason.PARSE, (result as Failed).reason)
    }

    @Test
    fun releaseWithoutApkFailsAsNoApkAsset() {
        val result = UpdateCheck.check("http://e/releases.json", "0.1.0") {
            """[{"tag_name": "v0.2.0", "assets": []}]"""
        }
        assertEquals(UpdateFailureReason.NO_APK_ASSET, (result as Failed).reason)
    }

    @Test
    fun newerTagReturnsRelease() {
        val result = UpdateCheck.check("http://e/releases.json", "0.1.0", fetchBody(validNewerJson))
        val newer = result as Newer
        assertEquals("v0.2.0", newer.release.tag)
        assertEquals("更新说明", newer.release.notes)
        assertEquals("https://e/a.apk", newer.release.apkUrl)
    }

    @Test
    fun equalOrOlderTagIsUpToDate() {
        assertEquals(
            UpToDate,
            UpdateCheck.check("http://e/releases.json", "0.1.0", fetchBody(validNewerJson.replace("v0.2.0", "v0.1.0"))),
        )
        assertEquals(
            UpToDate,
            UpdateCheck.check("http://e/releases.json", "0.3.0", fetchBody(validNewerJson)),
        )
    }

    @Test
    fun endpointIsTrimmedBeforeFetch() {
        var received: String? = null
        UpdateCheck.check("  http://e/releases.json  ", "0.1.0") { url ->
            received = url
            validNewerJson
        }
        assertEquals("http://e/releases.json", received)
    }

    private fun fetchBody(body: String): (String) -> String = { body }
}
