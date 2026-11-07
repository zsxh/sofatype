// T14 · 配对卡视图装配（FR-B4，画板⑤「远程模式」副卡）：
// 副卡整卡 = 配对二维码（376×376，圆角 38）+ 下方访问地址一行，地址与二维码内容一致
//（同源 pairingUrl，含一次性 token），二维码下方不显示其他文案（决策 4）。
// 规格全部走 Token（dimens.xml / colors.xml）；二维码黑模块白底为扫码对比度所需
//（画板⑤ 二维码资产同形），属功能性用色，不入主题 Token 方案（不新增配色方案）。
package com.sofatype.ime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Outline
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import com.sofatype.ime.remote.QrCodeEncoder

object PairingCard {

    /** 二维码无障碍标注（画板⑤ img alt 原文）。 */
    private const val QR_CONTENT_DESCRIPTION = "跨屏输入配对二维码"

    /** 地址行字号自适应下限（sp）：token 拼入后地址变长，基准字号放不下时逐步缩小，守「一行 + 地址一致」。 */
    private const val URL_MIN_TEXT_SP = 6

    /** 二维码模块色 / 静区底色：黑模块白底（扫码对比度所需，画板⑤ 资产同形）。 */
    private const val QR_MODULE_COLOR = Color.BLACK
    private const val QR_BACKGROUND_COLOR = Color.WHITE

    /**
     * 渲染配对卡：二维码 + 地址一行。容器原占位内容清空；重复调用幂等（先清后装）。
     * 卡片为纯展示（无遥控交互），视图不设可聚焦。
     */
    fun show(context: Context, container: FrameLayout, pairingUrl: String) {
        container.removeAllViews()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padding = context.resources.getDimensionPixelSize(R.dimen.ime_pairing_padding)
            setPadding(padding, padding, padding, padding)
        }
        val qrSizePx = context.resources.getDimensionPixelSize(R.dimen.ime_qr_size)
        val qrView = ImageView(context).apply {
            setImageBitmap(renderQrBitmap(pairingUrl, qrSizePx))
            contentDescription = QR_CONTENT_DESCRIPTION
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            // FR-B4 圆角 38px（Token = ime_card_corner）：轮廓裁剪实现
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
                bottomMargin = context.resources.getDimensionPixelSize(R.dimen.ime_pairing_gap)
            },
        )
        val urlView = TextView(context).apply {
            text = pairingUrl
            maxLines = 1
            gravity = Gravity.CENTER
            setTextColor(context.getColor(R.color.accent_text))
            // 画板⑤ 地址行 letter-spacing .5px @22px ≈ 0.023em（em 相对字号，缩字号后按比例生效）
            letterSpacing = 0.023f
            // 未挂载视图必须有 LayoutParams 再 measure（T10 坑口径）
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        fitUrlTextSize(context, urlView)
        content.addView(urlView)
        container.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /** 清空配对卡回占位态（服务停止 / 键盘重建无服务时）。 */
    fun clear(container: FrameLayout) {
        container.removeAllViews()
    }

    /** 二维码圆角半径 px（FR-B4 圆角 38 = 悬浮卡片圆角 Token）。 */
    private fun cornerRadiusPx(context: Context): Float =
        context.resources.getDimension(R.dimen.ime_card_corner)

    /** BitMatrix → 位图：白底黑模块（376×376 ARGB_8888，一次性渲染成本约 14 万像素写入）。 */
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

    /**
     * 地址一行自适应（FR-B4「地址与二维码内容一致」+ 决策 4「网址一行」）：
     * 基准字号（Token 11sp）测量放不下副卡内容宽时按 1px 步进缩小（下限 [URL_MIN_TEXT_SP] sp），
     * 不换行、不截断。正常 IP 长度下保持基准字号，不触发缩小。
     */
    private fun fitUrlTextSize(context: Context, view: TextView) {
        val basePx = context.resources.getDimensionPixelSize(R.dimen.ime_pairing_url_text)
        val minPx = spToPx(context, URL_MIN_TEXT_SP.toFloat())
        val available = context.resources.getDimensionPixelSize(R.dimen.ime_side_card_width) -
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
