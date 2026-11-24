package com.sofatype.ime.update

import com.sofatype.ime.update.UpdateCheckResult.Failed
import com.sofatype.ime.update.UpdateCheckResult.Newer
import com.sofatype.ime.update.UpdateCheckResult.UpToDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {

    private val validNewerJson =
        """[{"tag_name": "v0.2.0", "body": "更新说明", "assets": [
            {"name": "a.apk", "browser_download_url": "https://e/a.apk"}]}]"""

    private val validAtom =
        """<?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <entry>
            <id>tag:github.com,2008:Release/1</id>
            <title>v0.2.1</title>
            <content type="html">&lt;h2&gt;0.2.1（2026-11-24）&lt;/h2&gt;
&lt;p&gt;热词内置与换源。&lt;/p&gt;</content>
          </entry>
        </feed>"""

    private fun jsonConfig(url: String = "http://e/releases.json") =
        UpdateCheckConfig(url = url, atom = false)

    private fun atomConfig(proxyPrefix: String = "", url: String = "http://e/releases.atom") =
        UpdateCheckConfig(url = url, atom = true, proxyPrefix = proxyPrefix)

    @Test
    fun blankUrlFailsWithEmptyEndpoint() {
        val result = UpdateCheck.check(UpdateCheckConfig(url = ""), "0.1.0") { "" }
        assertEquals(UpdateFailureReason.EMPTY_ENDPOINT, (result as Failed).reason)
        val blank = UpdateCheck.check(UpdateCheckConfig(url = "   "), "0.1.0") { "" }
        assertEquals(UpdateFailureReason.EMPTY_ENDPOINT, (blank as Failed).reason)
    }

    @Test
    fun httpNon2xxSurfacesStatus() {
        val result = UpdateCheck.check(jsonConfig(), "0.1.0") {
            throw UpdateHttp.HttpException(UpdateFailureReason.CHECK_HTTP, 503, "HTTP 503")
        }
        assertEquals(UpdateFailureReason.CHECK_HTTP, (result as Failed).reason)
        assertEquals(503, result.httpStatus)
    }

    @Test
    fun genericFetchFailureSurfacesNetwork() {
        val result = UpdateCheck.check(jsonConfig(), "0.1.0") {
            throw java.io.IOException("连接超时")
        }
        assertEquals(UpdateFailureReason.CHECK_NETWORK, (result as Failed).reason)
    }

    @Test
    fun malformedBodyFailsAsParse() {
        val result = UpdateCheck.check(jsonConfig(), "0.1.0") { "not json" }
        assertEquals(UpdateFailureReason.PARSE, (result as Failed).reason)
    }

    @Test
    fun releaseWithoutApkFailsAsNoApkAsset() {
        val result = UpdateCheck.check(jsonConfig(), "0.1.0") {
            """[{"tag_name": "v0.2.0", "body": "更新说明", "assets": []}]"""
        }
        assertEquals(UpdateFailureReason.NO_APK_ASSET, (result as Failed).reason)
    }

    @Test
    fun newerTagReturnsRelease() {
        val result = UpdateCheck.check(jsonConfig(), "0.1.0", fetchBody(validNewerJson))
        assertEquals("v0.2.0", (result as Newer).release.tag)
    }

    @Test
    fun equalOrOlderTagIsUpToDate() {
        assertEquals(
            UpToDate,
            UpdateCheck.check(jsonConfig(), "0.1.0", fetchBody(validNewerJson.replace("v0.2.0", "v0.1.0"))),
        )
        assertEquals(UpToDate, UpdateCheck.check(jsonConfig(), "0.3.0", fetchBody(validNewerJson)))
    }

    @Test
    fun urlIsTrimmedBeforeFetch() {
        var fetched: String? = null
        UpdateCheck.check(jsonConfig("  http://e/releases.json  "), "0.1.0") { url ->
            fetched = url
            validNewerJson
        }
        assertEquals("http://e/releases.json", fetched)
    }

    @Test
    fun atomNewerAppliesProxyPrefixToConstructedDownloadUrl() {
        val result = UpdateCheck.check(atomConfig(proxyPrefix = "https://gh-proxy.com/"), "0.1.0", fetchBody(validAtom))
        val newer = result as Newer
        assertEquals("v0.2.1", newer.release.tag)
        assertTrue(newer.release.notes.contains("热词内置与换源"))
        assertTrue(newer.release.notes.contains("0.2.1（2026-11-24）"))
        assertEquals(
            "https://gh-proxy.com/https://github.com/zsxh/sofatype/releases/download/v0.2.1/sofatype-0.2.1-unsigned.apk",
            newer.release.apkUrl,
        )
    }

    @Test
    fun atomWithoutProxyYieldsDirectDownloadUrl() {
        val result = UpdateCheck.check(atomConfig(), "0.1.0", fetchBody(validAtom))
        assertEquals(
            "https://github.com/zsxh/sofatype/releases/download/v0.2.1/sofatype-0.2.1-unsigned.apk",
            (result as Newer).release.apkUrl,
        )
    }

    @Test
    fun atomEqualTagIsUpToDate() {
        assertEquals(UpToDate, UpdateCheck.check(atomConfig(), "0.2.1", fetchBody(validAtom)))
    }

    @Test
    fun atomMalformedFailsAsParse() {
        val result = UpdateCheck.check(atomConfig(), "0.1.0") { "<feed></feed>" }
        assertEquals(UpdateFailureReason.PARSE, (result as Failed).reason)
    }

    private fun fetchBody(body: String): (String) -> String = { body }
}
