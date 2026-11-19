package com.sofatype.ime.remote

object MinimalJson {

    class JsonParseException(message: String) : Exception(message)

    sealed class Value {

        class Str(val value: String) : Value()

        class Raw(val literal: String) : Value()
    }

    fun parseTopLevelObject(text: String): Map<String, String> {
        val typed = parseTopLevelObjectTyped(text)
        val result = LinkedHashMap<String, String>(typed.size)
        for ((key, value) in typed) {
            result[key] = when (value) {
                is Value.Str -> value.value
                is Value.Raw -> value.literal
            }
        }
        return result
    }

    fun parseTopLevelObjectTyped(text: String): Map<String, Value> {
        val parser = Parser(text)
        val result = parser.parseObject()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonParseException("对象结束后存在多余内容")
        return result
    }

    fun escape(value: String): String {
        val out = StringBuilder(value.length + 8)
        for (c in value) {
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\u000C' -> out.append("\\f")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos] in " \t\r\n") pos++
        }

        fun parseObject(): Map<String, Value> {
            skipWhitespace()
            expect('{')
            val result = LinkedHashMap<String, Value>()
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return result
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = parseValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return result
                    }
                    else -> throw JsonParseException("位置 $pos：期望 ',' 或 '}'，实际 '${peek()}'")
                }
            }
        }

        private fun parseValue(): Value {
            val c = peek()
            return when {
                c == '"' -> Value.Str(parseString())
                c == 't' -> Value.Raw(parseLiteral("true"))
                c == 'f' -> Value.Raw(parseLiteral("false"))
                c == 'n' -> Value.Raw(parseLiteral("null"))
                c == '-' || c in '0'..'9' -> Value.Raw(parseNumber())
                else -> throw JsonParseException("位置 $pos：非法值起始字符 '$c'")
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (pos >= text.length) throw JsonParseException("字符串未闭合")
                val c = text[pos++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> out.append(readEscape())
                    c < ' ' -> throw JsonParseException("字符串含未转义控制字符")
                    else -> out.append(c)
                }
            }
        }

        private fun readEscape(): Char {
            if (pos >= text.length) throw JsonParseException("转义序列截断")
            val c = text[pos++]
            return when (c) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'b' -> '\b'
                'f' -> '\u000C'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> {
                    if (pos + 4 > text.length) throw JsonParseException("\\u 转义截断")
                    val hex = text.substring(pos, pos + 4)
                    pos += 4
                    (hex.toIntOrNull(16) ?: throw JsonParseException("\\u 转义非十六进制：$hex")).toChar()
                }
                else -> throw JsonParseException("非法转义字符：\\$c")
            }
        }

        private fun parseLiteral(expected: String): String {
            if (pos + expected.length > text.length ||
                text.substring(pos, pos + expected.length) != expected
            ) {
                throw JsonParseException("位置 $pos：非法字面量（期望 $expected）")
            }
            pos += expected.length
            return expected
        }

        private fun parseNumber(): String {
            val start = pos
            if (peek() == '-') pos++
            when (peek()) {
                '0' -> pos++
                in '1'..'9' -> while (pos < text.length && text[pos] in '0'..'9') pos++
                else -> throw JsonParseException("位置 $pos：非法数字")
            }
            if (pos < text.length && text[pos] == '.') {
                pos++
                if (pos >= text.length || text[pos] !in '0'..'9') {
                    throw JsonParseException("位置 $pos：小数点后缺数字")
                }
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            if (pos < text.length && (text[pos] == 'e' || text[pos] == 'E')) {
                pos++
                if (pos < text.length && (text[pos] == '+' || text[pos] == '-')) pos++
                if (pos >= text.length || text[pos] !in '0'..'9') {
                    throw JsonParseException("位置 $pos：指数缺数字")
                }
                while (pos < text.length && text[pos] in '0'..'9') pos++
            }
            if (pos == start) throw JsonParseException("位置 $start：非法数字")
            return text.substring(start, pos)
        }

        private fun expect(expected: Char) {
            if (pos >= text.length || text[pos] != expected) {
                throw JsonParseException("位置 $pos：期望 '$expected'")
            }
            pos++
        }

        private fun peek(): Char {
            if (pos >= text.length) throw JsonParseException("输入提前结束")
            return text[pos]
        }
    }
}
