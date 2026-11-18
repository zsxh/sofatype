package com.sofatype.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * 高频子集提取单测（T85 · 方向 C）。
 *
 * 覆盖：截断口径（频次降序 + 并列取源行序）、词表紧缩重映射、音节表全量保留、确定性、
 * SPD1 往返兼容，以及真词库产物的覆盖度口径（子集对高频样例输入的首键召回与全量一致）。
 */
class DictSubsetTest {

    /** 合成词库：音节 5 个，词条频次手工指定（音节组合仅作数据载体，不要求语言学成词） */
    private fun syntheticData(): DictData {
        val syllables = listOf("ni", "hao", "ke", "dian", "shi")
        val words = listOf("你", "好", "你好", "客", "客厅", "电视", "城市")
        fun ids(vararg idx: Int): IntArray = idx
        return DictData(
            syllables,
            words,
            listOf(
                DictData.Entry(ids(2, 0), 2, 100.0),  // 源行序 0
                DictData.Entry(ids(0), 0, 900.0),     // 源行序 1：你
                DictData.Entry(ids(0, 1), 2, 800.0),  // 源行序 2：你好（同词多词条之一）
                DictData.Entry(ids(1), 1, 700.0),     // 源行序 3：好
                DictData.Entry(ids(3, 4), 5, 600.0),  // 源行序 4：电视
                DictData.Entry(ids(2), 3, 500.0),     // 源行序 5：客
                DictData.Entry(ids(0, 1), 2, 400.0),  // 源行序 6：你好（同词多词条之二）
                DictData.Entry(ids(3, 4), 6, 300.0),  // 源行序 7：城市
            ),
        )
    }

    private fun entriesEquals(a: DictData, b: DictData) {
        assertEquals(a.syllables, b.syllables)
        assertEquals(a.words, b.words)
        assertEquals(a.entryCount, b.entryCount)
        for (i in 0 until a.entryCount) {
            assertEquals("词条 $i 频次一致", a.entries[i].freq, b.entries[i].freq, 0.0)
            assertEquals("词条 $i 词 id 一致", a.entries[i].wordId, b.entries[i].wordId)
            assertTrue("词条 $i 音节序列一致", a.entries[i].syllableIds.contentEquals(b.entries[i].syllableIds))
        }
    }

    @Test
    fun `按频次降序截断`() {
        val subset = syntheticData().topFreqSubset(4)
        assertEquals(4, subset.entryCount)
        // 全库频次降序前 4：900 你 / 800 你好 / 700 好 / 600 电视（100 那条被截掉）
        assertEquals(listOf(900.0, 800.0, 700.0, 600.0), subset.entries.map { it.freq })
    }

    @Test
    fun `频次并列取源行序`() {
        val data = syntheticData()
        // 追加一条与「好」(wordId 1) 同频 700 的新词条，源行序在后、指向不同词「客」(wordId 3)
        val tied = DictData(
            data.syllables,
            data.words,
            data.entries + DictData.Entry(data.entries[3].syllableIds, 3, 700.0),
        )
        val top4 = tied.topFreqSubset(4)
        assertEquals(listOf(900.0, 800.0, 700.0, 700.0), top4.entries.map { it.freq })
        // 先入选的是源行序在前的原「好」词条；源行序在后的同频词条排后
        assertEquals("好", top4.words[top4.entries[2].wordId])
        assertEquals("客", top4.words[top4.entries[3].wordId])
    }

    @Test
    fun `词表紧缩重映射_同词多词条共享新词id`() {
        val subset = syntheticData().topFreqSubset(4)
        // 子集引用词：你 / 你好 / 好 / 电视（重映射后随选取序排列）
        assertEquals(listOf("你", "你好", "好", "电视"), subset.words)
        // 「你好」两条词条（原词 id 2）重映射后指向同一新词 id
        val niHaoIds = subset.entries.filter { subset.words[it.wordId] == "你好" }.map { it.wordId }.toSet()
        assertEquals(1, niHaoIds.size)
    }

    @Test
    fun `音节表全量保留_与全量一致`() {
        val full = syntheticData()
        assertEquals(full.syllables, full.topFreqSubset(1).syllables)
        assertEquals(full.syllables, full.topFreqSubset(4).syllables)
    }

    @Test
    fun `确定性_同输入两次提取结果一致`() {
        entriesEquals(syntheticData().topFreqSubset(5), syntheticData().topFreqSubset(5))
    }

