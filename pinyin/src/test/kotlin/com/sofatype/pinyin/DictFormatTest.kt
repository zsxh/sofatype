package com.sofatype.pinyin

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** SPD1 精简格式读写单测 */
class DictFormatTest {

    private fun sampleData(): DictData {
        // 音节表：ke=0, ting=1, bei=2
        val entries = listOf(
            DictData.Entry(intArrayOf(0, 1), 0, 141.29744947),  // 客厅 = ke ting
            DictData.Entry(intArrayOf(0), 1, 523.526794166),    // 客 = ke
            DictData.Entry(intArrayOf(2), 2, 309.4219738),      // 呗 = bei
        )
        return DictData(listOf("ke", "ting", "bei"), listOf("客厅", "客", "呗"), entries)
    }

    @Test
    fun `写出再读回各字段一致`() {
        val data = sampleData()
        val buffer = ByteArrayOutputStream()
        DictFormat.write(data, buffer)
        val round = DictFormat.read(ByteArrayInputStream(buffer.toByteArray()))

        assertEquals(data.syllables, round.syllables)
        assertEquals(data.words, round.words)
        assertEquals(data.entryCount, round.entryCount)
        for (i in data.entries.indices) {
            assertArrayEquals(data.entries[i].syllableIds, round.entries[i].syllableIds)
            assertEquals(data.entries[i].wordId, round.entries[i].wordId)
            assertEquals(data.entries[i].freq, round.entries[i].freq, 0.0) // 频次位级一致
        }
    }

    @Test
    fun `同一数据两次写出字节级一致`() {
        val data = sampleData()
        val a = ByteArrayOutputStream().also { DictFormat.write(data, it) }.toByteArray()
        val b = ByteArrayOutputStream().also { DictFormat.write(data, it) }.toByteArray()
        assertArrayEquals(a, b)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `魔数不符拒绝读取`() {
        val bytes = ByteArrayOutputStream().also { DictFormat.write(sampleData(), it) }.toByteArray()
        bytes[1] = 'X'.code.toByte()
        DictFormat.read(ByteArrayInputStream(bytes))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `版本不符拒绝读取`() {
        val bytes = ByteArrayOutputStream().also { DictFormat.write(sampleData(), it) }.toByteArray()
        // int32 版本位于偏移 4..8，翻转其低位使版本 != 1
        bytes[7] = (bytes[7].toInt() xor 0x01).toByte()
        DictFormat.read(ByteArrayInputStream(bytes))
    }

    @Test
    fun `四字长词往返无损`() {
        val data = DictData(
            listOf("ou", "mei", "guo", "jia"),
            listOf("欧美国家"),
            listOf(DictData.Entry(intArrayOf(0, 1, 2, 3), 0, 141.29744947)),
        )
        val buffer = ByteArrayOutputStream()
        DictFormat.write(data, buffer)
        val round = DictFormat.read(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals("欧美国家", round.words[0])
        assertArrayEquals(intArrayOf(0, 1, 2, 3), round.entries[0].syllableIds)
    }
}
