package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChineseComposerTest {

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

    @Test
    fun `退格删除组合串末位并刷新候选`() {
        val (c, fake) = composer()
        c.appendLetter('k')
        c.appendLetter('e')
        assertEquals("ke", c.composition)
        assertTrue(c.hasCandidates)
        assertTrue(c.deleteLastLetter())
        assertEquals("k", c.composition)

        assertFalse(c.hasCandidates)
        assertEquals(emptyList<String>(), c.candidates)
        assertEquals(listOf("k", "ke", "k"), fake.calls)
    }

    @Test
    fun `组合态外退格返回false不触发查询`() {
        val (c, fake) = composer()
        assertFalse(c.deleteLastLetter())
        assertEquals(0, fake.calls.size)

        c.appendLetter('k')
        assertTrue(c.deleteLastLetter())
        assertEquals("", c.composition)
        assertFalse(c.hasCandidates)
    }

    @Test
    fun `组合态内插入切分符并刷新候选`() {
        val (c, fake) = composer(mapOf("xian" to listOf("西安", "先"), "xi" to listOf("西")))
        c.appendLetter('x')
        c.appendLetter('i')
        assertTrue(c.appendSegment())
        assertEquals("xi'", c.composition)
        c.appendLetter('a')
        c.appendLetter('n')
        assertEquals("xi'an", c.composition)

        assertEquals(listOf("x", "xi", "xi", "xia", "xian"), fake.calls)
        assertEquals(listOf("西安", "先"), c.candidates)
    }

    @Test
    fun `无组合态插切分符被拒绝`() {
        val (c, fake) = composer()
        assertFalse(c.appendSegment())
        assertEquals("", c.composition)
        assertEquals(0, fake.calls.size)
    }

    @Test
    fun `查询与上屏串剥离切分符`() {
        val (c, _) = composer(mapOf("xian" to listOf("西安")))
        c.appendLetter('x')
        c.appendLetter('i')
        c.appendSegment()
        c.appendLetter('a')
        c.appendLetter('n')
        assertEquals("xi'an", c.composition)
        assertEquals("xian", c.queryInput())
        assertEquals("xian", c.committedText())

        assertTrue(c.deleteLastLetter())
        assertEquals("xi'a", c.composition)
        assertTrue(c.deleteLastLetter())
        assertEquals("xi'", c.composition)
        assertTrue(c.deleteLastLetter())
        assertEquals("xi", c.composition)
    }

    @Test
    fun `选词上屏与原样上屏均不落切分符`() {

        val (c, _) = composer(mapOf("xian" to listOf("西安", "先")))
        c.appendLetter('x')
        c.appendLetter('i')
        c.appendSegment()
        c.appendLetter('a')
        c.appendLetter('n')
        assertEquals("西安", c.firstCandidate())
        assertEquals("xian", c.committedText())

        c.appendLetter('q')
        assertEquals(listOf<String>(), c.candidates)
        assertEquals("xianq", c.committedText())
    }

    @Test
    fun `重置清空组合串与候选`() {
        val (c, _) = composer()
        repeat(6) { c.appendLetter('k') }
        c.reset()
        assertEquals("", c.composition)
        assertFalse(c.hasCandidates)
        assertNull(c.firstCandidate())
    }

    @Test
    fun `选词复位后重按同字母产生单字母组合`() {

        val (c, fake) = composer(mapOf("e" to listOf("额", "饿", "恶")))
        c.appendLetter('e')
        assertEquals(listOf("e"), fake.calls)
        c.reset()
        c.appendLetter('e')
        assertEquals("e", c.composition)
        assertEquals("复位后同字母重按应重新查询单字母组合", listOf("e", "e"), fake.calls)
    }

    @Test
    fun `删空组合后字母重新入组回到组合态`() {

        val (c, fake) = composer(
            mapOf(
                "ke" to listOf("客", "课"),
                "k" to emptyList(),
                "e" to listOf("额", "饿", "恶"),
            ),
        )
        c.appendLetter('k')
        c.appendLetter('e')
        assertEquals("ke", c.composition)
        assertEquals(listOf("客", "课"), c.candidates)

        assertTrue(c.deleteLastLetter())
        assertTrue(c.deleteLastLetter())
        assertEquals("", c.composition)
        assertFalse(c.hasCandidates)

        c.appendLetter('e')
        assertEquals("e", c.composition)
        assertEquals(listOf("额", "饿", "恶"), c.candidates)
        assertTrue(c.hasCandidates)
        assertEquals("删空后重按字母应重新查询单字母组合", listOf("k", "ke", "k", "e"), fake.calls)
    }

    @Test
    fun `删空后无候选字母仍入组不阻塞后续组合`() {

        val (c, fake) = composer(mapOf("f" to emptyList(), "fa" to listOf("发")))
        c.appendLetter('f')
        assertEquals("f", c.composition)
        assertFalse(c.hasCandidates)
        c.appendLetter('a')
        assertEquals("fa", c.composition)
        assertEquals(listOf("发"), c.candidates)
        assertEquals(listOf("f", "fa"), fake.calls)
    }
}
