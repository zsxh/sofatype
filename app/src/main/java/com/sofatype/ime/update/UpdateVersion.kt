// T21 · 版本比较纯函数（决策 17：tag = 版本名）。
// 规则：去首字母 v/V 前缀；按「.」分段取各段前导数字按数值比较，缺段补 0，
// 段内无前导数字按 0（如「0-rc1」→ 0）。tag 旧于或等于当前版本 → 「已是最新」。
package com.sofatype.ime.update

object UpdateVersion {

    /** 负数 = release 更旧，0 = 相同，正数 = release 更新。 */
    fun compare(currentVersionName: String, releaseTag: String): Int {
        val current = segments(currentVersionName)
        val release = segments(releaseTag)
        val count = maxOf(current.size, release.size)
        for (i in 0 until count) {
            val a = current.getOrElse(i) { 0L }
            val b = release.getOrElse(i) { 0L }
            if (a != b) return b.compareTo(a)
        }
        return 0
    }

    private fun segments(version: String): List<Long> {
        val cleaned = version.trim().let { if (it.startsWith("v") || it.startsWith("V")) it.substring(1) else it }
        return cleaned.split('.').map { segment ->
            segment.takeWhile { it.isDigit() }.ifEmpty { "0" }.toLong()
        }
    }
}
