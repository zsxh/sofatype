package com.sofatype.ime.update

object UpdateVersion {

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
