// T71 · 决策 60 画板 14 · 调试日志开关控件：纯绘制 View（64×32px 轨道 + 24px 旋钮）。
// 开 = accent 底轨道 + on_accent 旋钮（画板 14 开启态）；关 = key 底轨道 + 描边 + 灰旋钮
//（画板未给出关闭态，按非焦点不高亮原则取键帽同款底色，决策 44）。仅作状态显示——
// 切换动作在所在行（OK 键），本视图不参与焦点链（无障碍描述由行容器承载）。
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

    /** 开关状态（LogSettingsActivity 刷新；默认关 = 决策 60）。 */
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
        // 旋钮直径 = 轨道高 - 上下内边距各 2px；ON 在右、OFF 在左（画板 14 口径）
        val pad = h * 0.125f
        val thumbRadius = radius - pad
        val cx = if (checked) w - radius else radius
        canvas.drawCircle(cx, radius, thumbRadius, if (checked) thumbOn else thumbOff)
    }
}
