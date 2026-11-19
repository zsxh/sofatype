package com.sofatype.pinyin.tools

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VerifyDictTest {

    private fun sourceFile(): File = File("dictsrc/rawdict_utf16_65105_freq.txt")

    private fun runVerify(path: String): IllegalStateException? = try {
        verifySourceFile(path)
        null
    } catch (e: IllegalStateException) {
        e
    }

    @Test
    fun `入库源文件校验通过`() {
        val f = sourceFile()
        assertTrue(
            "入库源文件不存在：${f.absolutePath}（T30 起源文件随仓库入库，不应缺失）",
            f.exists(),
        )

        val e = runVerify(f.absolutePath)
        assertTrue("入库源文件应校验通过，实际报错：${e?.message}", e == null)
    }

    @Test
    fun `源文件缺失时报错并含期望sha256`() {
        val missing = File.createTempFile("dict_missing", ".tmp").also { it.delete() }
        val e = runVerify(missing.absolutePath)
        assertNotNull("缺失文件应校验失败", e)
        val msg = e!!.message ?: ""
        assertTrue("报错应含「缺失」：$msg", "缺失" in msg)
        assertTrue("报错应含期望 sha256：$msg", EXPECTED_SHA256 in msg)
    }

    @Test
    fun `源文件被篡改时报错并含期望与实际sha256`() {
        val tampered = File.createTempFile("dict_tampered", ".txt")
        tampered.deleteOnExit()
        tampered.writeText("非词库内容")
        val e = runVerify(tampered.absolutePath)
        assertNotNull("被篡改文件应校验失败", e)
        val msg = e!!.message ?: ""
        assertTrue("报错应含「不一致」：$msg", "不一致" in msg)
        assertTrue("报错应含期望 sha256：$msg", EXPECTED_SHA256 in msg)
        assertTrue("报错应含实际 sha256 字段：$msg", "实际 sha256" in msg)
        assertTrue("实际 sha256 不应等于期望值", EXPECTED_SHA256 !in msg.substringAfter("实际 sha256"))
    }

    @Test
    fun `空文件按缺失处理`() {
        val empty = File.createTempFile("dict_empty", ".txt")
        empty.deleteOnExit()
        val e = runVerify(empty.absolutePath)
        assertNotNull("空文件应校验失败", e)
        val msg = e!!.message ?: ""
        assertTrue("报错应含「缺失」：$msg", "缺失" in msg)
    }
}
