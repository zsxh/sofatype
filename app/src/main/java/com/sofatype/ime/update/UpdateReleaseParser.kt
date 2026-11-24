package com.sofatype.ime.update

data class UpdateRelease(

    val tag: String,

    val notes: String,

    val apkUrl: String,
)

object UpdateReleaseParser {

    class ParseException(
        val reason: UpdateFailureReason = UpdateFailureReason.PARSE,
        message: String,
    ) : Exception(message)

    fun parse(text: String): UpdateRelease {
        val parser = Parser(text)
        val release = parser.parseDocument()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw ParseException(message = "数组结束后存在多余内容")
        return release
    }

    private class Parser(private val text: String) {
        private var pos = 0

        fun atEnd(): Boolean = pos >= text.length

        fun skipWhitespace() {
            while (pos < text.length && text[pos] in " \t\r\n") pos++
        }

        private fun expect(expected: Char) {
            if (pos >= text.length || text[pos] != expected) {
                throw ParseException(message = "位置 $pos：期望 '$expected'")
            }
            pos++
        }

        private fun peek(): Char {
            if (pos >= text.length) throw ParseException(message = "输入提前结束")
            return text[pos]
        }

        fun parseDocument(): UpdateRelease {
            skipWhitespace()
            if (peek() == '{') {
                // /releases/latest 单对象形状：最新发布直接给出，不可用（draft/prerelease/空 tag）即失败
                return parseReleaseObject()
                    ?: throw ParseException(message = "latest 发布不可用（draft/prerelease/空 tag）")
            }
            expect('[')
            skipWhitespace()
            if (peek() == ']') throw ParseException(message = "发布列表为空")
            while (true) {
                skipWhitespace()
                val candidate = parseReleaseObject()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return candidate
                            ?: throw ParseException(message = "发布列表无可用条目")
                    }
                    else ->
                        throw ParseException(
                            message = "位置 $pos：期望 ',' 或 ']'，实际 '${peek()}'",
                        )
                }

