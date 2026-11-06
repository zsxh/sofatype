package com.sofatype.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 拼音切分器单测（音节表以合成数据为准，覆盖歧义与非法输入） */
class SegmenterTest {

    private val segmenter = PinyinSegmenter(
        setOf("a", "an", "ba", "bai", "bo", "ke", "ken", "ting", "ti", "xian", "xi", "an", "fang", "fan", "gan", "zhuang", "zhu", "ang"),
    )

    @Test
    fun `无歧义串切分为单一方案`() {
        assertEquals(listOf(listOf("ke", "ting")), segmenter.segmentAll("keting"))
    }

    @Test
    fun `歧义串产出全部切分方案`() {
        // xian 可整体成音节，也可切 xi + an
        val plans = segmenter.segmentAll("xian")
        assertEquals(setOf(listOf("xian"), listOf("xi", "an")), plans.toSet())
    }

    @Test
    fun `三向歧义串全部枚举`() {
        // fangan = fang+an = fan+gan
        val plans = segmenter.segmentAll("fangan")
        assertEquals(setOf(listOf("fang", "an"), listOf("fan", "gan")), plans.toSet())
    }

    @Test
    fun `首音节长优先`() {
        assertEquals(listOf("xian", "xi"), segmenter.firstSyllables("xian"))
        assertEquals(listOf("ke"), segmenter.firstSyllables("keting"))
    }

    @Test
    fun `含非法子串的输入无切分方案`() {
        // "kz" 无合法音节
        assertTrue(segmenter.segmentAll("kz").isEmpty())
        assertTrue(segmenter.firstSyllables("kz").isEmpty())
    }

    @Test
    fun `部分可切分时保留合法前缀方案`() {
        // "kexq"：ke 合法，xq 无法切分 → 无完整方案
        assertTrue(segmenter.segmentAll("kexq").isEmpty())
    }

    @Test
    fun `非法输入返回空`() {
        for (bad in listOf("", "Ke", "ke ting", "ke1", "ke-ting")) {
            assertTrue("输入 \"$bad\" 应无方案", segmenter.segmentAll(bad).isEmpty())
            assertTrue("输入 \"$bad\" 应无首音节", segmenter.firstSyllables(bad).isEmpty())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `空音节表拒绝构建`() {
        PinyinSegmenter(emptySet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `含非法字符的音节表拒绝构建`() {
        PinyinSegmenter(setOf("ke", "T1"))
    }

    @Test
    fun `超过六字母的连续串无法整体成音节但可逐段切分`() {
        // 音节表含 "a"，"aaaa" 逐段全切
        assertEquals(listOf(listOf("a", "a", "a", "a")), segmenter.segmentAll("aaaa"))
    }

    @Test
    fun `切分方案数上限保护生效`() {
        // 全 "a" 长串组合数指数级，枚举数不超过 MAX_PLANS
        val plans = segmenter.segmentAll("a".repeat(20))
        assertTrue(plans.size <= PinyinSegmenter.MAX_PLANS)
    }
}
