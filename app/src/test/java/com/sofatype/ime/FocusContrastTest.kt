// T26 · 焦点态对比度核查清单单元测试（PRD 第 8 章无障碍「焦点态满足对比度要求」+ 6.4 状态与焦点）。
// 口径：WCAG 2.1 相对亮度对比度——普通文字 ≥ 4.5:1；大文字（≥ 24px / ≥ 18.66px bold）与
// 非 UI 文字组件（焦点描边）≥ 3:1。TV 1080p 基准：键帽字符 30px 属大文字档（3:1），
// 功能键 22px / 按钮文字 19~21px 属普通文字档（4.5:1）。
// 色值常量与 app/src/main/res/values/colors.xml（PRD 6.1 唯一色值来源）及手机 H5 页 Token
//（assets/remote/index.html :root）逐字同步维护；任一侧色值改动而未同步复核，本类断言即失败。
// 半透明 Token（描边 / 主色淡底）按 src-over 合成到实际承载底色（主卡 card / 页面 bg）后计算。
// 禁用态控件不参与断言（WCAG 1.4.3 对禁用控件豁免，核查清单另行记录实测值）。
// 纯 JVM，不依赖 Android UI 与模拟器（TASKS T5 施工口径）。
package com.sofatype.ime

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class FocusContrastTest {

    // ---- PRD 6.1 / colors.xml Token 色值（同步维护口径见类注释） ----

    private val bg = rgb(0x06080C) // 页面背景
    private val card = rgb(0x12161E) // 卡片底
    private val key = rgb(0x1D242F) // 键帽底
    private val keyHover = rgb(0x242C39) // 键帽焦点/按压提亮
    private val accent = rgb(0x38BDF8) // 主色
    private val accentText = rgb(0x7DD3FC) // 主色上的文字 / 焦点描边文字
    private val onAccent = rgb(0x052330) // 主色底上的深青文字
    private val textPrimary = rgb(0xF2F6FB) // 主文字
    private val textBody = rgb(0xE8EEF5) // 键帽字符正文
    private val textSecondary = rgb(0xC9D4E2) // 功能键文字
    private val borderAccent = argb(0x80, 0x38BDF8) // 主色描边 rgba(56,189,248,.5)
    private val accentWash = argb(0x24, 0x38BDF8) // 主色淡底 rgba(56,189,248,.14)
    private val accentWashStrong = argb(0x29, 0x38BDF8) // 主色淡底上界 rgba(56,189,248,.16)

    // ---- WCAG 2.1 相对亮度与对比度 ----

    /** #RRGGBB → 线性化前 sRGB 三元组。 */
    private fun rgb(argb: Int): Triple<Double, Double, Double> {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return Triple(r.toDouble(), g.toDouble(), b.toDouble())
    }

    /** #AARRGGBB → (alpha, sRGB 三元组)。 */
    private fun argb(alpha: Int, rgb: Int): Pair<Double, Triple<Double, Double, Double>> =
        alpha / 255.0 to rgb(rgb)

    /** sRGB 通道 → 线性值（WCAG 2.1 定义）。 */
    private fun linear(channel: Double): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    private fun luminance(color: Triple<Double, Double, Double>): Double =
        0.2126 * linear(color.first) + 0.7152 * linear(color.second) + 0.0722 * linear(color.third)

    /** 对比度 = (L1 + 0.05) / (L2 + 0.05)，L1 为较亮者。 */
    private fun contrast(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Double {
        val la = luminance(a)
        val lb = luminance(b)
        val (hi, lo) = if (la >= lb) la to lb else lb to la
        return (hi + 0.05) / (lo + 0.05)
    }

    /** 半透明前景按 src-over 合成到不透明底。 */
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

    // ---- TV 端焦点态（PRD 6.4「聚焦态 = 主色描边 + 键帽提亮」） ----

    @Test
    fun `键帽焦点态文字对比度达标`() {
        // 字母/数字键帽字符 30px（bg_key state_focused 底 = key_hover）：大文字档 3:1
        assertContrast("键帽字符焦点 text_body on key_hover", textBody, keyHover, 3.0)
        // 功能键 22px / 图标键（bg_key 焦点底）：普通文字档 4.5:1
        assertContrast("功能键与图标键焦点 text_secondary on key_hover", textSecondary, keyHover, 4.5)
        // 上档键激活态图标/描边文字色（大写态 accent_text on key_hover）：组件 3:1
        assertContrast("上档激活焦点 accent_text on key_hover", accentText, keyHover, 3.0)
    }

    @Test
    fun `主色淡底元素焦点态文字对比度达标`() {
        val washOnCard = over(accentWash, card) // 候选 chip / 更多⌄ / 完成键常态底
        val washStrongOnCard = over(accentWashStrong, card) // 完成键 / 副卡按钮焦点底
        val washStrongOnBg = over(accentWashStrong, bg) // 设置页按钮焦点底（页根为 bg）
        // 候选 chip 28px / 更多⌄ 22px / 完成键（bg_chip_highlight / bg_key_done）焦点文字
        assertContrast("候选 chip 焦点 accent_text on wash@card", accentText, washOnCard, 4.5)
        assertContrast("完成键焦点 accent_text on wash_strong@card", accentText, washStrongOnCard, 4.5)
        // 引导卡主按钮 21px（bg_side_button 焦点底 = wash 上界 @ card）
        assertContrast("引导主按钮焦点 accent_text on wash_strong@card", accentText, washStrongOnCard, 4.5)
        // 设置页检查更新按钮 19px（bg_btn_accent 焦点底 = wash 上界 @ bg）
        assertContrast("设置页按钮焦点 accent_text on wash_strong@bg", accentText, washStrongOnBg, 4.5)
    }

    @Test
    fun `退出远程按钮焦点态文字对比度达标`() {
        // 「退出远程」按钮（ime_keyboard.xml）底 = bg_key（焦点态 key_hover）+ text_secondary 20px
        assertContrast("退出远程按钮焦点 text_secondary on key_hover", textSecondary, keyHover, 4.5)
    }

    @Test
    fun `焦点描边与承载底色对比度达标`() {
        // WCAG 1.4.11 非文字组件 ≥ 3:1：焦点描边（border_accent）是 FR-A11 焦点态的边界标识，
        // 辨识对象 = 相邻主卡底（描边跨键帽段合成底为 key_hover、跨卡底段为 card）
        assertContrast("焦点描边(border over key_hover) vs card", over(borderAccent, keyHover), card, 3.0)
        assertContrast("焦点描边(border over card) vs card", over(borderAccent, card), card, 3.0)
    }

    // ---- 手机 H5 页交互元素文字（触屏无焦点态，常态文字档核查） ----

    @Test
    fun `手机H5页按钮文字对比度达标`() {
        // 上屏主按钮（--accent 底 + --on-accent 文字 17px bold）
        assertContrast("H5 上屏按钮 on_accent on accent", onAccent, accent, 4.5)
        // 删除/清空次级按钮（--key 底 + --text-secondary 文字 15px）
        assertContrast("H5 次级按钮 text_secondary on key", textSecondary, key, 4.5)
        // 重连入口（--accent-wash @ bg 底 + --accent-text 文字 12px）
        assertContrast("H5 重连按钮 accent_text on wash@bg", accentText, over(accentWash, bg), 4.5)
        // 编辑卡正文（--card 底 + --text-primary 22px）
        assertContrast("H5 编辑卡正文 text_primary on card", textPrimary, card, 4.5)
    }
}
