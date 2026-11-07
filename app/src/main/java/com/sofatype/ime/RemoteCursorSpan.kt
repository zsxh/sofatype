// T33 · 远程大字区末尾青色光标（FR-B3「末尾青色光标」，决策 31 折行显示口径）。
// RemoteCursorSpan：Spannable 尾部锚点字符的自绘 ReplacementSpan——光标随文本流定位
//（折行后恒在最后一行行尾），纵向滚动时随内容层整体移动，无需手动坐标。
// RemoteCursorGeometry：行盒内光标纵向居中的纯算术函数（无 android 依赖，可被 JVM 单测覆盖）。
// 尺寸/色值走 Token（dimens / colors），任何日志不涉文本内容（隐私红线）。
package com.sofatype.ime

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

/** 行盒内光标纵向居中的纯算术（可被 JVM 单测覆盖）。 */
object RemoteCursorGeometry {

    /**
     * 光标顶缘 y：在 [top, bottom] 行盒内垂直居中放置 [cursorHeight] 高的光标。
     * 光标高于行盒时结果为负（顶部越界居中），调用方按算术值绘制（当前口径光标恒低于行盒）。
     */
    fun centeredTop(top: Int, bottom: Int, cursorHeight: Int): Int =
        top + (bottom - top - cursorHeight) / 2
}

/**
 * 末尾青色光标 Span：锚定一个占位字符，宽度由本 Span 提供（[widthPx]），
 * 绘制 [heightPx] 高的圆角竖条（accent 色）。折行后 Span 锚点跟随最后一行行尾。
 */
class RemoteCursorSpan(
    private val widthPx: Int,
    private val heightPx: Int,
    private val color: Int,
    private val radiusPx: Float,
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?,
    ): Int {
        // 光标占位字符不参与字体度量：宽度恒为光标条宽，行高度量不改动
        return widthPx
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        baseline: Int,
        bottom: Int,
        paint: Paint,
    ) {
        val cursorTop = RemoteCursorGeometry.centeredTop(top, bottom, heightPx)
        paint.color = color
        canvas.drawRoundRect(x, cursorTop.toFloat(), x + widthPx, (cursorTop + heightPx).toFloat(), radiusPx, radiusPx, paint)
    }
}
