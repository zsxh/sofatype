package com.sofatype.ime

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan

object RemoteCursorGeometry {

    fun centeredTop(top: Int, bottom: Int, cursorHeight: Int): Int =
        top + (bottom - top - cursorHeight) / 2
}

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
