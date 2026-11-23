package com.sofatype.ime.visual

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.sofatype.ime.Cell
import com.sofatype.ime.Key
import com.sofatype.ime.KeyboardLayout
import com.sofatype.ime.LanguageMode
import com.sofatype.ime.R
import com.android.resources.Density
import com.android.resources.NightMode

class KeyboardVisualRegressionTest {

    private val tvDeviceConfig = DeviceConfig.PIXEL.copy(
        screenWidth = SCREEN_W,
        screenHeight = SCREEN_H,
        xdpi = 320,
        ydpi = 320,
        density = Density.XHIGH,
        orientation = ScreenOrientation.LANDSCAPE,
        size = ScreenSize.XLARGE,
        ratio = ScreenRatio.NOTLONG,
        nightMode = NightMode.NOTNIGHT,
    )

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = tvDeviceConfig,
        maxPercentDifference = 0.0,
        showSystemUi = false,
    )

    private fun keyLabel(key: Key, chinese: Boolean, upper: Boolean): String =
        KeyboardLayout.displayLabel(key, chinese, upper) ?: key.label.orEmpty()

    private fun buildKeyboard(chinese: Boolean, upper: Boolean): ViewGroup {
        val context = paparazzi.context
        val res = context.resources
        val keyWidth = res.getDimensionPixelSize(R.dimen.ime_key_width)
        val keyHeight = res.getDimensionPixelSize(R.dimen.ime_key_height)
        val spacing = res.getDimensionPixelSize(R.dimen.ime_key_spacing)
        val spacerWidth = res.getDimensionPixelSize(R.dimen.ime_column_spacer)
        val textPx = res.getDimension(R.dimen.ime_key_text)
        val fnTextPx = res.getDimension(R.dimen.ime_key_fn_text)

        val mode = if (chinese) LanguageMode.CHINESE else LanguageMode.ENGLISH
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(res.getColor(R.color.card, null))
        }
        for (row in KeyboardLayout.rows(mode)) {
            val rowView = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            for (cell in row.cells) {
                when (cell) {
                    is Cell.KeyCell -> {
                        val key = cell.key
                        val view = TextView(context).apply {
                            text = keyLabel(key, chinese, upper)
                            gravity = Gravity.CENTER
                            maxLines = 1
                            includeFontPadding = false
                            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, res.getDimension(key.textSizeRes()))
                            setTextColor(res.getColor(key.textColorRes(), null))
                            background = context.getDrawable(R.drawable.bg_key)
                        }
                        view.layoutParams = LinearLayout.LayoutParams(keyWidth, keyHeight).apply {
                            marginEnd = spacing
                        }
                        rowView.addView(view)
                    }
                    Cell.Spacer -> rowView.addView(
                        View(context),
                        LinearLayout.LayoutParams(spacerWidth, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            marginEnd = spacing
                        },
                    )
                    Cell.EmptyCell -> rowView.addView(
                        View(context),
                        LinearLayout.LayoutParams(keyWidth, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            marginEnd = spacing
                        },
                    )
                }
            }
            root.addView(
                rowView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    keyHeight,
                ).apply { topMargin = spacing },
            )
        }
        root.measure(
            View.MeasureSpec.makeMeasureSpec(SCREEN_W, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(SCREEN_H, View.MeasureSpec.AT_MOST),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return root
    }

    private fun Key.textColorRes(): Int = when (variant) {
        com.sofatype.ime.KeyVariant.FUNCTION -> R.color.text_secondary
        else -> R.color.text_body
    }

    private fun Key.textSizeRes(): Int = when (textSize) {
        com.sofatype.ime.KeyTextSize.NORMAL -> R.dimen.ime_key_text
        com.sofatype.ime.KeyTextSize.SMALL -> R.dimen.ime_key_fn_text
        com.sofatype.ime.KeyTextSize.DENSE -> R.dimen.ime_key_dense_text
        com.sofatype.ime.KeyTextSize.TRIPLE -> R.dimen.ime_key_tri_text
    }

    private fun rowChildLefts(root: ViewGroup, rowIndex: Int): List<Int> {
        val row = root.getChildAt(rowIndex) as ViewGroup
        return (0 until row.childCount).map { row.getChildAt(it).left }
    }

    @Test
    fun columnRhythmMatchesArtboardPx() {
        for (chinese in listOf(true, false)) {
            val root = buildKeyboard(chinese, upper = false)
            val row1 = rowChildLefts(root, 0)
            assertEquals("字母列原点（屏 424）", LETTER_COL_PX, row1[4])
            assertEquals("功能列原点（屏 1224）", FN_COL_PX, row1[12])
            assertEquals("图标列原点（屏 1330）", ICON_COL_PX, row1[13])
            assertEquals("列间隔步进（spacer 48dp + 间距 5dp @2x）", COL_STEP_PX, row1[4] - row1[3])
            val row3 = rowChildLefts(root, 2)
            assertEquals("行 3 功能列（123_!）", FN_COL_PX, row3[12])
            val row4 = rowChildLefts(root, 3)
            assertEquals("行 4 功能列（.com）", FN_COL_PX, row4[12])
        }
    }

    @Test
    fun colorTokensAppliedToKeyViews() {
        val context = paparazzi.context
        val res = context.resources
        val root = buildKeyboard(chinese = true, upper = false)
        val row = root.getChildAt(1) as ViewGroup
        val letterView = row.getChildAt(4) as TextView
        assertEquals(res.getColor(R.color.text_body, null), letterView.currentTextColor)
        assertTrue(letterView.background !is android.graphics.drawable.ColorDrawable)

        val segmentRow = root.getChildAt(0) as ViewGroup
        val segmentView = segmentRow.getChildAt(12) as TextView
        assertEquals(res.getColor(R.color.text_secondary, null), segmentView.currentTextColor)

        val accentText = res.getColor(R.color.accent_text, null)
        assertEquals(Color.parseColor("#7DD3FC"), accentText)
    }

    @Test
    fun snapshotChineseLowercaseFaces() {
        paparazzi.snapshot(buildKeyboard(chinese = true, upper = false))
    }

    @Test
    fun snapshotEnglishUppercaseFaces() {
        paparazzi.snapshot(buildKeyboard(chinese = false, upper = true))
    }

    private companion object {
        const val SCREEN_W = 1378
        const val SCREEN_H = 820
        const val LETTER_COL_PX = 376
        const val FN_COL_PX = 1176
        const val ICON_COL_PX = 1282
        const val COL_STEP_PX = 58
    }
}
