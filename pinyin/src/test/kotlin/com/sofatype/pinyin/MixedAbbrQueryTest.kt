package com.sofatype.pinyin

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 混合缩写（逐音节「全拼 / 缩写」混合）查询单测（T84 · PRD_20261118 决策 D6）。
 *
 * 小词库（频次为构造值）：
 * - zhong+guo：中国 60（zguo / zgu / zh 声母形态命中）
 * - zhi+li：治理 80 / chi+li：治理 90（同词多读音，混合路只按命中读音计频）
 * - zhe+li：这里 200（zhli 声母缩写 + 全拼位首位）
 * - shu+ru+fa：输入法 100（srf 纯简拼一期口径 / srfa 缩+缩+全拼三段混合）
 * - bei+le：呗了 7 / bai+le：呗了 5（多读音只按匹配读音参与排序）
 */
class MixedAbbrQueryTest {

    private fun engine(): PinyinEngine {
        val syllables = listOf("bai", "bei", "chi", "fa", "guo", "le", "li", "ru", "shu", "zhe", "zhi", "zhong")
        val words = listOf("中国", "呗了", "治理", "这里", "输入法")
        val id = { w: String -> words.indexOf(w) }
        val syl = { s: String -> syllables.indexOf(s) }
        fun e(vararg seq: String, w: String, f: Double) =
            DictData.Entry(seq.map(syl).toIntArray(), id(w), f)

        val entries = listOf(
            e("zhi", "li", w = "治理", f = 80.0),
            e("chi", "li", w = "治理", f = 90.0),
            e("zhe", "li", w = "这里", f = 200.0),
            e("shu", "ru", "fa", w = "输入法", f = 100.0),
            e("zhong", "guo", w = "中国", f = 60.0),
            e("bei", "le", w = "呗了", f = 7.0),
            e("bai", "le", w = "呗了", f = 5.0),
        )
        return PinyinEngine.fromData(DictData(syllables, words, entries))
    }

    private fun wordsOf(engine: PinyinEngine, input: String, limit: Int = 20) =
        engine.query(input, limit).map { it.word }

    @Test
    fun `缩写位加全拼位混合命中`() {
        // "zguo"：z = zhong 首字母缩写 + guo 整音节（一期两路皆零命中，混合路专属形态）
        assertEquals(listOf("中国"), wordsOf(engine(), "zguo"))
        assertEquals(60.0, engine().query("zguo").single().freq, 0.0)
        // "zgu"：末音节真前缀增量（组合态中途形态）同命中
        assertEquals(listOf("中国"), wordsOf(engine(), "zgu"))
    }

    @Test
    fun `混合串超出词条覆盖返回空`() {
        // "zguox"：guo 吃满后残留 x 无法落到任何音节位 → 无候选
        assertEquals(emptyList<Candidate>(), engine().query("zguox"))
    }

    @Test
    fun `声母缩写位加全拼位混合命中`() {
        // "zhli"：zh = zhe/zhi 整声母缩写 + li 整音节；一期两路皆零命中
        assertEquals(listOf("这里", "治理"), wordsOf(engine(), "zhli"))
        assertEquals(200.0, engine().query("zhli").first().freq, 0.0)
        // "zhl"：末音节真前缀增量同命中
        assertEquals(listOf("这里", "治理"), wordsOf(engine(), "zhl"))
        // "zhil"：全拼路命中治理（zhili 真前缀）；"il" 非任何音节前缀，混合路零新增
        assertEquals(listOf("治理"), wordsOf(engine(), "zhil"))
        // 多读音按命中读音计频：zhli 只命中治理 zhi 读音（80），chi 读音（90）不参与
        assertEquals(80.0, engine().query("zhli").last().freq, 0.0)
    }

    @Test
    fun `声母缩写两字母即命中词前缀`() {
        // "zh"：zhe/zhi/zhong 的整声母缩写，音节边界词前缀命中（一期两路皆空）
        assertEquals(listOf("这里", "治理", "中国"), wordsOf(engine(), "zh"))
        // "ch"：chi 整声母缩写，只命中治理 chi 读音（90）
        assertEquals(listOf("治理"), wordsOf(engine(), "ch"))
        assertEquals(90.0, engine().query("ch").single().freq, 0.0)
    }

    @Test
    fun `缩写缩写全拼三段混合命中`() {
        // "srfa"：s/r 首字母缩写 + fa 整音节；一期简拼路只认 "srf" 前缀、全拼路认 "shurufa" 前缀，
        // "srfa" 两路皆零命中，混合路命中
        assertEquals(listOf("输入法"), wordsOf(engine(), "srfa"))
        assertEquals(100.0, engine().query("srfa").single().freq, 0.0)
    }

    @Test
    fun `一期纯简拼与全拼前缀不回归`() {
        // 纯简拼："srf" / "sr" / "zl" 简拼路命中（混合路同词剔重，结果不变）
        assertEquals(listOf("输入法"), wordsOf(engine(), "srf"))
        assertEquals(listOf("输入法"), wordsOf(engine(), "sr"))
        assertEquals(listOf("这里", "治理"), wordsOf(engine(), "zl"))
        // 简拼超出首字母串长度仍无命中
        assertEquals(emptyList<Candidate>(), engine().query("srfr"))
        // 全拼前缀："zhili" / "shurufa" 全拼路命中
        assertEquals(listOf("治理"), wordsOf(engine(), "zhili"))
        assertEquals(listOf("输入法"), wordsOf(engine(), "shurufa"))
    }

    @Test
    fun `混合路命中与两路同词去重`() {
        // "zl"：简拼路与混合路同时命中 这里/治理，各只出现一次
        val result = engine().query("zl")
        assertEquals(listOf("这里", "治理"), result.map { it.word })
        assertEquals(200.0, result.first().freq, 0.0)
    }

    @Test
    fun `拼音无以iu v开头音节_恒无候选`() {
        val engine = engine()
        for (input in listOf("i", "u", "v", "iuv")) {
            assertEquals("输入 \"$input\" 应无候选", emptyList<Candidate>(), engine.query(input))
        }
    }

    @Test
    fun `limit 截断混合候选`() {
        assertEquals(listOf("这里"), wordsOf(engine(), "zhli", limit = 1))
        assertEquals(0, engine().query("zhli", limit = 0).size)
    }

    @Test
    fun `非法输入返回空列表`() {
        val engine = engine()
        for (bad in listOf("", "ZGUO", "zh li", "zh2", "zguo!")) {
            assertEquals("输入 \"$bad\" 应无候选", emptyList<Candidate>(), engine.query(bad))
        }
    }
}
