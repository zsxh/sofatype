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

    // ---- T66 · 决策 51-② 分词切分符 ----

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
        // 查询入参剥离切分符（引擎只接受纯小写字母串）
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
        // 退格先删切分符
        assertTrue(c.deleteLastLetter())
        assertEquals("xi'a", c.composition)
        assertTrue(c.deleteLastLetter())
        assertEquals("xi'", c.composition)
        assertTrue(c.deleteLastLetter())
        assertEquals("xi", c.composition)
    }

    @Test
    fun `选词上屏与原样上屏均不落切分符`() {
        // 决策 51-② 三细节之②③的模型侧口径：选词走 firstCandidate（候选与切分符无关），
        // 原样上屏走 committedText（剥离切分符）——服务层经区域替换 / setComposingText 承载
        val (c, _) = composer(mapOf("xian" to listOf("西安", "先")))
        c.appendLetter('x')
        c.appendLetter('i')
        c.appendSegment()
        c.appendLetter('a')
        c.appendLetter('n')
        assertEquals("西安", c.firstCandidate())
        assertEquals("xian", c.committedText())
        // 无候选组合串（合成表无 "xianq"）同样剥离后原样上屏
        c.appendLetter('q')
        assertEquals(listOf<String>(), c.candidates)
        assertEquals("xianq", c.committedText())
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

    // ---- T44 · 选词复位后重按同字母不叠加（T27 缺陷 A 语义锁定） ----

    @Test
    fun `选词复位后重按同字母产生单字母组合`() {
        // 缺陷 A 观测语义:选词上屏后 composer 残留致再按同字母装载「ee」混入正文。
        // 本用例锁定模型侧语义:复位后的同字母重按必须产生全新单字母组合。
        // 服务层根因(迟到确认键 UP 幽灵点击)由服务层消费修复,真机回归随 T27 Round 2。
        val (c, fake) = composer(mapOf("e" to listOf("额", "饿", "恶")))
        c.appendLetter('e')
        assertEquals(listOf("e"), fake.calls)
        c.reset() // = 服务层 selectCandidate 的复位语义
        c.appendLetter('e')
        assertEquals("e", c.composition)
        assertEquals("复位后同字母重按应重新查询单字母组合", listOf("e", "e"), fake.calls)
    }

    // ---- T45 · 删除键删空组合后字母重新入组（T27 缺陷 B 原始路径锁定） ----

    @Test
    fun `删空组合后字母重新入组回到组合态`() {
        // 缺陷 B 验收序列模型侧锁定:组合若干字母 → 删除键逐位删空 → 再按字母必须产生
        // 全新单字母组合并重新查询候选（无需任何语言切换等恢复操作）。
        // 服务层路径 = performDelete 逐位删至空后再 appendLetter，模拟器网格通道走查随 T45。
        val (c, fake) = composer(
            mapOf(
                "ke" to listOf("客", "课"),
                "k" to emptyList(), // 声母独用无候选（真机缺陷 B 误判签名对应的引擎口径）
                "e" to listOf("额", "饿", "恶"),
            ),
        )
        c.appendLetter('k')
        c.appendLetter('e')
        assertEquals("ke", c.composition)
        assertEquals(listOf("客", "课"), c.candidates)
        // 删除键删空组合串：两次退格均命中组合态（返回 true）
        assertTrue(c.deleteLastLetter())
        assertTrue(c.deleteLastLetter())
        assertEquals("", c.composition)
        assertFalse(c.hasCandidates)
        // 删空后再按字母：回到组合态，产生全新单字母组合
        c.appendLetter('e')
        assertEquals("e", c.composition)
        assertEquals(listOf("额", "饿", "恶"), c.candidates)
        assertTrue(c.hasCandidates)
        assertEquals("删空后重按字母应重新查询单字母组合", listOf("k", "ke", "k", "e"), fake.calls)
    }

    @Test
    fun `删空后无候选字母仍入组不阻塞后续组合`() {
        // T45 残留症状场景:删空后先按声母独用字母（引擎无候选，组合态黑盒不可辨），
        // 再按有效音节字母必须正常叠加入组——组合态不因无候选序列损坏。
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
