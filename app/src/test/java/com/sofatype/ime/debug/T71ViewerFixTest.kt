package com.sofatype.ime.debug

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T71ViewerFixTest {

    private fun source(path: String): String = File(path).readText()

    @Test
    fun `B1 浏览卡顶距修正为画板口径 140dp`() {
        val layout = source("src/main/res/layout/activity_log_viewer.xml")
        val scrollBlockStart = layout.indexOf("android:id=\"@+id/log_scroll\"")
        assertTrue("log_scroll 定义缺失", scrollBlockStart >= 0)
        assertTrue(
            "浏览卡须引用 log_view_card_margin_top（qa B1）",
            layout.substring(scrollBlockStart).contains("@dimen/log_view_card_margin_top"),
        )
        val dimens = source("src/main/res/values/dimens.xml")
        assertTrue(
            "log_view_card_margin_top 须为 140dp（画板 15 浏览卡顶 280px ÷ 2；qa B1 修正）",
            dimens.contains("<dimen name=\"log_view_card_margin_top\">140dp</dimen>"),
        )
    }

    @Test
    fun `B2 左右键按钮间移焦接线存在`() {
        val activity = source("src/main/java/com/sofatype/ime/LogViewerActivity.kt")
        assertTrue(
            "查看页须有 FOCUS_LEFT/FOCUS_RIGHT 移焦分支（qa B2：[清空日志] 遥控可达）",
            activity.contains("SettingsKeyRouter.Route.FOCUS_LEFT") &&
                activity.contains("SettingsKeyRouter.Route.FOCUS_RIGHT"),
        )
        assertTrue(
            "左右键移焦须走 FocusFinder 框架搜索（设置页既有口径同构）",
            activity.contains("FocusFinder.getInstance()"),
        )
    }

    @Test
    fun `B2 底部提示还原画板 15 原文`() {
        val strings = source("src/main/res/values/strings.xml")
        assertTrue(
            "log_viewer_hint 须与画板 15 提示原文一致（qa B2）",
            strings.contains(
                "上下键滚动 · 焦点在按钮时 OK 刷新 / 清空 · 清空需确认 · BACK 返回日志设置",
            ),
        )
    }

    @Test
    fun `观察项2 轮转日志行追加在本批末尾`() {
        val writer = source("src/main/java/com/sofatype/ime/debug/LogFileWriter.kt")
        assertTrue(
            "轮转日志行须在本批末尾（缓冲行为更早事件，落盘时间单调）",
            writer.contains("lines + rotateLine"),
        )
    }
}
