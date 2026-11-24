package com.sofatype.ime.update

/**
 * T103 · GitHub Releases Atom 订阅源解析（github.com 网页侧通道，无 API 限额、代理可达）。
 * 只取首个 entry（feed 按新到旧排序）＝最新发布；tag 来自 title，说明来自 content
 * （GitHub 输出为 HTML 转义形态，清洗为纯文本后供 UI 呈现）。
 * 下载地址不来自 feed：按 T100 资产命名口径构造（releases/download/<tag>/sofatype-<版本>-unsigned.apk）。
 */
object UpdateAtomParser {

    data class Entry(
        val tag: String,
        val notes: String,
    )

    class ParseException(
        val reason: UpdateFailureReason = UpdateFailureReason.PARSE,
        message: String,
    ) : Exception(message)

    const val RELEASES_DOWNLOAD_BASE = "https://github.com/zsxh/sofatype/releases/download/"

    fun parse(text: String): Entry {
        val entryStart = text.indexOf("<entry>")
        if (entryStart < 0) throw ParseException(message = "feed 无 entry（无已发布 release）")
        val entryEnd = text.indexOf("</entry>", entryStart)
        if (entryEnd < 0) throw ParseException(message = "entry 未闭合")
        val entry = text.substring(entryStart, entryEnd)

        val tag = extractChild(entry, "<title>", "</title>")?.trim().orEmpty()
        if (tag.isEmpty()) throw ParseException(message = "entry 缺 title（tag）")

        val notesRaw = extractChild(entry, "<content", "</content>") ?: ""
        return Entry(tag = tag, notes = cleanHtml(notesRaw))
    }

    fun downloadUrl(tag: String, proxyPrefix: String): String {
        val clean = tag.trim()
        if (clean.isEmpty()) throw ParseException(message = "tag 为空，无法构造下载地址")
        val version = clean.removePrefix("v").removePrefix("V")
        return "${proxyPrefix}${RELEASES_DOWNLOAD_BASE}$clean/sofatype-$version-unsigned.apk"
    }

    private fun extractChild(text: String, openTag: String, closeTag: String): String? {
        val start = text.indexOf(openTag)
        if (start < 0) return null
        // openTag 可能带属性（如 <content type="html">），从标签结束符之后取内容
        val contentStart = text.indexOf('>', start)
        if (contentStart < 0) return null
        val bodyStart = contentStart + 1
        val end = text.indexOf(closeTag, bodyStart)
        if (end < 0) throw ParseException(message = "标签未闭合：$openTag")
        return text.substring(bodyStart, end)
    }

    private fun cleanHtml(html: String): String =
        html
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("<[^>]*>"), "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
}
