// T71 · 决策 60 · 日志行格式锁定：logcat 同构行（MM-dd HH:mm:ss.SSS P/TAG: message）与
// 级别字符映射（V/D/I/W/E/A）。时间戳注入式（纯 JVM 无 Android 依赖）。
package com.sofatype.ime.debug

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Test

class LogFileFormatterTest {

    @Test
    fun `行格式为 logcat 同构口径`() {
        val line = LogFileFormatter.formatLine("11-14 21:56:58.921", Log.DEBUG, "ImeKeyTrace", "focus a LEFT -> digit_3")
        assertEquals("11-14 21:56:58.921 D/ImeKeyTrace: focus a LEFT -> digit_3", line)
    }

    @Test
    fun `级别字符映射覆盖六档`() {
        assertEquals('V', LogFileFormatter.priorityChar(Log.VERBOSE))
        assertEquals('D', LogFileFormatter.priorityChar(Log.DEBUG))
        assertEquals('I', LogFileFormatter.priorityChar(Log.INFO))
        assertEquals('W', LogFileFormatter.priorityChar(Log.WARN))
        assertEquals('E', LogFileFormatter.priorityChar(Log.ERROR))
        assertEquals('A', LogFileFormatter.priorityChar(Log.ASSERT))
    }

    @Test
    fun `未知级别兜底为 A`() {
        assertEquals('A', LogFileFormatter.priorityChar(42))
    }

    @Test
    fun `tag 缺失兜底为连字符不产生 NPE`() {
        val line = LogFileFormatter.formatLine("01-01 00:00:00.000", Log.INFO, null, "msg")
        assertEquals("01-01 00:00:00.000 I/-: msg", line)
    }
}
