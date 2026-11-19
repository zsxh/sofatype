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

    const val BUTTON_TAG = "guide_enter"

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

                    setBackgroundResource(R.drawable.bg_step_circle)

                    layoutParams = ViewGroup.LayoutParams(circleSize, circleSize)

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

        content.addView(
            View(context),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val button = TextView(context).apply {
            text = RemoteUiText.GUIDE_BUTTON_LABEL
            contentDescription = RemoteUiText.GUIDE_BUTTON_DESCRIPTION
            gravity = Gravity.CENTER
            maxLines = 1
            setTextColor(context.getColor(R.color.text_body))
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

    private fun dimension(context: Context, res: Int): Float = context.resources.getDimension(res)
}
