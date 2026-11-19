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

    fun show(context: Context, container: LinearLayout, code: ClientCode) {
        container.removeAllViews()
        container.orientation = LinearLayout.HORIZONTAL
        container.gravity = Gravity.CENTER_VERTICAL
        container.setBackgroundResource(R.drawable.bg_client_code_pill)
        val padH = context.resources.getDimensionPixelSize(R.dimen.client_code_padding_h)
        val padV = context.resources.getDimensionPixelSize(R.dimen.client_code_padding_v)
        container.setPadding(padH, padV, padH, padV)

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

            typeface = Typeface.create(typeface, Typeface.BOLD)
            fontFeatureSettings = "tnum"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.resources.getDimensionPixelSize(R.dimen.client_code_gap) }
        }
        container.addView(number)
    }

    fun drawableOf(type: DeviceType): Int = when (type) {
        DeviceType.ANDROID -> R.drawable.ic_device_android
        DeviceType.APPLE -> R.drawable.ic_device_apple
        DeviceType.WECHAT -> R.drawable.ic_device_wechat
        DeviceType.BROWSER -> R.drawable.ic_device_browser
    }
}
