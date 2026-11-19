package com.sofatype.pinyin

import org.junit.Assert.assertEquals
import org.junit.Test

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

        assertEquals(listOf("中国"), wordsOf(engine(), "zguo"))
        assertEquals(60.0, engine().query("zguo").single().freq, 0.0)

        assertEquals(listOf("中国"), wordsOf(engine(), "zgu"))
    }

    @Test
    fun `混合串超出词条覆盖返回空`() {

        assertEquals(emptyList<Candidate>(), engine().query("zguox"))
    }

    @Test
    fun `声母缩写位加全拼位混合命中`() {

        assertEquals(listOf("这里", "治理"), wordsOf(engine(), "zhli"))
        assertEquals(200.0, engine().query("zhli").first().freq, 0.0)

        assertEquals(listOf("这里", "治理"), wordsOf(engine(), "zhl"))

        assertEquals(listOf("治理"), wordsOf(engine(), "zhil"))

        assertEquals(80.0, engine().query("zhli").last().freq, 0.0)
    }

    @Test
    fun `声母缩写两字母即命中词前缀`() {

        assertEquals(listOf("这里", "治理", "中国"), wordsOf(engine(), "zh"))

        assertEquals(listOf("治理"), wordsOf(engine(), "ch"))
        assertEquals(90.0, engine().query("ch").single().freq, 0.0)
    }

    @Test
    fun `缩写缩写全拼三段混合命中`() {

        assertEquals(listOf("输入法"), wordsOf(engine(), "srfa"))
        assertEquals(100.0, engine().query("srfa").single().freq, 0.0)
    }

    @Test
    fun `一期纯简拼与全拼前缀不回归`() {

        assertEquals(listOf("输入法"), wordsOf(engine(), "srf"))
        assertEquals(listOf("输入法"), wordsOf(engine(), "sr"))
        assertEquals(listOf("这里", "治理"), wordsOf(engine(), "zl"))

        assertEquals(emptyList<Candidate>(), engine().query("srfr"))

        assertEquals(listOf("治理"), wordsOf(engine(), "zhili"))
        assertEquals(listOf("输入法"), wordsOf(engine(), "shurufa"))
    }

    @Test
    fun `混合路命中与两路同词去重`() {

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
