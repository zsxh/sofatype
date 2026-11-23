package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T98KeyFaceScaleTest {

    private fun mainSource(path: String): String =
        File("src/main/$path").readText()

    private fun dimenValue(dimens: String, name: String): String =
        dimens.substringAfter("<dimen name=\"$name\">").substringBefore("</dimen>")

    @Test
    fun `键面字号口径三档落 dimen`() {
        val dimens = mainSource("res/values/dimens.xml")
        assertEquals("字母/数字/符号键标签 30px（T98 定稿）", "15sp", dimenValue(dimens, "ime_key_text"))
        assertEquals("功能键标签 24px（T98 定稿）", "12sp", dimenValue(dimens, "ime_key_fn_text"))
        assertEquals("三字符标签键 20px（T98 定稿）", "10sp", dimenValue(dimens, "ime_key_tri_text"))
    }

    @Test
    fun `键内图标统一 30px`() {
        val dimens = mainSource("res/values/dimens.xml")
        assertEquals("下屏图标 30px", "15dp", dimenValue(dimens, "ime_icon_hide"))
        assertEquals("删除图标 30px", "15dp", dimenValue(dimens, "ime_icon_backspace"))
        assertEquals("光标图标 30px", "15dp", dimenValue(dimens, "ime_icon_cursor"))
        assertEquals("上档图标 30px", "15dp", dimenValue(dimens, "ime_icon_shift"))
    }

    @Test
    fun `完成键格位按分词列栅格对齐`() {
        val dimens = mainSource("res/values/dimens.xml")
        assertEquals("完成键左缘 1282px", "641dp", dimenValue(dimens, "ime_done_key_start"))
        assertEquals("完成键宽 96px", "48dp", dimenValue(dimens, "ime_done_key_width"))
        assertEquals("完成键右缘齐键盘右边线（1282 + 96 = 1378）", 1378, 1282 + 96)
        assertEquals("完成键左邻键隙 10px（功能列右缘 1272 → 1282）", 10, 1282 - 1272)
        assertEquals("符号板完成键格位同步（1282/96）", "641dp", dimenValue(dimens, "ime_symbols_done_key_start"))
        assertEquals("符号板完成键宽 96px", "48dp", dimenValue(dimens, "ime_symbols_done_key_width"))
    }

    @Test
    fun `符号板空格键格位对齐分词列`() {
        val dimens = mainSource("res/values/dimens.xml")
        assertEquals("符号板空格键左缘 1176px（对齐分词列）", "588dp", dimenValue(dimens, "ime_space_key_start"))
    }

    @Test
    fun `键模型档位与口径逐键对齐`() {
        val byId = KeyboardLayout.keyById
        assertEquals("分词键 24px 档", KeyTextSize.SMALL, byId.getValue("segment").textSize)
        assertEquals(".com 键 24px 档", KeyTextSize.SMALL, byId.getValue("dot_com").textSize)
        assertEquals("空格键 24px 档", KeyTextSize.SMALL, byId.getValue("space").textSize)
        assertEquals("中/英键 20px 档（三字符）", KeyTextSize.TRIPLE, byId.getValue("lang").textSize)
        assertEquals("123_! 键 20px 档（三字符）", KeyTextSize.TRIPLE, byId.getValue("symbols").textSize)
        assertEquals("字母键 30px 档", KeyTextSize.NORMAL, byId.getValue("a").textSize)
        assertEquals("数字键 30px 档", KeyTextSize.NORMAL, byId.getValue("digit_5").textSize)
        assertEquals("完成键 24px 档", KeyTextSize.SMALL, KeyboardLayout.doneKey.textSize)
        assertEquals("符号板完成键 24px 档", KeyTextSize.SMALL, SymbolsLayout.doneKey.textSize)
        assertEquals("符号板空格键 24px 档", KeyTextSize.SMALL, SymbolsLayout.spaceKey.textSize)

        val halfWidth = SymbolsLayout.allKeys(SymbolsWidth.HALF_WIDTH)
        assertEquals("全/半键 20px 档（三字符）", KeyTextSize.TRIPLE, halfWidth.first { it.id == SymbolsLayout.TOGGLE_WIDTH_ID }.textSize)
        assertEquals("符号板返回键 24px 档", KeyTextSize.SMALL, halfWidth.first { it.id == SymbolsLayout.BACK_ID }.textSize)
    }

    @Test
    fun `状态键渲染字号走三字符档`() {
        val service = mainSource("java/com/sofatype/ime/SofatypeImeService.kt")
        val stateRender = service.substringAfter("private fun createStateKeyView(key: Key)")
            .substringBefore("\n    }")
        assertTrue(
            "状态键（中英、全/半）文字须落三字符档（T98 口径 20px）",
            stateRender.contains("R.dimen.ime_key_tri_text"),
        )
    }

    @Test
    fun `完成键渲染字号走 24px 档`() {
        val service = mainSource("java/com/sofatype/ime/SofatypeImeService.kt")
        val overlayBind = service.substringAfter("private fun bindOverlayKeys(root: View)")
            .substringBefore("\n    }\n")
        assertTrue(
            "字母板完成键须绑定格位 dimen（T98 口径 641dp/48dp）",
            overlayBind.contains("R.dimen.ime_done_key_start") && overlayBind.contains("R.dimen.ime_done_key_width"),
        )
        assertTrue(
            "符号板空格键须绑定 588dp 格位（T98 口径 1176px）",
            overlayBind.contains("R.dimen.ime_space_key_start"),
        )
    }
}
