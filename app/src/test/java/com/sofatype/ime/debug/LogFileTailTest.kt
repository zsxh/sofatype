// T71 · 决策 60 · 文件尾部截取锁定（环形语义「保留最新」的落地单元）：
// 尾部对齐整行、文件小于保留量原样返回、就地轮转保留最新丢弃最早。真实临时文件直跑（纯 JVM）。
package com.sofatype.ime.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LogFileTailTest {

    private fun tempFile(content: String): File =
        File.createTempFile("t71-log-tail", ".log").apply {
            deleteOnExit()
            writeText(content)
        }

    @Test
    fun `尾部保留量落在行首对齐不产生半行`() {
        // 6 行，每行 "line-N\n"（7 字节，共 42）；保留 10 字节 → 窗口起点落在 line-5 行内，
        // 该半行须被裁掉（保留最新且首行完整）
        val file = tempFile((1..6).joinToString("") { "line-$it\n" })
        val kept = LogFileTail.tailBytesAligned(file, keepBytes = 10)
        val keptText = String(kept, Charsets.UTF_8)
        assertEquals("line-6\n", keptText)
    }

    @Test
    fun `保留量不小于文件时原样返回`() {
        val file = tempFile("line-1\nline-2\n")
        val kept = LogFileTail.tailBytesAligned(file, keepBytes = file.length())
        assertEquals("line-1\nline-2\n", String(kept, Charsets.UTF_8))
        val keptMore = LogFileTail.tailBytesAligned(file, keepBytes = file.length() + 100)
        assertEquals("line-1\nline-2\n", String(keptMore, Charsets.UTF_8))
    }

    @Test
    fun `空文件与非正值保留量返回空`() {
        val file = tempFile("")
        assertEquals(0, LogFileTail.tailBytesAligned(file, keepBytes = 100).size)
        assertEquals(0, LogFileTail.tailBytesAligned(tempFile("abc\n"), keepBytes = 0).size)
    }

    @Test
    fun `就地轮转保留最新丢弃最早且单文件语义`() {
        val file = tempFile((1..10).joinToString("") { "row-$it\n" })
        val keptBytes = LogFileTail.rewriteKeepingTail(file, keepBytes = 20)
        // 文件仍存在且变小（单文件环形覆盖，无第二个文件残留）
        assertTrue(file.exists())
        val after = file.readText()
        assertEquals(keptBytes, file.length().also { assertTrue(it > 0 && it < 60) })
        // 最新两行保留、最早行丢弃
        assertTrue("最新行 row-10 应保留", after.contains("row-10\n"))
        assertTrue("最早行 row-1 应丢弃", !after.contains("row-1\n"))
        // 保留内容首行完整（对齐行首）
        assertTrue("保留内容应以完整行开头", after.startsWith("row-"))
    }

    @Test
    fun `轮转后保留内容为原文件尾部子串`() {
        val file = tempFile("aaa\nbbb\nccc\nddd\n")
        LogFileTail.rewriteKeepingTail(file, keepBytes = 8)
        val after = file.readText()
        assertEquals("ccc\nddd\n", after)
    }

    @Test
    fun `切割点恰在行边界时首行不丢弃`() {
        // "aaa\nbbb\n"（8 字节），保留 4 → 切割点 4 恰在 'b' 行首（前一字节为 \n）→ 原样保留 "bbb\n"
        val file = tempFile("aaa\nbbb\n")
        assertEquals("bbb\n", String(LogFileTail.tailBytesAligned(file, keepBytes = 4)))
    }

    @Test
    fun `窗口内无完整行时保留空`() {
        // 单行超长（无换行）超出保留量：无可保留整行 → 空
        val file = tempFile("abcdefghij")
        assertEquals(0, LogFileTail.tailBytesAligned(file, keepBytes = 4).size)
    }
}
