package com.sofatype.ime.dicts

object DictPackManifestParser {

    class ParseException(message: String) : Exception(message)

    private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")
    private val URL_PATTERN = Regex("^https?://\\S+$")

    fun parse(text: String): List<DictPackEntry> {
        val scanner = Scanner(text)
        val entries = scanner.parseArray()
        scanner.requireAtEnd()
        return entries
    }

    private class Scanner(private val text: String) {

        private var pos = 0

        fun requireAtEnd() {
            skipWhitespace()
            if (pos < text.length) throw ParseException("数组结束后存在多余内容：位置 $pos")
        }

        fun parseArray(): List<DictPackEntry> {
            skipWhitespace()
            expect('[')
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return emptyList()
            }
            val result = ArrayList<DictPackEntry>()
            while (true) {
                skipWhitespace()
                result.add(parseEntry())
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return result
                    }
                    else -> throw ParseException("位置 $pos：期望 ',' 或 ']'，实际 '${peek()}'")
                }
            }
        }

        private fun parseEntry(): DictPackEntry {
            expect('{')
            var name: String? = null
            var version: String? = null
            var sha256: String? = null
            var bytes = -1L
            var url: String? = null
            skipWhitespace()
            if (peek() == '}') throw ParseException("词包条目为空对象")
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                when (key) {
                    "name" -> name = parseString()
                    "version" -> version = parseString()
                    "sha256" -> sha256 = parseString()
                    "bytes" -> bytes = parseNumber()
                    "url" -> url = parseString()
                    else -> skipValue()
                }
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        break
                    }
                    else -> throw ParseException("位置 $pos：期望 ',' 或 '}'，实际 '${peek()}'")
                }
            }
            val finalName = name?.takeIf { it.isNotBlank() } ?: throw ParseException("词包条目缺少必填字段：name")
            val finalVersion = version?.takeIf { it.isNotBlank() } ?: throw ParseException("词包条目缺少必填字段：version")
            val finalSha = sha256?.takeIf { it.isNotBlank() } ?: throw ParseException("词包条目缺少必填字段：sha256")
            val finalUrl = url?.takeIf { it.isNotBlank() } ?: throw ParseException("词包条目缺少必填字段：url")
            if (bytes <= 0) throw ParseException("词包条目字节数非法：$bytes")
            if (!SHA256_PATTERN.matches(finalSha)) throw ParseException("词包 sha256 非法（须 64 位十六进制）")
            if (!URL_PATTERN.matches(finalUrl)) throw ParseException("词包下载地址非法（仅支持 http/https）")
            if (finalName.contains('/') || finalName.contains('\\') || finalName.contains("..")) {
                throw ParseException("词包名称非法（含路径分隔符）")
            }
            if (finalName.any { it.code < 0x20 }) {
                throw ParseException("词包名称非法（含控制字符）")
            }
            return DictPackEntry(finalName, finalVersion, finalSha.lowercase(), bytes, finalUrl)
        }

        private fun parseString(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                if (pos >= text.length) throw ParseException("字符串提前结束")
                val c = text[pos++]
                when {
                    c == '"' -> return sb.toString()
                    c == '\\' -> {
                        if (pos >= text.length) throw ParseException("转义符提前结束")
                        when (val esc = text[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'u' -> {
                                if (pos + 4 > text.length) throw ParseException("unicode 转义提前结束")
                                sb.append(text.substring(pos, pos + 4).toInt(16).toChar())
                                pos += 4
                            }
                            else -> throw ParseException("非法转义字符：\\$esc")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun parseNumber(): Long {
            val start = pos
            while (pos < text.length && (text[pos].isDigit() || text[pos] == '-')) pos++
            val raw = text.substring(start, pos)
            return raw.toLongOrNull() ?: throw ParseException("位置 $start：数字字段非法：$raw")
        }

        private fun skipValue() {
            skipWhitespace()
            when (peek()) {
                '"' -> parseString()
                '{', '[' -> {
                    val close = if (peek() == '{') '}' else ']'
                    pos++
                    while (true) {
                        skipWhitespace()
                        if (peek() == close) {
                            pos++
                            return
                        }
                        if (peek() == '"') {
                            parseString()
                            skipWhitespace()
                            if (peek() == ':') pos++
                            continue
                        }
                        skipValue()
                        skipWhitespace()
                        when (peek()) {
                            ',' -> pos++
                            close -> {
                                pos++
                                return
                            }
                            else -> throw ParseException("位置 $pos：嵌套结构非法")
                        }
                    }
                }
                else -> {
                    while (pos < text.length && text[pos] !in ",}]") pos++
                }
            }
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos] in " \t\r\n") pos++
        }

        private fun expect(expected: Char) {
            if (pos >= text.length || text[pos] != expected) {
                throw ParseException("位置 $pos：期望 '$expected'")
            }
            pos++
        }

        private fun peek(): Char {
            if (pos >= text.length) throw ParseException("输入提前结束")
            return text[pos]
        }
    }

    fun writeEntryJson(entry: DictPackEntry): String =
        "[" +
            "{\"name\":${quote(entry.name)}," +
            "\"version\":${quote(entry.version)}," +
            "\"sha256\":${quote(entry.sha256Hex)}," +
            "\"bytes\":${entry.bytes}," +
            "\"url\":${quote(entry.url)}}" +
            "]"

    private fun quote(raw: String): String {
        val sb = StringBuilder("\"")
        for (c in raw) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}
