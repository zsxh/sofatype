// T10 · 中文组合态状态机单元测试（JUnit 4，纯 Kotlin，不依赖 Android UI 与模拟器）。
// 锁定 FR-A4（字母入组/转小写/上限）、FR-A8（组合态候选随组合串刷新）、决策 8 R-2 判定项
//（hasCandidates / firstCandidate）、退格与重置语义。候选查询注入合成函数，不触真实词库。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseComposerTest {

    /** 合成查询：固定映射表 + 调用记录（断言刷新时机与入参）。 */
    private class FakeQuery(private val table: Map<String, List<String>>) {
        val calls = mutableListOf<String>()

        fun query(input: String): List<String> {
            calls.add(input)
            return table[input] ?: emptyList()
        }
    }

    private fun composer(table: Map<String, List<String>> = mapOf("ke" to listOf("客", "课"), "keting" to listOf("客厅", "客"))): Pair<ChineseComposer, FakeQuery> {
        val fake = FakeQuery(table)
        return ChineseComposer(fake::query) to fake
    }

    // ---- 字母入组（FR-A4） ----

    @Test
    fun `字母入组转小写并刷新候选`() {
        val (c, fake) = composer()
        c.appendLetter('K')
        assertEquals("k", c.composition)
        c.appendLetter('E')
        c.appendLetter('T')
        c.appendLetter('I')
        c.appendLetter('N')
        c.appendLetter('G')
        assertEquals("keting", c.composition)
        assertEquals(listOf("客厅", "客"), c.candidates)
        // 每次入组都刷新一次候选，且以全组合串为入参
        assertEquals(listOf("k", "ke", "ket", "keti", "ketin", "keting"), fake.calls)
    }

    @Test
    fun `组合态有候选时hasCandidates为真`() {
        val (c, _) = composer()
        c.appendLetter('k')
        c.appendLetter('e')
        assertTrue(c.hasCandidates)
        assertEquals("客", c.firstCandidate())
    }

    @Test
    fun `无命中时hasCandidates为假且首候选为空`() {
        val (c, _) = composer()
        c.appendLetter('x')
        c.appendLetter('q')
        assertFalse(c.hasCandidates)
        assertNull(c.firstCandidate())
        assertEquals("xq", c.composition)
    }

    @Test
    fun `组合串达到引擎上限后不再入组`() {
        val (c, fake) = composer(mapOf("a".repeat(20) to listOf("词")))
        repeat(25) { c.appendLetter('a') }
        assertEquals(com.sofatype.pinyin.PinyinEngine.MAX_INPUT_LEN, c.composition.length)
        assertEquals(com.sofatype.pinyin.PinyinEngine.MAX_INPUT_LEN, fake.calls.size)
    }

    // ---- 退格（组合态内） ----

    @Test
    fun `退格删除组合串末位并刷新候选`() {
        val (c, fake) = composer()
        c.appendLetter('k')
        c.appendLetter('e')
        assertEquals("ke", c.composition)
        assertTrue(c.hasCandidates)
        assertTrue(c.deleteLastLetter())
        assertEquals("k", c.composition)
        // 组合串变短后仍刷新一次候选（"k" 在合成表中无命中 → 候选清空）
        assertFalse(c.hasCandidates)
        assertEquals(emptyList<String>(), c.candidates)
        assertEquals(listOf("k", "ke", "k"), fake.calls)
    }

    @Test
    fun `组合态外退格返回false不触发查询`() {
        val (c, fake) = composer()
        assertFalse(c.deleteLastLetter())
        assertEquals(0, fake.calls.size)
        // 清空组合串后候选同步清空
        c.appendLetter('k')
        assertTrue(c.deleteLastLetter())
        assertEquals("", c.composition)
        assertFalse(c.hasCandidates)
    }

    // ---- 重置（上屏 / 切模式 / 键盘重建） ----

    @Test
    fun `重置清空组合串与候选`() {
        val (c, _) = composer()
        repeat(6) { c.appendLetter('k') }
        c.reset()
        assertEquals("", c.composition)
        assertFalse(c.hasCandidates)
        assertNull(c.firstCandidate())
    }
}
