// T71 · 决策 60 · 日志页动态文案（纯 Kotlin，单元测试锁定 LogUiTextTest；RemoteUiText 先例口径）。
// 用量/统计为动态组合文案，与静态 UI 文案（strings.xml）分层；数字格式此处统一收口。
package com.sofatype.ime.debug

object LogUiText {

    /** 查看页尾部截断提示前缀（查看页仅显示最新部分时的说明行，完整取证走 adb pull）。 */
    const val TAIL_NOTE = "（仅显示最新部分，完整内容经 adb 取证）\n"

    /** 用量数字：字节 → MB 一位小数（画板 14/15「0.8 MB」口径）。 */
    fun usedMbText(usedBytes: Long): String {
        val mb = usedBytes / (1024f * 1024f)
        return String.format(java.util.Locale.US, "%.1f", mb)
    }

    /** 行数数字：不带千分位（直接计数呈现）。 */
    fun lineCountText(lineCount: Long): String = lineCount.toString()

    /** 日志子页行 3 右侧用量提示（画板 14：「当前 0.8 MB / 2 MB · 轮转覆盖」）。 */
    fun usageHint(usedBytes: Long, maxMb: Int): String =
        "当前 ${usedMbText(usedBytes)} MB / ${DebugLogOptions.label(maxMb)} · 轮转覆盖"

    /** 查看页右侧统计（画板 15：「共 1842 行 · 0.8 MB / 2 MB」）。 */
    fun stats(lineCount: Long, usedBytes: Long, maxMb: Int): String =
        "共 ${lineCountText(lineCount)} 行 · ${usedMbText(usedBytes)} MB / ${DebugLogOptions.label(maxMb)}"
}
