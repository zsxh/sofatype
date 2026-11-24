package com.sofatype.ime.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.sofatype.ime.update.UpdateAtomParser.ParseException

class UpdateAtomParserTest {

    private val realShape =
        """<?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom" xml:lang="en-US">
          <id>tag:github.com,2008:https://github.com/zsxh/sofatype/releases</id>
          <title>Release notes from sofatype</title>
          <updated>2026-11-24T05:37:12Z</updated>
          <entry>
            <id>tag:github.com,2008:Repository/1/v0.2.1</id>
            <updated>2026-11-24T05:37:12Z</updated>
            <link rel="alternate" type="text/html" href="https://github.com/zsxh/sofatype/releases/tag/v0.2.1"/>
            <title>v0.2.1</title>
            <content type="html">&lt;h2&gt;0.2.1（2026-11-24）&lt;/h2&gt;
&lt;p&gt;日期：2026-11-24（T96 ~ T99 / T101；随 0.2.1 发布）。&lt;/p&gt;
&lt;p&gt;热词内置（T96）。&lt;/p&gt;</content>
          </entry>
          <entry>
            <id>tag:github.com,2008:Repository/1/v0.2.0</id>
            <title>v0.2.0</title>
            <content type="html">&lt;p&gt;旧版&lt;/p&gt;</content>
          </entry>
        </feed>"""

    @Test
    fun parsesFirstEntryTagAndCleanedNotes() {
        val entry = UpdateAtomParser.parse(realShape)
        assertEquals("v0.2.1", entry.tag)
        assertTrue(entry.notes.startsWith("0.2.1（2026-11-24）"))
        assertTrue(entry.notes.contains("热词内置（T96）。"))
        assertTrue("HTML 标签应被清洗", !entry.notes.contains("<"))
    }

    @Test
    fun constructsDownloadUrlFromTagWithProxyPrefix() {
        assertEquals(
            "https://gh-proxy.com/https://github.com/zsxh/sofatype/releases/download/v0.2.1/sofatype-0.2.1.apk",
            UpdateAtomParser.downloadUrl("v0.2.1", "https://gh-proxy.com/"),
        )
        assertEquals(
            "https://github.com/zsxh/sofatype/releases/download/v0.2.1/sofatype-0.2.1.apk",
            UpdateAtomParser.downloadUrl("v0.2.1", ""),
        )
    }

    @Test
    fun feedWithoutEntryFails() {
        try {
            UpdateAtomParser.parse("<feed xmlns=\"http://www.w3.org/2005/Atom\"></feed>")
            throw AssertionError("无 entry 应解析失败")
        } catch (expected: ParseException) {
        }
    }

    @Test
    fun unclosedEntryFails() {
        try {
            UpdateAtomParser.parse("<feed><entry><title>v0.1.0</title></feed>")
            throw AssertionError("entry 未闭合应解析失败")
        } catch (expected: ParseException) {
        }
    }
}
