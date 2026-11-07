// T14 · 跨屏协议消息的最小 JSON 解析 / 序列化（PRD 第 7 章）。
// 纯 Kotlin/JVM，不引第三方 JSON 库（工具链拍板：跨屏通道自研）。
// 只覆盖协议所需子集：顶层对象的标量字段（字符串 / 数字 / true / false / null），
// 不支持嵌套对象与数组（协议消息无此形态；出现即解析失败，调用方按拒绝处理）。
package com.sofatype.ime.remote

object MinimalJson {

    /** 解析失败（格式非法或含不支持的结构）。 */
    class JsonParseException(message: String) : Exception(message)

    /**
     * 解析顶层 JSON 对象的标量字段：键 → 值。
     * 字符串值已解转义；数字 / true / false / null 以字面量原文返回；重复键后者覆盖。
     */
    fun parseTopLevelObject(text: String): Map<String, String> {
        val parser = Parser(text)
        val result = parser.parseObject()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw JsonParseException("对象结束后存在多余内容")
        return result
    }

    /** 序列化字符串值：转义引号 / 反斜杠 / 控制字符；非 ASCII 字符原样输出（JSON 允许）。 */
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

        fun parseObject(): Map<String, String> {
            skipWhitespace()
            expect('{')
            val result = LinkedHashMap<String, String>()
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

        private fun parseValue(): String {
            val c = peek()
            return when {
                c == '"' -> parseString()
                c == 't' -> parseLiteral("true")
                c == 'f' -> parseLiteral("false")
                c == 'n' -> parseLiteral("null")
                c == '-' || c in '0'..'9' -> parseNumber()
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

        /** 读取一个转义序列（\uXXXX 含代理对时按 UTF-16 char 原样保留）。 */
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

        /** 数字按字面量原文返回（协议只取字符串字段；此处仅做结构校验）。 */
        private fun parseNumber(): String {
            val start = pos
            while (pos < text.length && text[pos] in "0123456789+-.eE") pos++
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
