package com.sofatype.ime.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sofatype.ime.update.UpdateReleaseParser.ParseException

class UpdateReleaseParserTest {

    @Test
    fun parsesStandardSingleReleaseArray() {
        val json = """
            [
              {
                "url": "https://api.github.com/repos/example/sofatype/releases/1",
                "tag_name": "v0.2.0",
                "name": "v0.2.0",
                "draft": false,
                "prerelease": false,
                "body": "新增检查更新",
                "assets": [
                  {
                    "name": "sofatype-0.2.0.apk",
                    "browser_download_url": "https://example.com/sofatype-0.2.0.apk"
                  }
                ]
              }
            ]
        """.trimIndent()
        val release = UpdateReleaseParser.parse(json)
        assertEquals("v0.2.0", release.tag)
        assertEquals("新增检查更新", release.notes)
        assertEquals("https://example.com/sofatype-0.2.0.apk", release.apkUrl)
    }

    @Test
    fun skipsDraftAndPrereleaseEntries() {
        val json = """
            [
              {"tag_name": "v0.3.0-rc1", "prerelease": true, "assets": [
                {"name": "a.apk", "browser_download_url": "https://e/rc.apk"}]},
              {"tag_name": "v0.2.5", "draft": true, "assets": [
                {"name": "a.apk", "browser_download_url": "https://e/draft.apk"}]},
              {"tag_name": "v0.2.0", "assets": [
                {"name": "a.apk", "browser_download_url": "https://e/stable.apk"}]}
            ]
        """.trimIndent()
        assertEquals("v0.2.0", UpdateReleaseParser.parse(json).tag)
    }

    @Test
    fun picksFirstApkAssetAmongMixedAssets() {
        val json = """
            [
              {"tag_name": "v0.2.0", "assets": [
                {"name": "checksums.zip", "browser_download_url": "https://e/c.zip"},
                {"name": "Sofatype-0.2.0.APK", "browser_download_url": "https://e/app.apk"},
                {"name": "sofatype-0.2.0-old.apk", "browser_download_url": "https://e/old.apk"}
              ]}
            ]
        """.trimIndent()
        assertEquals("https://e/app.apk", UpdateReleaseParser.parse(json).apkUrl)
    }

    @Test
    fun nullBodyBecomesEmptyNotes() {
        val json = """
            [
              {"tag_name": "v0.2.0", "body": null, "assets": [
                {"name": "a.apk", "browser_download_url": "https://e/a.apk"}]}
            ]
        """.trimIndent()
        assertEquals("", UpdateReleaseParser.parse(json).notes)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val json = """
            [
              {"tag_name": "v0.2.0", "author": {"login": "someone"}, "assets": [
                {"name": "a.apk", "browser_download_url": "https://e/a.apk", "size": 123}],
               "extra_number": 1.5, "extra_bool": true}
            ]
        """.trimIndent()
        assertEquals("v0.2.0", UpdateReleaseParser.parse(json).tag)
    }

    @Test
    fun emptyArrayFailsAsParse() {
        val e = assertParse(UpdateFailureReason.PARSE) { UpdateReleaseParser.parse("[]") }
        assertTrue(e.message!!.contains("发布列表为空"))
    }

    @Test
    fun arrayWithoutUsableEntryFailsAsParse() {
        val json = """
            [
              {"tag_name": "v0.3.0-rc1", "prerelease": true, "assets": [
                {"name": "a.apk", "browser_download_url": "https://e/rc.apk"}]}
            ]
        """.trimIndent()
        assertParse(UpdateFailureReason.PARSE) { UpdateReleaseParser.parse(json) }
    }

    @Test
    fun malformedJsonFailsAsParse() {
        assertParse(UpdateFailureReason.PARSE) { UpdateReleaseParser.parse("{\"tag_name\":") }
        assertParse(UpdateFailureReason.PARSE) { UpdateReleaseParser.parse("not json") }
    }

    @Test
    fun selectedReleaseWithoutApkAssetFailsAsNoApkAsset() {
        val json = """
            [
              {"tag_name": "v0.2.0", "assets": [
                {"name": "checksums.zip", "browser_download_url": "https://e/c.zip"}]}
            ]
        """.trimIndent()
        assertParse(UpdateFailureReason.NO_APK_ASSET) { UpdateReleaseParser.parse(json) }
    }

    private fun assertParse(
        expected: UpdateFailureReason,
        block: () -> UpdateRelease,
    ): ParseException =
        try {
            block()
            throw AssertionError("应抛 ParseException($expected)")
        } catch (e: ParseException) {
            assertEquals(expected, e.reason)
            e
        }

    @Test
    fun parsesLatestSingleObjectShape() {
        val json = """
            {
              "url": "https://api.github.com/repos/zsxh/sofatype/releases/1",
              "assets_url": "https://api.github.com/repos/zsxh/sofatype/releases/1/assets",
              "author": {"login": "zsxh"},
              "tag_name": "v0.2.1",
              "name": "v0.2.1",
              "draft": false,
              "prerelease": false,
              "body": "## 0.2.1（2026-11-24）\n\n热词内置与换源。",
              "assets": [
                {"name": "Source code (zip)", "browser_download_url": "https://e/src.zip"},
                {"name": "sofatype-0.2.1-unsigned.apk", "browser_download_url": "https://e/sofatype-0.2.1-unsigned.apk"}
              ]
            }
        """.trimIndent()
        val release = UpdateReleaseParser.parse(json)
        assertEquals("v0.2.1", release.tag)
        assertTrue(release.notes.startsWith("## 0.2.1"))
        assertEquals("https://e/sofatype-0.2.1-unsigned.apk", release.apkUrl)
    }

    @Test
    fun latestSingleObjectUnavailableFails() {
        val json = """{"tag_name": "v0.3.0-rc1", "prerelease": true, "assets": [
            {"name": "a.apk", "browser_download_url": "https://e/rc.apk"}]}"""
        try {
            UpdateReleaseParser.parse(json)
            throw AssertionError("latest 单对象为 prerelease 应解析失败")
        } catch (expected: ParseException) {
        }
    }

}
