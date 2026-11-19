package com.sofatype.ime

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class FocusContrastTest {

    private val bg = rgb(0x06080C)
    private val card = rgb(0x12161E)
    private val key = rgb(0x1D242F)
    private val keyHover = rgb(0x242C39)
    private val accent = rgb(0x38BDF8)
    private val accentText = rgb(0x7DD3FC)
    private val onAccent = rgb(0x052330)
    private val textPrimary = rgb(0xF2F6FB)
    private val textBody = rgb(0xE8EEF5)
    private val textSecondary = rgb(0xC9D4E2)
    private val borderAccent = argb(0x80, 0x38BDF8)
    private val accentWash = argb(0x24, 0x38BDF8)
    private val accentWashStrong = argb(0x47, 0x38BDF8)
    private val textFaint = argb(0x80, 0xE8EEF5)

    private fun rgb(argb: Int): Triple<Double, Double, Double> {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return Triple(r.toDouble(), g.toDouble(), b.toDouble())
    }

    private fun argb(alpha: Int, rgb: Int): Pair<Double, Triple<Double, Double, Double>> =
        alpha / 255.0 to rgb(rgb)

    private fun linear(channel: Double): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Triple<Double, Double, Double>): Double =
        0.2126 * linear(color.first) + 0.7152 * linear(color.second) + 0.0722 * linear(color.third)

    private fun contrast(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val (hi, lo) = if (la >= lb) la to lb else lb to la
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun over(
        fg: Pair<Double, Triple<Double, Double, Double>>,
        back: Triple<Double, Double, Double>,
    ): Triple<Double, Double, Double> {
        val (alpha, fgColor) = fg
        fun mix(f: Double, b: Double): Double = alpha * f + (1 - alpha) * b
        return Triple(
            mix(fgColor.first, back.first),
            mix(fgColor.second, back.second),
            mix(fgColor.third, back.third),
        )
    }

    private fun assertContrast(
        name: String,
        foreground: Triple<Double, Double, Double>,
        background: Triple<Double, Double, Double>,
        threshold: Double,
    ) {
        val ratio = contrast(foreground, background)
        assertTrue(
            "$name 对比度 %.2f:1 低于 WCAG 阈值 %.1f:1".format(ratio, threshold),
            ratio >= threshold,
        )
    }

    @Test
    fun `键帽焦点态文字对比度达标`() {

        val focusOnCard = over(accentWashStrong, card)

        assertContrast("键帽字符焦点 text_body on wash_strong@card", textBody, focusOnCard, 3.0)

        assertContrast("功能键焦点 text_secondary on wash_strong@card", textSecondary, focusOnCard, 4.5)

        assertContrast("上档激活图标 accent_text on wash_strong@card", accentText, focusOnCard, 3.0)

        assertContrast("状态键当前态 accent_text on key", accentText, key, 4.5)
    }

    @Test
    fun `主色淡底元素焦点态文字对比度达标`() {

        val focusOnCard = over(accentWashStrong, card)
        val focusOnBg = over(accentWashStrong, bg)

        assertContrast("候选 chip 焦点 text_body on wash_strong@card", textBody, focusOnCard, 4.5)
        assertContrast("完成键焦点 text_body on wash_strong@card", textBody, focusOnCard, 4.5)
        assertContrast("引导主按钮焦点 text_body on wash_strong@card", textBody, focusOnCard, 4.5)
        assertContrast("退出远程按钮焦点 text_body on wash_strong@card", textBody, focusOnCard, 4.5)

        assertContrast("设置页与测试页按钮焦点 text_body on wash_strong@bg", textBody, focusOnBg, 4.5)
    }

    @Test
    fun `状态键非当前态压暗字符对比度核查`() {

        assertContrast("状态键非当前态 text_faint on key", over(textFaint, key), key, 3.0)
    }

    @Test
    fun `焦点描边与承载底色对比度达标`() {

        val focusOnCard = over(accentWashStrong, card)
        assertContrast("焦点描边(border over wash_strong@card) vs card", over(borderAccent, focusOnCard), card, 3.0)
        assertContrast("焦点描边(border over card) vs card", over(borderAccent, card), card, 3.0)
    }

    @Test
    fun `手机H5页按钮文字对比度达标`() {

        assertContrast("H5 上屏按钮 on_accent on accent", onAccent, accent, 4.5)

        assertContrast("H5 次级按钮 text_secondary on key", textSecondary, key, 4.5)

        assertContrast("H5 重连按钮 accent_text on wash@bg", accentText, over(accentWash, bg), 4.5)

        assertContrast("H5 编辑卡正文 text_primary on card", textPrimary, card, 4.5)
    }
}
