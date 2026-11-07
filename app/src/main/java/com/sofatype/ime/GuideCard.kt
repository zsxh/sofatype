// T17 · 引导副卡视图装配（FR-B1 / 决策 32「未连接」卡面，画板①「字母键盘」右侧副卡）：
// 标题「跨屏输入」+ 图标；状态行（灰点 + 未连接）；三步指引（① 同一 Wi-Fi ② 点按「远程输入」
// 出示二维码 ③ 手机扫码，即可远程打字）；底部「远程输入」主按钮（高 60px、accent-wash 底 +
// border-accent 描边、文字 21px，PRD 6.3）；按钮下提示文案。
// 文案单一事实源在 RemoteUiText（单测锁定 FR-B1 原文）；规格全部走 Token（dimens/colors）。
// 主按钮可聚焦、OK 触发（FR-A10 焦点口径 = bg_side_button 主色描边 + 提亮，PRD 6.4）；
// 触发回调由服务层注入（启动服务 → 进入远程模式，FR-B2）。
package com.sofatype.ime

import android.content.Context
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

object GuideCard {

    /** 主按钮视图 tag（焦点方向路由识别：引导按钮 ↔ 键盘键互返，SofatypeImeService）。 */
    const val BUTTON_TAG = "guide_enter"

    /**
     * 渲染引导卡。容器原内容清空；重复调用幂等（先清后装）。
     * [onEnterRemote] = 主按钮点击回调（FR-B2 进入远程模式）。
     */
    fun show(context: Context, container: FrameLayout, onEnterRemote: () -> Unit) {
        container.removeAllViews()
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                context.resources.getDimensionPixelSize(R.dimen.ime_side_card_padding_h),
                context.resources.getDimensionPixelSize(R.dimen.ime_side_card_padding_v),
                context.resources.getDimensionPixelSize(R.dimen.ime_side_card_padding_h),
                context.resources.getDimensionPixelSize(R.dimen.ime_side_card_padding_v),
            )
        }

        // 标题行：图标 + 「跨屏输入」（画板① gap 10px；图标为展示件，无障碍语义由标题承载）
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_remote_guide)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        titleRow.addView(
            icon,
            LinearLayout.LayoutParams(
                context.resources.getDimensionPixelSize(R.dimen.ime_guide_icon),
                context.resources.getDimensionPixelSize(R.dimen.ime_guide_icon),
            ),
        )
        val title = TextView(context).apply {
            text = RemoteUiText.GUIDE_TITLE
            setTextColor(context.getColor(R.color.text_primary))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dimension(context, R.dimen.ime_guide_title_text))
            // 画板① 标题 24px/700
            typeface = Typeface.create(typeface, Typeface.BOLD)
        }
        titleRow.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.resources.getDimensionPixelSize(R.dimen.ime_guide_title_gap) },
        )
        content.addView(titleRow)

        // 状态行：灰点 + 未连接（FR-B1 未连接 = 灰点；引导卡只在未连接态呈现，决策 32）
        val statusRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        statusRow.addView(
            View(context).apply { setBackgroundResource(R.drawable.bg_status_dot_idle) },
            LinearLayout.LayoutParams(
                context.resources.getDimensionPixelSize(R.dimen.ime_status_dot_size),
                context.resources.getDimensionPixelSize(R.dimen.ime_status_dot_size),
            ),
        )
        statusRow.addView(
            TextView(context).apply {
                text = RemoteUiText.STATUS_NOT_CONNECTED
                setTextColor(context.getColor(R.color.text_dim))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, dimension(context, R.dimen.ime_guide_status_text))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = context.resources.getDimensionPixelSize(R.dimen.ime_guide_status_gap) },
        )
        content.addView(
            statusRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.resources.getDimensionPixelSize(R.dimen.ime_guide_status_margin_top) },
        )

        // 三步指引（FR-B1 原文，画板① 序号圆 26px + 指引文字 17px；行距 16px）
        val steps = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val circleSize = context.resources.getDimensionPixelSize(R.dimen.ime_guide_step_circle)
        RemoteUiText.GUIDE_STEPS.forEachIndexed { index, step ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                TextView(context).apply {
                    text = (index + 1).toString()
                    gravity = Gravity.CENTER
                    setTextColor(context.getColor(R.color.accent_text))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, dimension(context, R.dimen.ime_guide_step_num_text))
                    // 画板① 序号圆：26px 圆、accent-wash 底 + border-accent 描边（bg_step_circle）
                    setBackgroundResource(R.drawable.bg_step_circle)
                    // 圆内文字度量需要确定形状（T10 坑口径：LayoutParams 先行）
                    layoutParams = ViewGroup.LayoutParams(circleSize, circleSize)
                    // 序号圆为纯展示件：圆形轮廓裁剪（画板① 26px 圆）
                    clipToOutline = true
                    outlineProvider = object : ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: Outline) {
                            outline.setOval(0, 0, view.width, view.height)
                        }
                    }
                },
            )
            row.addView(
                TextView(context).apply {
                    text = step
                    setTextColor(context.getColor(R.color.text_step))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, dimension(context, R.dimen.ime_guide_step_text))
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { marginStart = context.resources.getDimensionPixelSize(R.dimen.ime_guide_step_gap_h) },
            )
            steps.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    circleSize,
                ).apply {
                    if (index > 0) topMargin = context.resources.getDimensionPixelSize(R.dimen.ime_guide_step_gap)
                },
            )
        }
        content.addView(
            steps,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = context.resources.getDimensionPixelSize(R.dimen.ime_guide_steps_margin_top) },
        )

        // 弹性占位：把主按钮与提示压到卡底（画板① 按钮距卡底 92px / 提示距卡底 34px，含卡 padding 换算见 Token 注释）
        content.addView(
            View(context),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        // 主按钮（FR-B1「远程输入」/ PRD 6.3 高 60px accent-wash + border-accent 字 21px；画板①「远 程 输 入」）
        val button = TextView(context).apply {
            text = RemoteUiText.GUIDE_BUTTON_LABEL
            contentDescription = RemoteUiText.GUIDE_BUTTON_DESCRIPTION
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(context.getColor(R.color.accent_text))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dimension(context, R.dimen.ime_guide_button_text))
            setBackgroundResource(R.drawable.bg_side_button)
            tag = BUTTON_TAG
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { onEnterRemote() }
        }
        content.addView(
            button,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.resources.getDimensionPixelSize(R.dimen.ime_guide_button_height),
            ).apply {
                bottomMargin = context.resources.getDimensionPixelSize(R.dimen.ime_guide_button_gap)
            },
        )

        // 按钮下提示（FR-B1 原文）
        content.addView(
            TextView(context).apply {
                text = RemoteUiText.GUIDE_BUTTON_HINT
                setTextColor(context.getColor(R.color.text_hint))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, dimension(context, R.dimen.ime_guide_hint_text))
            },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )

        container.addView(
            content,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /** sp 维度 → px（dimens 以 sp 声明，getDimension 已换算 px，按 px 设置避免二次缩放，既有口径）。 */
    private fun dimension(context: Context, res: Int): Float = context.resources.getDimension(res)
}
