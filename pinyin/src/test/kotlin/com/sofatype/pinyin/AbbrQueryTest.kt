package com.sofatype.pinyin

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 简拼（首字母缩写）查询单测（T83 · FR-E1 · PRD_20261118 决策 D1-D2）。
 *
 * 小词库（频次为构造值）：
 * - shang+hai：上海 100（简拼 "sh"）
 * - bei / bai：呗 309.42 / 8019.99（同词多读音，简拼同为 "b"）
 * - shan：山 50（简拼 "s"）；s：嘶 10（全拼路单音节）
 * - s+ha：傻哈 20（全拼 "sha"、简拼 "sh"，双路可同时命中）
 * - chong / zhong：重 100 / 50（多读音不同简拼 "c" / "z"）
 * - zhong+guo：中国 60（简拼 "zg"）
 */
class AbbrQueryTest {

    private fun engine(): PinyinEngine {
        val syllables = listOf("bai", "bei", "chong", "guo", "ha", "hai", "s", "shan", "shang", "zhong")
        val words = listOf("上海", "呗", "山", "傻哈", "嘶", "重", "中国")
        val id = { w: String -> words.indexOf(w) }
        val syl = { s: String -> syllables.indexOf(s) }
        fun e(vararg seq: String, w: String, f: Double) =
            DictData.Entry(seq.map(syl).toIntArray(), id(w), f)

        val entries = listOf(
            e("shang", "hai", w = "上海", f = 100.0),
            e("bei", w = "呗", f = 309.42),
            e("bai", w = "呗", f = 8019.99),
            e("shan", w = "山", f = 50.0),
            e("s", w = "嘶", f = 10.0),
            e("s", "ha", w = "傻哈", f = 20.0),
            e("chong", w = "重", f = 100.0),
            e("zhong", w = "重", f = 50.0),
            e("zhong", "guo", w = "中国", f = 60.0),
        )
        return PinyinEngine.fromData(DictData(syllables, words, entries))
    }

    private fun wordsOf(engine: PinyinEngine, input: String, limit: Int = 20) =
        engine.query(input, limit).map { it.word }

    @Test
    fun `简拼串前缀命中词组`() {
        assertEquals(listOf("中国"), wordsOf(engine(), "zg"))
        assertEquals("简拼超出词条首字母串长度应无命中", emptyList<String>(), wordsOf(engine(), "zgr"))
    }

    @Test
    fun `两字母声母形态命中纯声母首音节词`() {
        // T84 起两字母输入不再恒空（一期此断言为「zh 双路皆空」，混合缩写路按声母形态命中）：
        // "zh" = zhong 整声母缩写 → 中国（词前缀边界命中）/ 重（zhong 读音词条）；
        // 全拼路（"zh"/"z" 均非合法音节）与简拼路（无 "zh" 开头首字母串）零命中，纯混合路贡献
        assertEquals(listOf("中国", "重"), wordsOf(engine(), "zh"))
        // "zr" 两字母下任何分段皆不可达（简拼无 "zr" 首字母串、无音节真前缀能吃满）→ 恒空
        assertEquals(emptyList<Candidate>(), engine().query("zr"))
    }

    @Test
    fun `三层级合并_全拼路排前_简拼次之_混合路殿后`() {
        // "sh"：全拼路命中傻哈（sha 前缀，频次 20），简拼路命中上海（100）与傻哈——
        // 全拼命中排前不按频次互换；傻哈两路同词只出现一次；
        // T84 混合路命中山（shan 整声母 "sh" 缩写，频次 50）殿后，两路旧词序不回归
        assertEquals(listOf("傻哈", "上海", "山"), wordsOf(engine(), "sh"))
    }

    @Test
    fun `单字母取简拼路超集按频次统一排序`() {
        // "s"：简拼路命中 上海/山/傻哈/嘶（全拼路仅后两者且频次更低）——
        // 单字母按频次统一排序，不把全拼组机械提前（嘶 殿后）
        val engine = engine()
        assertEquals(listOf("上海", "山", "傻哈", "嘶"), wordsOf(engine, "s"))
        assertEquals(100.0, engine.query("s").first().freq, 0.0)
        assertEquals(10.0, engine.query("s").last().freq, 0.0)
    }

    @Test
    fun `单字母多读音只按匹配读音计频`() {
        // "z" 只匹配重的 zhong 读音（50），chong 读音（100）不参与该输入排序
        val engine = engine()
        assertEquals(listOf("中国", "重"), wordsOf(engine, "z"))
        assertEquals(50.0, engine.query("z").last().freq, 0.0)
        assertEquals(listOf("重"), wordsOf(engine, "c"))
        assertEquals(100.0, engine.query("c").single().freq, 0.0)
    }

    @Test
    fun `同词多读音简拼去重取最高频次`() {
        val result = engine().query("b")
        assertEquals(listOf("呗"), result.map { it.word })
        assertEquals(8019.99, result.single().freq, 1e-9)
    }

    @Test
    fun `拼音无以iu v开头音节_恒无候选`() {
        val engine = engine()
        for (input in listOf("i", "u", "v")) {
            assertEquals("输入 \"$input\" 应无候选", emptyList<Candidate>(), engine.query(input))
        }
    }

    @Test
    fun `全拼查询行为不变`() {
        assertEquals(listOf("上海"), wordsOf(engine(), "shanghai"))
        // "shan"：全拼路命中山（shan 整音节）不变；T84 混合路增量命中上海（shan 为 shang 真前缀，
        // 全拼路只扫完整首音节桶看不见的形态）殿后
        assertEquals(listOf("山", "上海"), wordsOf(engine(), "shan"))
        // "sha"：全拼路首位傻哈（sha 真前缀）不变；混合路增量命中上海（s + ha 真前缀）与山（shan 真前缀）殿后
        assertEquals("全拼路首位不变，混合路增量殿后", listOf("傻哈", "上海", "山"), wordsOf(engine(), "sha"))
        assertEquals("组合态整词命中不回归", listOf("上海"), wordsOf(engine(), "shanghai", limit = 5))
    }

    @Test
    fun `limit 截断简拼候选`() {
        assertEquals(listOf("上海", "山"), wordsOf(engine(), "s", limit = 2))
        assertEquals(0, engine().query("s", limit = 0).size)
    }

    @Test
    fun `非法输入返回空列表`() {
        val engine = engine()
        for (bad in listOf("", "S", "SH", "s h", "sh2")) {
            assertEquals("输入 \"$bad\" 应无候选", emptyList<Candidate>(), engine.query(bad))
        }
    }
}
