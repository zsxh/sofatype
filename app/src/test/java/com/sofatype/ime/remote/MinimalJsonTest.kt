// T14 · 协议最小 JSON 解析/序列化单元测试：标量字段解析、转义处理、字面量、非法输入拒绝、
// 序列化转义回环。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MinimalJsonTest {

    private fun parse(text: String): Map<String, String> = MinimalJson.parseTopLevelObject(text)

    @Test
    fun parsesHelloMessageSample() {
        val fields = parse("""{"type":"hello","token":"AbC-dEf_01","deviceName":"客厅手机"}""")
        assertEquals(3, fields.size)
        assertEquals("hello", fields["type"])
        assertEquals("AbC-dEf_01", fields["token"])
        assertEquals("客厅手机", fields["deviceName"])
    }

    @Test
    fun parsesEmptyObject() {
        assertTrue(parse("{}").isEmpty())
    }

    @Test
    fun toleratesWhitespaceBetweenTokens() {
        val fields = parse("{ \"type\" : \"hello\" , \"token\" : \"t\" }\n")
        assertEquals("hello", fields["type"])
        assertEquals("t", fields["token"])
    }

    @Test
    fun parsesScalarLiteralsAsRawText() {
        val fields = parse("""{"a":true,"b":false,"c":null,"d":200,"e":-1.5}""")
        assertEquals("true", fields["a"])
        assertEquals("false", fields["b"])
        assertEquals("null", fields["c"])
        assertEquals("200", fields["d"])
        assertEquals("-1.5", fields["e"])
    }

    @Test
    fun unescapesJsonStringEscapes() {
        val fields = parse("""{"k":"a\"b\\c/d\n\r\t\bê"}""")
        assertEquals("a\"b\\c/d\n\r\t\bê", fields["k"])
    }

    @Test
    fun unescapesUnicodeEscapeSequences() {
        val fields = parse("""{"tv":"\u4e2d\u6587"}""")
        assertEquals("中文", fields["tv"])
    }

    @Test
    fun duplicateKeysLastValueWins() {
        val fields = parse("""{"t":"1","t":"2"}""")
        assertEquals("2", fields["t"])
    }

    @Test
    fun rejectsMalformedInputs() {
        val malformed = listOf(
            "",                       // 空
            "not json",               // 非对象
            "[1,2]",                  // 数组不支持
            """{"a"[]}""",            // 缺冒号
            """{"a":}""",             // 缺值
            """{"a":"unclosed}""",    // 字符串未闭合
            """{"a":1,}""",           // 尾逗号后无键（解析到非字符串键失败）
            """{"a":trux}""",         // 非法字面量
            """{"a":{"b":1}}""",      // 嵌套对象不支持
            """{"a":[1]}""",          // 数组值不支持
            """{"a":1} tail""",       // 对象后多余内容
            """{"a":"b\q"}""",        // 非法转义
            """{"a":"\u12"}""",       // \u 截断
        )
        for (text in malformed) {
            try {
                parse(text)
                throw AssertionError("应解析失败：$text")
            } catch (_: MinimalJson.JsonParseException) {
                // 预期
            }
        }
    }

    @Test
    fun escapeProducesRoundTrippableOutput() {
        val samples = listOf(
            "plain",
            "中文电视",
            "a\"b\\c",
            "line\nbreak\ttab",
            "\u001e\u001f",
            "emoji \uD83D\uDE00",
        )
        for (value in samples) {
            val json = "{\"tv\":\"" + MinimalJson.escape(value) + "\"}"
            assertEquals(value, parse(json)["tv"])
        }
    }

    @Test
    fun escapeEncodesQuoteBackslashAndControlChars() {
        assertEquals("a\\\"b", MinimalJson.escape("a\"b"))
        assertEquals("a\\\\b", MinimalJson.escape("a\\b"))
        assertEquals("a\\nb", MinimalJson.escape("a\nb"))
        assertEquals("\\u0001", MinimalJson.escape("\u0001"))
    }

    @Test
    fun escapeKeepsNonAsciiAsIs() {
        assertEquals("中文", MinimalJson.escape("中文"))
    }

    @Test
    fun parseNumberKeepsRawDigitsUsefulForCharLimit() {
        assertEquals("200", parse("""{"charLimit":200}""")["charLimit"])
        assertNull(parse("""{"charLimit":200}""")["missing"])
    }
}