    @Test
    fun `SPD1往返兼容_子集可经流加载为引擎并查询`() {
        val subset = syntheticData().topFreqSubset(4)
        val bytes = ByteArrayOutputStream().also { DictFormat.write(subset, it) }.toByteArray()
        val loaded = PinyinEngine.load(ByteArrayInputStream(bytes))
        assertEquals(4, loaded.entryCount)
        // 双路查询通路：全拼前缀 ni 命中 你 / 你好，频次降序、无重复词
        val words = loaded.query("ni", limit = 20).map { it.word }
        assertEquals(words.size, words.toSet().size)
        assertEquals("你", words.first())
        assertTrue("你好" in words)
    }

    // ---- 真词库产物覆盖度口径（前提：./gradlew :pinyin:buildDict 已生成产物） ----

    private fun openProduct(): java.io.InputStream =
        DictSubsetTest::class.java.getResourceAsStream("/dict/pinyin_dict.bin")
            ?: throw AssertionError("词库产物不存在（classpath /dict/pinyin_dict.bin）。先运行：./gradlew :pinyin:buildDict")

    private fun loadFullEngine(): PinyinEngine = openProduct().use { PinyinEngine.load(it) }

    @Test
    fun `真词库子集规模与音节表口径`() {
        val full = loadFullEngine()
        val subset = openProduct().use { DictFormat.read(it) }.topFreqSubset(HIGH_FREQ_LIMIT)
        assertEquals(HIGH_FREQ_LIMIT, subset.entryCount)
        // 音节表全量保留：切分语义与全量产物一致
        assertEquals(full.syllableCount, subset.syllables.size)
        // 词表紧缩：子集词数远小于全量词数
        assertTrue("子集词数应小于全量词数", subset.words.size < full.entryCount)
    }

    @Test
    fun `真词库子集确定性`() {
        val data = openProduct().use { DictFormat.read(it) }
        entriesEquals(data.topFreqSubset(HIGH_FREQ_LIMIT), data.topFreqSubset(HIGH_FREQ_LIMIT))
    }

    @Test
    fun `真词库子集高频样例首键召回与全量一致`() {
        val full = loadFullEngine()
        val subset = PinyinEngine.fromData(openProduct().use { DictFormat.read(it) }.topFreqSubset(HIGH_FREQ_LIMIT))
        // 首键样例（1–2 键，首键场景主体）：子集高频覆盖深，前 5 与全量严格一致
        val firstKeySamples = listOf("z", "d", "n", "s", "w", "zg", "bj", "nh", "ni", "ke", "ma")
        for (input in firstKeySamples) {
            val expected = full.query(input, limit = 5).map { it.word }
            val actual = subset.query(input, limit = 5).map { it.word }
            assertEquals("输入 $input 子集前 5 应与全量一致", expected, actual)
        }
        // 前缀样例（≥3 键）：子集候选须为全量候选的前缀（序不变、只少低频尾部，全量后台补齐后恢复）。
        // 已实测数据事实：客厅/课堂等中频词频次排在全库 4000 名外，桥接窗内此类前缀（如 ket）
        // 子集候选可为空——此处只选子集覆盖的样例断言前缀性质。
        for (input in listOf("nih", "zhe", "zhong")) {
            val expected = full.query(input, limit = 5).map { it.word }
            val actual = subset.query(input, limit = 5).map { it.word }
            assertTrue("输入 $input 子集候选不应为空", actual.isNotEmpty())
            assertTrue(
                "输入 $input 子集候选应为全量候选前缀：actual=$actual expected=$expected",
                expected.size >= actual.size && expected.subList(0, actual.size) == actual,
            )
        }
    }

    @Test
    fun `真词库子集含单字兜底高频字与样例词`() {
        val subsetWords = openProduct().use { DictFormat.read(it) }.topFreqSubset(HIGH_FREQ_LIMIT).words.toSet()
        for (word in listOf("在", "的", "你", "是", "我", "这个", "中国", "北京", "你好")) {
            assertTrue("高频子集应含「$word」", word in subsetWords)
        }
    }

    companion object {
        /** 高频子集词条数（与 pinyin/build.gradle.kts 的 dictHighFreqEntryLimit 同口径） */
        private const val HIGH_FREQ_LIMIT = 4000
    }
}
