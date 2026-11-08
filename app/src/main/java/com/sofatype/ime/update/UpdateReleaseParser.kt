// T21 · GitHub Releases 响应解析（决策 17：字段格式与 GitHub Releases 一致——
// tag = 版本名、assets 内 APK = 下载地址、release notes = 更新说明）。
// 纯 Kotlin 手写递归下降解析，只覆盖本场景所需子集（顶层数组 → 发布对象 → assets 数组），
// 未知字段跳过；不引第三方 JSON 库（工具链拍板，同 MinimalJson 先例）。
package com.sofatype.ime.update

/** 选定的最新可用发布条目。 */
data class UpdateRelease(
    /** 发布 tag（= 版本名，如「v0.2.0」）。 */
    val tag: String,
    /** 更新说明（release body），可为空串。 */
    val notes: String,
    /** APK 资产下载地址（首个 .apk 资产的 browser_download_url）。 */
    val apkUrl: String,
)

object UpdateReleaseParser {

    /** 解析失败；[reason] 区分格式非法与缺 APK 资产。 */
    class ParseException(
        val reason: UpdateFailureReason = UpdateFailureReason.PARSE,
        message: String,
    ) : Exception(message)

    /** 解析整份响应：取数组内首个非 draft、非 prerelease 的条目。 */
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

        /** 解析「[ {发布对象}, … ]」，返回首个可选发布条目；数组为空视为格式失败。 */
        fun parseDocument(): UpdateRelease {
            skipWhitespace()
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
                // 已找到可用条目时，剩余元素只做结构校验后跳过
                if (candidate != null) {
                    skipRemainingArrayElements()
                    return candidate
                }
            }
        }

        /** 条目找到后逐个跳过剩余元素（保持严格结构校验）。 */
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

        /** 解析一个发布对象；不可选（draft / prerelease / 空 tag）时返回 null。 */
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

        /** 解析 assets 数组，返回首个 .apk 资产的 browser_download_url（无则 null）。 */
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

        /** 解析单个资产对象，name 以 .apk 结尾（忽略大小写）时返回其 browser_download_url。 */
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
                    // 按 UTF-16 char 原样保留（与 MinimalJson 同口径）
                    code.toChar()
                }
                else -> throw ParseException(message = "非法转义字符：\\$c")
            }
        }

        /** 跳过任意一个 JSON 值（对象 / 数组 / 字符串 / 数字 / 字面量），未知字段用。 */
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
                    inString && c == '\\' -> if (pos < text.length) pos++ // 跳过转义对
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
