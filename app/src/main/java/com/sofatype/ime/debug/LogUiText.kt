package com.sofatype.ime.debug

object LogUiText {

    const val TAIL_NOTE = "（仅显示最新部分，完整内容经 adb 取证）\n"

    fun usedMbText(usedBytes: Long): String {
        val mb = usedBytes / (1024f * 1024f)
        return String.format(java.util.Locale.US, "%.1f", mb)
    }

    fun lineCountText(lineCount: Long): String = lineCount.toString()

    fun usageHint(usedBytes: Long, maxMb: Int): String =
        "当前 ${usedMbText(usedBytes)} MB / ${DebugLogOptions.label(maxMb)} · 轮转覆盖"

    fun stats(lineCount: Long, usedBytes: Long, maxMb: Int): String =
        "共 ${lineCountText(lineCount)} 行 · ${usedMbText(usedBytes)} MB / ${DebugLogOptions.label(maxMb)}"
}
