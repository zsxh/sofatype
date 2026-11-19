package com.sofatype.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream

class RealDictTest {

    private fun openProduct(): InputStream =
        RealDictTest::class.java.getResourceAsStream("/dict/pinyin_dict.bin")
            ?: throw AssertionError(
                "词库产物不存在（classpath /dict/pinyin_dict.bin）。先运行：./gradlew :pinyin:buildDict",
            )

    private fun loadEngine(): PinyinEngine = openProduct().use { PinyinEngine.load(it) }

    @Test
    fun `产物加载成功且规模与源词库一致`() {
        val engine = loadEngine()

        assertEquals(65_105, engine.entryCount)

        assertEquals(416, engine.syllableCount)
    }

    @Test
    fun `抽样整拼查询命中高频词`() {
        val engine = loadEngine()
        val samples = mapOf(
            "keting" to "客厅",
            "nihao" to "你好",
            "beijing" to "北京",
            "shanghai" to "上海",
            "xian" to "先",
            "xianwang" to null,
        )
        for ((input, expect) in samples) {
            val result = engine.query(input, limit = 100)
            if (expect != null) {
                assertTrue("输入 $input 应命中「$expect」，实际前 10：${result.take(10).map { it.word }}", expect in result.map { it.word })
            }
        }

        val xianWords = engine.query("xian", limit = 200).map { it.word }
        assertTrue("先" in xianWords)
        assertTrue("西安" in xianWords)
    }

    @Test
    fun `组合态前缀查询命中`() {
        val engine = loadEngine()

        assertTrue("keti 应增量命中「客厅」", "客厅" in engine.query("keti", limit = 100).map { it.word })
        assertTrue("nih 应增量命中「你好」", "你好" in engine.query("nih", limit = 100).map { it.word })
    }

    @Test
    fun `单字词兜底`() {
        val engine = loadEngine()
        val ke = engine.query("ke", limit = 50).map { it.word }

        assertTrue("输入 ke 应含单字候选，实际前 10：${ke.take(10)}", ke.take(10).any { it.length == 1 })
    }

    @Test
    fun `简拼_zg_这个与中国居前`() {
        val engine = loadEngine()
        val top = engine.query("zg", limit = 10).map { it.word }
        assertEquals("zg 前两名应为 这个/中国（PRD_20261118 FR-E1 立项调研口径），实际：$top", listOf("这个", "中国"), top.take(2))
    }

    @Test
    fun `简拼_bj_北京居前`() {
        val engine = loadEngine()
        val top = engine.query("bj", limit = 10).map { it.word }
        assertTrue("bj 前 5 应含「北京」，实际：$top", "北京" in top.take(5))
    }

    @Test
    fun `混合缩写_zguo_中国居首`() {
        val engine = loadEngine()
        val result = engine.query("zguo", limit = 10)
        assertEquals("zguo 首候选应为「中国」（T84 缩写位 + 全拼位混合形态），实际：${result.map { it.word }}", "中国", result.firstOrNull()?.word)

        assertEquals("中国", engine.query("zgu", limit = 10).firstOrNull()?.word)
    }

    @Test
    fun `简拼_srf_输入法居前`() {
        val engine = loadEngine()
        assertEquals("srf 首候选应为「输入法」（[shu,ru,fa] 纯简拼口径）", "输入法", engine.query("srf", limit = 10).firstOrNull()?.word)
    }

    @Test
    fun `声母形态_zhly_综合利用居首且声母形态新词入列`() {
        val engine = loadEngine()
        val top = engine.query("zhly", limit = 10).map { it.word }

        assertEquals("综合利用", top.firstOrNull())

        assertTrue("zhly 应含声母形态命中「张靓颖」，实际：$top", "张靓颖" in top.take(4))
    }

    @Test
    fun `声母缩写加全拼_zhli_这里居首`() {
        val engine = loadEngine()
        val top = engine.query("zhli", limit = 10).map { it.word }
        assertEquals("zhli 首候选应为「这里」（[zhe,li] zh 整声母 + li 全拼位），实际：$top", "这里", top.firstOrNull())
        assertTrue("zhli 应含「治理」（[zhi,li] 同形态），实际：$top", "治理" in top.take(10))
    }

    @Test
    fun `混合缩写合并无重复词`() {
        val engine = loadEngine()
        for (input in listOf("zguo", "zgu", "zhly", "zhli", "srfa")) {
            val words = engine.query(input, limit = 200).map { it.word }
            assertEquals("输入 $input 候选不应有重复词（三层级合并去重口径）", words.size, words.toSet().size)
        }
    }

    @Test
    fun `单字母即出首字母高频词`() {
        val engine = loadEngine()
        val cases = mapOf("z" to "在", "d" to "的", "n" to "你", "s" to "是", "w" to "我")
        for ((input, expect) in cases) {
            assertEquals("单字母 $input 首候选应为「$expect」（单字母简拼口径，决策 D2）", expect, engine.query(input).firstOrNull()?.word)
        }
    }

    @Test
    fun `拼音无以iu v开头音节_恒无候选`() {
        val engine = loadEngine()
        for (input in listOf("i", "u", "v", "iu")) {
            assertEquals("输入 $input 应无候选（拼音体系限制，非缺陷）", emptyList<Candidate>(), engine.query(input))
        }
    }

    @Test
    fun `双路合并无重复词且简拼路命中`() {
        val engine = loadEngine()
        for (input in listOf("ha", "nh", "zh", "ma")) {
            val words = engine.query(input, limit = 200).map { it.word }
            assertEquals("输入 $input 候选不应有重复词（全拼/简拼双路去重口径）", words.size, words.toSet().size)
            assertTrue("输入 $input 应有候选", words.isNotEmpty())
        }
        assertTrue("nh 应命中「你好」", "你好" in engine.query("nh", limit = 50).map { it.word })
    }

    @Test
    fun `候选频次组内单调不升`() {
        val engine = loadEngine()

        for (input in listOf("ke", "keting", "xian", "zhongwen", "nihao")) {
            val freqs = engine.query(input, limit = 20).map { it.freq }
            assertEquals("输入 $input 频次应降序", freqs, freqs.sortedDescending())
        }
        val zg = engine.query("zg", limit = 20).map { it.freq }
        assertEquals("zg 全为简拼路命中，频次应降序", zg, zg.sortedDescending())
    }

    @Test
    fun `查询性能量级佐证_单次查询不超过200毫秒`() {
        val engine = loadEngine()
        val inputs = listOf(
            "k", "ke", "ket", "keti", "keting", "n", "ni", "nihao", "x", "xi", "xian", "zhongwen",
            "zhuangzhuang", "z", "zg", "nh", "ma",

            "zguo", "zhly", "zhli", "srf", "srfa",
        )

        repeat(30) { engine.query(inputs[it % inputs.size]) }

        var maxMs = 0.0
        var totalMs = 0.0
        var count = 0
        repeat(50) {
            for (input in inputs) {
                val t0 = System.nanoTime()
                engine.query(input)
                val ms = (System.nanoTime() - t0) / 1_000_000.0
                maxMs = maxOf(maxMs, ms)
                totalMs += ms
                count++
            }
        }
        println("查询 ${count} 次，平均 ${"%.3f".format(totalMs / count)} ms，最大 ${"%.3f".format(maxMs)} ms")
        assertTrue("单次查询最大耗时 ${"%.3f".format(maxMs)} ms 超出 200 ms", maxMs < 200.0)
    }
}
