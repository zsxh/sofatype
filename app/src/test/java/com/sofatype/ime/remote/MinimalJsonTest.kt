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
            "",
            "not json",
            "[1,2]",
            """{"a"[]}""",
            """{"a":}""",
            """{"a":"unclosed}""",
            """{"a":1,}""",
            """{"a":trux}""",
            """{"a":{"b":1}}""",
            """{"a":[1]}""",
            """{"a":1} tail""",
            """{"a":"b\q"}""",
            """{"a":"\u12"}""",
        )
        for (text in malformed) {
            try {
                parse(text)
                throw AssertionError("应解析失败：$text")
            } catch (_: MinimalJson.JsonParseException) {

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

    @Test
    fun numberGrammarAcceptsRfc8259Forms() {
        val accepted = mapOf(
            """{"n":0}""" to "0",
            """{"n":-0}""" to "-0",
            """{"n":200}""" to "200",
            """{"n":-1.5}""" to "-1.5",
            """{"n":1.25e2}""" to "1.25e2",
            """{"n":1.25E-2}""" to "1.25E-2",
            """{"n":1e+3}""" to "1e+3",
        )
        for ((json, expected) in accepted) {
            assertEquals(expected, parse(json)["n"])
        }
    }

    @Test
    fun numberGrammarRejectsLenientForms() {
        val rejected = listOf(
            """{"n":1.2.3}""",
            """{"n":01}""",
            """{"n":-}""",
            """{"n":+1}""",
            """{"n":1.}""",
            """{"n":.5}""",
            """{"n":1e}""",
            """{"n":1e+}""",
            """{"n":--1}""",
        )
        for (text in rejected) {
            try {
                parse(text)
                throw AssertionError("应按非法数字拒绝：$text")
            } catch (_: MinimalJson.JsonParseException) {

            }
        }
    }

    @Test
    fun typedParseDistinguishesStringFromRawScalar() {
        val typed = MinimalJson.parseTopLevelObjectTyped(
            """{"type":"input","text":"你好","limit":200,"flag":true,"none":null}""",
        )
        assertTrue(typed["text"] is MinimalJson.Value.Str)
        assertEquals("你好", (typed["text"] as MinimalJson.Value.Str).value)
        assertTrue(typed["type"] is MinimalJson.Value.Str)
        assertTrue(typed["limit"] is MinimalJson.Value.Raw)
        assertEquals("200", (typed["limit"] as MinimalJson.Value.Raw).literal)
        assertTrue(typed["flag"] is MinimalJson.Value.Raw)
        assertTrue(typed["none"] is MinimalJson.Value.Raw)
    }

    @Test
    fun typedParseRejectsMalformedLikePlainParse() {
        for (text in listOf("not json", """{"a":1.2.3}""", """{"a":"open""")) {
            try {
                MinimalJson.parseTopLevelObjectTyped(text)
                throw AssertionError("应解析失败：$text")
            } catch (_: MinimalJson.JsonParseException) {

            }
        }
    }

    @Test
    fun plainParseAndTypedParseAgreeOnStringValues() {
        val json = """{"tv":"电视","n":-2.5e1}"""
        val plain = parse(json)
        val typed = MinimalJson.parseTopLevelObjectTyped(json)
        assertEquals("电视", plain["tv"])
        assertEquals("电视", (typed["tv"] as MinimalJson.Value.Str).value)
        assertEquals("-2.5e1", plain["n"])
        assertEquals("-2.5e1", (typed["n"] as MinimalJson.Value.Raw).literal)
    }
}
