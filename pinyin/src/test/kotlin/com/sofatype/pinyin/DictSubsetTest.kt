package com.sofatype.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DictSubsetTest {

    private fun syntheticData(): DictData {
        val syllables = listOf("ni", "hao", "ke", "dian", "shi")
        val words = listOf("你", "好", "你好", "客", "客厅", "电视", "城市")
        fun ids(vararg idx: Int): IntArray = idx
        return DictData(
            syllables,
            words,
            listOf(
                DictData.Entry(ids(2, 0), 2, 100.0),
                DictData.Entry(ids(0), 0, 900.0),
                DictData.Entry(ids(0, 1), 2, 800.0),
                DictData.Entry(ids(1), 1, 700.0),
                DictData.Entry(ids(3, 4), 5, 600.0),
                DictData.Entry(ids(2), 3, 500.0),
                DictData.Entry(ids(0, 1), 2, 400.0),
                DictData.Entry(ids(3, 4), 6, 300.0),
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

        assertEquals(listOf(900.0, 800.0, 700.0, 600.0), subset.entries.map { it.freq })
    }

    @Test
    fun `频次并列取源行序`() {
        val data = syntheticData()

        val tied = DictData(
            data.syllables,
            data.words,
            data.entries + DictData.Entry(data.entries[3].syllableIds, 3, 700.0),
        )
        val top4 = tied.topFreqSubset(4)
        assertEquals(listOf(900.0, 800.0, 700.0, 700.0), top4.entries.map { it.freq })

        assertEquals("好", top4.words[top4.entries[2].wordId])
        assertEquals("客", top4.words[top4.entries[3].wordId])
    }

    @Test
    fun `词表紧缩重映射_同词多词条共享新词id`() {
        val subset = syntheticData().topFreqSubset(4)

        assertEquals(listOf("你", "你好", "好", "电视"), subset.words)

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

        val words = loaded.query("ni", limit = 20).map { it.word }
        assertEquals(words.size, words.toSet().size)
        assertEquals("你", words.first())
        assertTrue("你好" in words)
    }

    private fun openProduct(): java.io.InputStream =
        DictSubsetTest::class.java.getResourceAsStream("/dict/pinyin_dict.bin")
            ?: throw AssertionError("词库产物不存在（classpath /dict/pinyin_dict.bin）。先运行：./gradlew :pinyin:buildDict")

    private fun loadFullEngine(): PinyinEngine = openProduct().use { PinyinEngine.load(it) }

    @Test
    fun `真词库子集规模与音节表口径`() {
        val full = loadFullEngine()
        val subset = openProduct().use { DictFormat.read(it) }.topFreqSubset(HIGH_FREQ_LIMIT)
        assertEquals(HIGH_FREQ_LIMIT, subset.entryCount)

        assertEquals(full.syllableCount, subset.syllables.size)

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

        val firstKeySamples = listOf("z", "d", "n", "s", "w", "zg", "bj", "nh", "ni", "ke", "ma")
        for (input in firstKeySamples) {
            val expected = full.query(input, limit = 5).map { it.word }
            val actual = subset.query(input, limit = 5).map { it.word }
            assertEquals("输入 $input 子集前 5 应与全量一致", expected, actual)
        }

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

        private const val HIGH_FREQ_LIMIT = 4000
    }
}
