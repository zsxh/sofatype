// T14/T35 · 配对卡视图装配（FR-B4 / 决策 4 / 决策 35）：
// 等待扫码态：副卡整卡 = 配对二维码（376×376，圆角 38）+ 下方访问地址一行，地址与二维码内容一致
//（同源 pairingUrl，含一次性 token），二维码下方不显示其他文案（决策 4）。
// 已连接态（决策 35）：二维码卡保留，二维码为配对成功后滚动签发的新 token 有效码，
// 下方网址行**原位换成**绿点状态行「已连接 · 设备名」（决策 32 独立已连接卡面废止）；
// 决策 4 只约束等待态配对卡。几何依据（决策 35）：548 = 40×2 + 376 + 10 + 22，余量 60px，
// 单行状态行直接放得下。
// 规格全部走 Token（dimens.xml / colors.xml）；二维码黑模块白底为扫码对比度所需
//（画板⑤ 二维码资产同形），属功能性用色，不入主题 Token 方案（不新增配色方案）。
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
     * 渲染副卡二维码卡（T35 · 决策 35）：二维码 + 下方一行——
     * [statusLineText] 为 null = 等待扫码态：访问地址一行（FR-B4 / 决策 4）；
     * 非 null = 已连接态：绿点 + 状态行文字（RemoteUiText.connectedFaceText，「已连接 · 设备名」），
     * 网址行不再显示（token 已消费，滚动新码见二维码本体）。
     * 容器原占位内容清空；重复调用幂等（先清后装）。
     * 卡片为纯展示（无遥控交互），视图不设可聚焦。
     */
    fun show(context: Context, container: FrameLayout, pairingUrl: String, statusLineText: String? = null) {
        container.removeAllViews()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val padding = context.resources.getDimensionPixelSize(R.dimen.ime_pairing_padding)
            setPadding(padding, padding, padding, padding)
        }
        val qrSizePx = context.resources.getDimensionPixelSize(R.dimen.ime_qr_size)
        // 与下方一行的间距：等待态地址行 5dp（画板⑤ gap）；已连接状态行更松 16dp
        //（户主 2026-11-07 反馈状态行太贴近二维码，T35 回访调整）
        val rowGapPx = context.resources.getDimensionPixelSize(
            if (statusLineText != null) R.dimen.ime_pairing_status_gap else R.dimen.ime_pairing_gap,
        )
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
                bottomMargin = rowGapPx
            },
        )
        content.addView(
            if (statusLineText != null) buildStatusLine(context, statusLineText) else buildUrlLine(context, pairingUrl),
            // 状态行显式 WRAP_CONTENT：竖向 LinearLayout 子视图默认宽 MATCH_PARENT，
            // 不给显式宽度会整行靠左（列重力居中不生效），户主反馈居中问题的根因
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

    /** 清空副卡二维码卡回占位态（服务停止 / 键盘重建无服务时）。 */
    fun clear(container: FrameLayout) {
        container.removeAllViews()
    }

    /** 等待态下方访问地址一行（FR-B4「地址与二维码内容一致」+ 决策 4「网址一行」）。 */
    private fun buildUrlLine(context: Context, pairingUrl: String): TextView {
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
        return urlView
    }

    /** 已连接态下方状态行（决策 35 · FR-B9）：绿点 + 「已连接 · 设备名」，排印同远程主卡状态行。 */
    private fun buildStatusLine(context: Context, statusText: String): LinearLayout {
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
                text = statusText
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
        return row
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
