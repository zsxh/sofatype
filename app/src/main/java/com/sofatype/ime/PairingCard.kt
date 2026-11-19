package com.sofatype.ime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.sofatype.ime.remote.ClientCode
import com.sofatype.ime.remote.QrCodeEncoder

object PairingCard {

    private const val QR_CONTENT_DESCRIPTION = "跨屏输入配对二维码"

    private const val URL_MIN_TEXT_SP = 5

    private const val QR_MODULE_COLOR = Color.BLACK
    private const val QR_BACKGROUND_COLOR = Color.WHITE

    fun show(context: Context, container: FrameLayout, pairingUrl: String, clientCode: ClientCode? = null) {
        container.removeAllViews()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padding = context.resources.getDimensionPixelSize(R.dimen.ime_pairing_padding)
            setPadding(padding, padding, padding, padding)
        }
        val qrSizePx = context.resources.getDimensionPixelSize(R.dimen.ime_qr_size)

        val rowGapPx = context.resources.getDimensionPixelSize(
            if (clientCode != null) R.dimen.ime_pairing_status_gap else R.dimen.ime_pairing_gap,
        )
        val qrView = ImageView(context).apply {
            setImageBitmap(renderQrBitmap(pairingUrl, qrSizePx))
            contentDescription = QR_CONTENT_DESCRIPTION
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES

            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx(context))
                }
            }
        }
        content.addView(
            qrView,
            LinearLayout.LayoutParams(qrSizePx, qrSizePx).apply {
                bottomMargin = rowGapPx
            },
        )
        content.addView(
            if (clientCode != null) buildStatusLine(context, clientCode) else buildUrlLine(context, pairingUrl),

            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        container.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun clear(container: FrameLayout) {
        container.removeAllViews()
    }

    private fun buildUrlLine(context: Context, pairingUrl: String): TextView {
        val urlView = TextView(context).apply {
            text = pairingUrl
            maxLines = 1
            gravity = Gravity.CENTER
            setTextColor(context.getColor(R.color.accent_text))

            letterSpacing = 0.017f

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        fitUrlTextSize(context, urlView)
        return urlView
    }

    private fun buildStatusLine(context: Context, clientCode: ClientCode): LinearLayout {
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
                text = RemoteUiText.CONNECTED_FACE_TEXT
                setTextColor(context.getColor(R.color.text_primary))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.ime_remote_status_text))

                typeface = Typeface.create(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.resources.getDimensionPixelSize(R.dimen.ime_pairing_status_text_gap) },
        )

        val pill = LinearLayout(context)
        ClientCodePill.show(context, pill, clientCode)
        row.addView(
            pill,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = context.resources.getDimensionPixelSize(R.dimen.ime_pairing_status_text_gap)
            },
        )
        return row
    }

    private fun cornerRadiusPx(context: Context): Float =
        context.resources.getDimension(R.dimen.ime_qr_corner)

    private fun renderQrBitmap(content: String, sizePx: Int): Bitmap {
        val matrix = QrCodeEncoder.encode(content, sizePx)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(QR_BACKGROUND_COLOR)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) bitmap.setPixel(x, y, QR_MODULE_COLOR)
            }
        }
        return bitmap
    }

    private fun fitUrlTextSize(context: Context, view: TextView) {
        val basePx = context.resources.getDimensionPixelSize(R.dimen.ime_pairing_url_text)
        val minPx = spToPx(context, URL_MIN_TEXT_SP.toFloat())
        val available = context.resources.getDimensionPixelSize(R.dimen.ime_qr_zone_width) -
            2 * context.resources.getDimensionPixelSize(R.dimen.ime_pairing_padding)
        var sizePx = basePx.toFloat()
        while (sizePx > minPx) {
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx)
            view.measure(0, 0)
            if (view.measuredWidth <= available) return
            sizePx -= 1f
        }
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, minPx)
    }

    private fun spToPx(context: Context, sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics)
}
