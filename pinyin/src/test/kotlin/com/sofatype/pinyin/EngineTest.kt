package com.sofatype.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** 查询引擎单测：词组候选、单字兜底、组合态前缀、排序、多音去重（小词库） */
class EngineTest {

    /**
     * 小词库（频次为构造值）：
     * - ke ting：客厅 141.29 / 客厅里 3.0（三字词组）
     * - ke：客 523.5、课 88.8、柯 1.2（单字兜底）
     * - ting：听 300.0、厅 66.6
     * - ti：提 120.0、题 90.0
     * - xian：先 800.0；xi+an：西安 350.0（同输入两读音来源）
     * - bai/bei：呗 8019.99 / 309.42（同词多读音，候选去重取最高）
     */
    private fun engine(): PinyinEngine {
        val syllables = listOf("an", "bai", "bei", "ke", "li", "ti", "ting", "xi", "xian")
        val words = listOf("西安", "呗", "厅", "先", "客", "客厅", "客厅里", "柯", "课", "题", "提", "听")
        val id = { w: String -> words.indexOf(w) }
        val syl = { s: String -> syllables.indexOf(s) }
        fun e(vararg seq: String, w: String, f: Double) =
            DictData.Entry(seq.map(syl).toIntArray(), id(w), f)

        val entries = listOf(
            e("ke", "ting", w = "客厅", f = 141.29744947),
            e("ke", w = "客", f = 523.526794166),
            e("ke", w = "课", f = 88.8),
            e("ke", w = "柯", f = 1.2),
            e("ting", w = "听", f = 300.0),
            e("ting", w = "厅", f = 66.6),
            e("ti", w = "提", f = 120.0),
            e("ti", w = "题", f = 90.0),
            e("xian", w = "先", f = 800.0),
            e("xi", "an", w = "西安", f = 350.0),
            e("ke", "ting", "li", w = "客厅里", f = 3.0),
            e("bei", w = "呗", f = 309.4219738),
            e("bai", w = "呗", f = 8019.99283537),
        )
        val data = DictData(syllables, words, entries)
        return PinyinEngine.fromData(data)
    }

    private fun wordsOf(engine: PinyinEngine, input: String, limit: Int = 20) =
        engine.query(input, limit).map { it.word }

    @Test
    fun `整拼命中词组`() {
        val ws = wordsOf(engine(), "keting")
        assertTrue("应含「客厅」，实际：$ws", "客厅" in ws)
        assertTrue("应含「客厅里」，实际：$ws", "客厅里" in ws)
    }

    @Test
    fun `组合态前缀增量命中`() {
        // "keti" 是「客厅」全拼 keting 的前缀
        val ws = wordsOf(engine(), "keti")
        assertTrue("输入 keti 应增量命中「客厅」，实际：$ws", "客厅" in ws)
    }

    @Test
    fun `单字词兜底`() {
        val ws = wordsOf(engine(), "ke")
        assertTrue("应含单字「客」，实际：$ws", "客" in ws)
        assertTrue("应含单字「课」，实际：$ws", "课" in ws)
        assertTrue("应含单字「柯」，实际：$ws", "柯" in ws)
    }

    @Test
    fun `候选按词库内建频次降序`() {
        val result = engine().query("ke")
        assertEquals(
            listOf("客" to 523.526794166, "客厅" to 141.29744947, "课" to 88.8, "柯" to 1.2, "客厅里" to 3.0)
                .sortedByDescending { it.second }.map { it.first },
            result.map { it.word },
        )
        // 频次值随候选返回，取词库内建频次
        assertEquals(523.526794166, result.first { it.word == "客" }.freq, 0.0)
    }

    @Test
    fun `歧义输入命中两种读音来源`() {
        // xian：整体音节「先」+ 切分 xi+an「西安」
        val ws = wordsOf(engine(), "xian")
        assertTrue("应含「先」（xian），实际：$ws", "先" in ws)
        assertTrue("应含「西安」（xi an），实际：$ws", "西安" in ws)
        assertEquals("先", ws.first()) // 频次更高（800 > 350）
    }

    @Test
    fun `同词多读音去重且取最高频次`() {
        val result = engine().query("bei")
        assertEquals(listOf("呗"), result.map { it.word })
        // bei 读 309.42；同词 bai 读频次 8019.99 不参与 bei 输入的候选排序
        assertEquals(309.4219738, result[0].freq, 1e-9)
    }

    @Test
    fun `limit 截断候选数`() {
        assertEquals(2, engine().query("ke", limit = 2).size)
        assertEquals(0, engine().query("ke", limit = 0).size)
    }

    @Test
    fun `频次相同时按词条序稳定`() {
        val syllables = listOf("a", "b")
        val data = DictData(
            syllables,
            listOf("乙", "甲"),
            listOf(
                DictData.Entry(intArrayOf(0), 0, 100.0), // 乙，词条序 0
                DictData.Entry(intArrayOf(0), 1, 100.0), // 甲，词条序 1
            ),
        )
        val result = PinyinEngine.fromData(data).query("a")
        assertEquals(listOf("乙", "甲"), result.map { it.word }) // 同频按源行序
    }

    @Test
    fun `非法输入返回空列表`() {
        val engine = engine()
        for (bad in listOf("", "KETING", "ke ting", "ke1", "zzz")) {
            assertEquals("输入 \"$bad\" 应无候选", emptyList<Candidate>(), engine.query(bad))
        }
    }

    @Test
    fun `超长输入返回空列表`() {
        assertEquals(emptyList<Candidate>(), engine().query("a".repeat(PinyinEngine.MAX_INPUT_LEN + 1)))
    }

    @Test
    fun `SPD1 流加载与内存构建行为一致`() {
        val syllables = listOf("ke", "ting")
        val data = DictData(
            syllables,
            listOf("客厅"),
            listOf(DictData.Entry(intArrayOf(0, 1), 0, 141.29744947)),
        )
        val buffer = ByteArrayOutputStream()
        DictFormat.write(data, buffer)
        val loaded = PinyinEngine.load(ByteArrayInputStream(buffer.toByteArray()))
        assertEquals(1, loaded.entryCount)
        assertEquals(2, loaded.syllableCount)
        assertEquals(listOf("客厅"), loaded.query("keting").map { it.word })
        assertEquals(listOf("客厅"), loaded.query("keti").map { it.word })
        assertEquals(emptyList<Candidate>(), loaded.query("ting"))
    }
}