                if (candidate != null) {
                    skipRemainingArrayElements()
                    return candidate
                }
            }
        }

        private fun skipRemainingArrayElements() {
            while (true) {
                skipWhitespace()
                if (peek() == ']') {
                    pos++
                    return
                }
                skipValue()
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return
                    }
                    else ->
                        throw ParseException(
                            message = "位置 $pos：期望 ',' 或 ']'，实际 '${peek()}'",
                        )
                }
            }
        }

        private fun parseReleaseObject(): UpdateRelease? {
            expect('{')
            var tag: String? = null
            var notes: String? = null
            var draft = false
            var prerelease = false
            var apkUrl: String? = null
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return releaseOrNull(tag, notes, draft, prerelease, apkUrl)
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                when (key) {
                    "tag_name" -> tag = parseStringOrNull()
                    "body" -> notes = parseStringOrNull()
                    "draft" -> draft = parseBoolean()
                    "prerelease" -> prerelease = parseBoolean()
                    "assets" -> apkUrl = parseAssetsAndPickApk()
                    else -> skipValue()
                }
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return releaseOrNull(tag, notes, draft, prerelease, apkUrl)
                    }
                    else ->
                        throw ParseException(
                            message = "位置 $pos：期望 ',' 或 '}'，实际 '${peek()}'",
                        )
                }
            }
        }

        private fun releaseOrNull(
            tag: String?,
            notes: String?,
            draft: Boolean,
            prerelease: Boolean,
            apkUrl: String?,
        ): UpdateRelease? {
            val cleanTag = tag?.trim().orEmpty()
            if (draft || prerelease || cleanTag.isEmpty()) return null
            val url = apkUrl?.trim().orEmpty()
            if (url.isEmpty()) {
                throw ParseException(
                    reason = UpdateFailureReason.NO_APK_ASSET,
                    message = "发布 $cleanTag 缺少 .apk 下载资产",
                )
            }
            return UpdateRelease(tag = cleanTag, notes = notes.orEmpty(), apkUrl = url)
        }

        private fun parseAssetsAndPickApk(): String? {
            expect('[')
            var picked: String? = null
            skipWhitespace()
            if (peek() == ']') {
                pos++
                return null
            }
            while (true) {
                skipWhitespace()
                val assetUrl = parseAssetObject()
                if (picked == null && assetUrl != null) picked = assetUrl
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    ']' -> {
                        pos++
                        return picked
                    }
                    else ->
                        throw ParseException(
                            message = "位置 $pos：期望 ',' 或 ']'，实际 '${peek()}'",
                        )
                }
            }
        }

        private fun parseAssetObject(): String? {
            expect('{')
            var name: String? = null
            var url: String? = null
            skipWhitespace()
            if (peek() == '}') {
                pos++
                return null
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                when (key) {
                    "name" -> name = parseStringOrNull()
                    "browser_download_url" -> url = parseStringOrNull()
                    else -> skipValue()
                }
                skipWhitespace()
                when (peek()) {
                    ',' -> pos++
                    '}' -> {
                        pos++
                        return url
                            ?.trim()
                            ?.takeIf { it.isNotEmpty() && name?.trim()?.lowercase()?.endsWith(".apk") == true }
                    }
                    else ->
                        throw ParseException(
                            message = "位置 $pos：期望 ',' 或 '}'，实际 '${peek()}'",
                        )
                }
            }
        }

        private fun parseBoolean(): Boolean =
            when {
                text.startsWith("true", pos) -> {
                    pos += 4
                    true
                }
                text.startsWith("false", pos) -> {
                    pos += 5
                    false
                }
                else -> throw ParseException(message = "位置 $pos：期望 true / false")
            }

        private fun parseStringOrNull(): String? {
            skipWhitespace()
            if (text.startsWith("null", pos)) {
                pos += 4
                return null
            }
            return parseString()
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                if (pos >= text.length) throw ParseException(message = "字符串未闭合")
                val c = text[pos++]
                when {
                    c == '"' -> return out.toString()
                    c == '\\' -> out.append(readEscape())
                    c < ' ' -> throw ParseException(message = "字符串含未转义控制字符")
                    else -> out.append(c)
                }
            }
        }

        private fun readEscape(): Char {
            if (pos >= text.length) throw ParseException(message = "转义序列截断")
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
                    if (pos + 4 > text.length) throw ParseException(message = "\\u 转义截断")
                    val hex = text.substring(pos, pos + 4)
                    pos += 4
                    val code = hex.toIntOrNull(16)
                        ?: throw ParseException(message = "\\u 转义非十六进制：$hex")

                    code.toChar()
                }
                else -> throw ParseException(message = "非法转义字符：\\$c")
            }
        }

        private fun skipValue() {
            skipWhitespace()
            when (peek()) {
                '{' -> skipContainer('}', isObject = true)
                '[' -> skipContainer(']', isObject = false)
                '"' -> parseString()
                't' -> requireLiteral("true")
                'f' -> requireLiteral("false")
                'n' -> requireLiteral("null")
                '-', in '0'..'9' -> skipNumber()
                else -> throw ParseException(message = "位置 $pos：非法值起始字符 '${peek()}'")
            }
        }

        private fun skipContainer(closer: Char, isObject: Boolean) {
            expect(if (isObject) '{' else '[')
            var depth = 1
            var inString = false
            while (pos < text.length) {
                val c = text[pos++]
                when {
                    inString && c == '\\' -> if (pos < text.length) pos++
                    c == '"' -> inString = !inString
                    !inString && c == '{' || !inString && c == '[' -> depth++
                    !inString && (c == '}' || c == ']') -> {
                        depth--
                        if (depth == 0) return
                    }
                }
            }
            throw ParseException(message = "嵌套结构未闭合（期望 $closer）")
        }

        private fun skipNumber() {
            if (peek() == '-') pos++
            while (pos < text.length && (text[pos].isDigit() || text[pos] in ".eE+-")) pos++
        }

        private fun requireLiteral(expected: String) {
            if (!text.startsWith(expected, pos)) {
                throw ParseException(message = "位置 $pos：非法字面量（期望 $expected）")
            }
            pos += expected.length
        }
    }
}
