// T72 · 连接代号胶囊视图装配（决策 61，画板 RemoteConnected / PhoneRemote 20261114）：
// 胶囊 = [官方标 icon][类型]设备[两位序号]，中性浅灰底圆角（bg_client_code_pill），
// icon 外文字纯白 text_primary（#F2F6FB）；icon 尺寸 TV 22px（Token client_code_icon_size）。
// 与 PairingCard / SofatypeImeService 共用：主卡「来自 ·」行与副卡「已连接」状态行同款。
// 视图无遥控交互（纯展示），不设可聚焦；无障碍以胶囊整体 contentDescription 播报全文
//（如「微信设备 01」）。每次 show 先清后装（幂等，可重复渲染）。
package com.sofatype.ime

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.sofatype.ime.remote.ClientCode
import com.sofatype.ime.remote.DeviceType

object ClientCodePill {

    /** 装配连接代号胶囊进容器（容器为布局占位的水平 LinearLayout；先清后装）。 */
    fun show(context: Context, container: LinearLayout, code: ClientCode) {
        container.removeAllViews()
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER_VERTICAL
        container.setBackgroundResource(R.drawable.bg_client_code_pill)
        val padH = context.resources.getDimensionPixelSize(R.dimen.client_code_padding_h)
        val padV = context.resources.getDimensionPixelSize(R.dimen.client_code_padding_v)
        container.setPadding(padH, padV, padH, padV)
        // 胶囊整体无障碍播报全文（两段 span 不单独标注，避免拆碎语义）
        container.contentDescription = code.text
        container.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES

        val icon = ImageView(context)
        icon.setImageResource(drawableOf(code.type))
        val iconSize = context.resources.getDimensionPixelSize(R.dimen.client_code_icon_size)
        container.addView(
            icon,
            LinearLayout.LayoutParams(iconSize, iconSize),
        )

        val label = TextView(context).apply {
            text = code.type.label + ClientCode.DEVICE_SUFFIX
            setTextColor(context.getColor(R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.client_code_label_text))
            // 画板「微信设备」w600
            typeface = Typeface.create(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.resources.getDimensionPixelSize(R.dimen.client_code_gap) }
        }
        container.addView(label)

        val number = TextView(context).apply {
            text = code.numberText
            setTextColor(context.getColor(R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.client_code_number_text))
            // 画板序号 w700 + 等宽数字（font-variant-numeric:tabular-nums 同义）
            typeface = Typeface.create(typeface, Typeface.BOLD)
            fontFeatureSettings = "tnum"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.resources.getDimensionPixelSize(R.dimen.client_code_gap) }
        }
        container.addView(number)
    }

    /** 类型 → 官方标矢量图标（决策 61 四类型全套：安卓 / 苹果 / 微信 / 浏览器地球）。 */
    fun drawableOf(type: DeviceType): Int = when (type) {
        DeviceType.ANDROID -> R.drawable.ic_device_android
        DeviceType.APPLE -> R.drawable.ic_device_apple
        DeviceType.WECHAT -> R.drawable.ic_device_wechat
        DeviceType.BROWSER -> R.drawable.ic_device_browser
    }
}
