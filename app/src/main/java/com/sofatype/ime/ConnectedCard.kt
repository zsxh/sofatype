// T17 · 「已连接」副卡卡面视图装配（FR-B9 / 决策 32 / T14 评审遗留建议落实）：
// 配对成功后整卡替换配对卡——绿点 + 「已连接 · 设备名」，不显示已失效二维码
//（避免用户扫已消费 token 的死码）。规格复用状态行体系 Token（状态点直径 / 状态文字 20px 500）；
// 文案单一事实源在 RemoteUiText；设备名为 hello.deviceName（决策 34：会话内存驻留，停止即清）。
// 纯展示卡面，无可聚焦元素。
package com.sofatype.ime

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

object ConnectedCard {

    /**
     * 渲染已连接卡面（FR-B9 已连接 = 绿点 + 设备名）。容器原内容清空；重复调用幂等。
     * [deviceName] 为 null / 空（hello 未携带）时只显示「已连接」（RemoteUiText 格式化）。
     */
    fun show(context: Context, container: FrameLayout, deviceName: String?) {
        container.removeAllViews()
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(
            View(context).apply { setBackgroundResource(R.drawable.bg_status_dot_connected) },
            LinearLayout.LayoutParams(
                context.resources.getDimensionPixelSize(R.dimen.ime_status_dot_size),
                context.resources.getDimensionPixelSize(R.dimen.ime_status_dot_size),
            ),
        )
        row.addView(
            TextView(context).apply {
                text = RemoteUiText.connectedFaceText(deviceName)
                setTextColor(context.getColor(R.color.text_primary))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.ime_remote_status_text))
                // 与远程主卡状态行同一排印层级（20px/500 语义 → bold）
                typeface = Typeface.create(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.resources.getDimensionPixelSize(R.dimen.ime_remote_status_gap) },
        )
        container.addView(
            row,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
    }
}
