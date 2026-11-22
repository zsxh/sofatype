package com.sofatype.ime.dicts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DictPackManifestParserTest {

    private val sha =
        "a3b1c2d3e4f5061728394a5b6c7d8e9fa0b1c2d3e4f5061728394a5b6c7d8e9f"

    @Test
    fun `解析合法目录_字段齐全`() {
        val text = """
            [
              {"name": "样例热词包", "version": "1.0", "sha256": "$sha", "bytes": 321, "url": "http://10.0.2.2:8000/wordpack-sample.bin"}
            ]
        """.trimIndent()

        val entries = DictPackManifestParser.parse(text)

        assertEquals(1, entries.size)
        assertEquals("样例热词包", entries[0].name)
        assertEquals("1.0", entries[0].version)
        assertEquals(sha.lowercase(), entries[0].sha256Hex)
        assertEquals(321L, entries[0].bytes)
        assertEquals("http://10.0.2.2:8000/wordpack-sample.bin", entries[0].url)
    }

    @Test
    fun `解析空数组为空列表_允许多条目与多余字段`() {
        assertEquals(emptyList<DictPackEntry>(), DictPackManifestParser.parse("[]"))
        val text = """
            [
              {"name": "a", "version": "1", "sha256": "$sha", "bytes": 1, "url": "https://example.com/a.bin", "extra": {"n": [1, 2]}},
              {"name": "b", "version": "2", "sha256": "$sha", "bytes": 2, "url": "http://example.com/b.bin"}
            ]
        """.trimIndent()
        assertEquals(2, DictPackManifestParser.parse(text).size)
    }

    @Test
    fun `sha256非64位十六进制被拒`() {
        for (bad in listOf("abc", sha.drop(1), sha.dropLast(1) + "g")) {
            try {
                DictPackManifestParser.parse(
                    """[{"name":"a","version":"1","sha256":"$bad","bytes":1,"url":"http://e.com/a.bin"}]""",
                )
                throw AssertionError("sha256=$bad 应被拒绝")
            } catch (expected: DictPackManifestParser.ParseException) {
            }
        }
    }

    @Test
    fun `字节数非法被拒`() {
        for (bad in listOf("0", "-5")) {
            try {
                DictPackManifestParser.parse(
                    """[{"name":"a","version":"1","sha256":"$sha","bytes":$bad,"url":"http://e.com/a.bin"}]""",
                )
                throw AssertionError("bytes=$bad 应被拒绝")
            } catch (expected: DictPackManifestParser.ParseException) {
            }
        }
    }

    @Test
    fun `名称含路径分隔符被拒`() {
        for (bad in listOf("../evil", "a/b")) {
            try {
                DictPackManifestParser.parse(
                    """[{"name":"$bad","version":"1","sha256":"$sha","bytes":1,"url":"http://e.com/a.bin"}]""",
                )
                throw AssertionError("name=$bad 应被拒绝")
            } catch (expected: DictPackManifestParser.ParseException) {
            }
        }
        try {
            DictPackManifestParser.parse(
                """[{"name":"a\\\\b","version":"1","sha256":"$sha","bytes":1,"url":"http://e.com/a.bin"}]""",
            )
            throw AssertionError("JSON 转义反斜杠路径名应被拒绝")
        } catch (expected: DictPackManifestParser.ParseException) {
        }
        try {
            DictPackManifestParser.parse(
                """[{"name":"a\b","version":"1","sha256":"$sha","bytes":1,"url":"http://e.com/a.bin"}]""",
            )
            throw AssertionError("JSON 转义为控制字符的名称应被拒绝")
        } catch (expected: DictPackManifestParser.ParseException) {
        }
    }

    @Test
    fun `非http下载地址与缺字段被拒`() {
        val cases = listOf(
            """[{"name":"a","version":"1","sha256":"$sha","bytes":1,"url":"ftp://e.com/a.bin"}]""",
            """[{"name":"","version":"1","sha256":"$sha","bytes":1,"url":"http://e.com/a.bin"}]""",
            """[{"name":"a","sha256":"$sha","bytes":1,"url":"http://e.com/a.bin"}]""",
            """[{"name":"a","version":"1","bytes":1,"url":"http://e.com/a.bin"}]""",
            """[{"name":"a","version":"1","sha256":"$sha","url":"http://e.com/a.bin"}]""",
        )
        for (text in cases) {
            try {
                DictPackManifestParser.parse(text)
                throw AssertionError("应被拒绝：$text")
            } catch (expected: DictPackManifestParser.ParseException) {
            }
        }
    }

    @Test
    fun `非数组与截断输入与尾随内容被拒`() {
        val cases = listOf(
            """{"name":"a"}""",
            """[{"name":"a"""".trimIndent() + "}",
            """[] 拖尾""",
            "",
        )
        for (text in cases) {
            try {
                DictPackManifestParser.parse(text)
                throw AssertionError("应被拒绝：$text")
            } catch (expected: DictPackManifestParser.ParseException) {
            }
        }
    }

    @Test
    fun `字符串转义解析与写出回读一致`() {
        val text =
            """[{"name":"词\"包测é","version":"1.0","sha256":"$sha","bytes":9,"url":"http://e.com/a%20b.bin"}]"""
        val entries = DictPackManifestParser.parse(text)
        assertEquals(1, entries.size)
        assertEquals("词\"包测é", entries[0].name)

        val unicode = """[{"name":"词\u00e9包","version":"1.0","sha256":"$sha","bytes":9,"url":"http://e.com/a.bin"}]"""
        assertEquals("词é包", DictPackManifestParser.parse(unicode).single().name)

        val json = DictPackManifestParser.writeEntryJson(entries[0])
        val reparsed = DictPackManifestParser.parse(json)
        assertEquals(entries[0], reparsed.single())
    }

    @Test
    fun `写出条目JSON为meta文件口径_数组单条目形态`() {
        val entry = DictPackEntry("样例热词包", "1.0", sha, 321L, "http://10.0.2.2:8000/wordpack-sample.bin")
        val json = DictPackManifestParser.writeEntryJson(entry)
        assertTrue("meta 应为数组形态便于复用目录解析器", json.startsWith("[") && json.endsWith("]"))
        assertEquals(entry, DictPackManifestParser.parse(json).single())
    }
}
