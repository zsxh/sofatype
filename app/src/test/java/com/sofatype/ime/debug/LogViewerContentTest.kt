package com.sofatype.ime.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LogViewerContentTest {

    private fun tempFile(content: String?): File =
        File.createTempFile("t71-log-view", ".log").apply {
            deleteOnExit()
            content?.let { writeText(it) }
        }

    @Test
    fun `文件缺失为空结果且读取成功`() {
        val missing = File("/nonexistent-t71/${System.nanoTime()}.log")
        val data = LogViewerContent.read(missing)
        assertTrue(data.ok)
        assertEquals(0, data.lineCount)
        assertEquals(0, data.usedBytes)
        assertFalse(data.truncated)
    }

    @Test
    fun `行数按换行计数`() {
        val file = tempFile("a\nb\nc\n")
        val data = LogViewerContent.read(file)
        assertEquals(3, data.lineCount)
        assertEquals(6, data.usedBytes)
        assertEquals("a\nb\nc\n", data.displayText)
        assertFalse(data.truncated)
    }

    @Test
    fun `空文件行为数为零不截断`() {
        val data = LogViewerContent.read(tempFile(""))
        assertEquals(0, data.lineCount)
        assertFalse(data.truncated)
        assertEquals("", data.displayText)
    }

    @Test
    fun `超上限只保留最新尾部并前置说明行`() {

        val content = (1..10).joinToString("") { "row-%05d\n".format(it) }
        val data = LogViewerContent.read(tempFile(content), tailCapBytes = 30)
        assertTrue(data.truncated)
        assertEquals(10, data.lineCount)
        assertEquals(100, data.usedBytes)
        assertTrue("应前置截断说明", data.displayText.startsWith(LogUiText.TAIL_NOTE))

        assertTrue(data.displayText.contains("row-00010"))
        assertFalse(data.displayText.contains("row-00001"))
    }

    @Test
    fun `恰好等于上限不截断`() {
        val content = "abcdef\n"
        val data = LogViewerContent.read(tempFile(content), tailCapBytes = 7)
        assertFalse(data.truncated)
        assertEquals(content, data.displayText)
    }
}
