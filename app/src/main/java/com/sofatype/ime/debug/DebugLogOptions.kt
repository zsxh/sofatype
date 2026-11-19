package com.sofatype.ime.debug

object DebugLogOptions {

    val MB_CHOICES: IntArray = intArrayOf(1, 2, 5, 10)

    const val DEFAULT_MB = 2

    fun bytesFor(mb: Int): Long = mb.toLong() * 1024L * 1024L

    fun clampMb(rawMb: Int): Int = MB_CHOICES.firstOrNull { it >= rawMb } ?: MB_CHOICES.last()

    fun label(mb: Int): String = "$mb MB"
}
