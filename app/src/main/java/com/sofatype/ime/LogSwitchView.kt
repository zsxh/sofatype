package com.sofatype.ime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class LogSwitchView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val trackOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.accent) }
    private val trackOff = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.key) }
    private val trackStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = context.getColor(R.color.divider)
    }
    private val thumbOn = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(R.color.on_accent) }
    private val thumbOff = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(R.color.dot_idle)
    }

    var checked: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = h / 2f
        if (checked) {
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, trackOn)
        } else {
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, trackOff)
            canvas.drawRoundRect(0f, 0f, w, h, radius, radius, trackStroke)
        }

        val pad = h * 0.125f
        val thumbRadius = radius - pad
        val cx = if (checked) w - radius else radius
        canvas.drawCircle(cx, radius, thumbRadius, if (checked) thumbOn else thumbOff)
    }
}
