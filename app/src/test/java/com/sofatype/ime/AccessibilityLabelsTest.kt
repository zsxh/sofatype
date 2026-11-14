// T26 · 无障碍标注清单单元测试（PRD 第 8 章 / 画板 aria-label 标注集 / TASKS 验收点「标注清单核对」）。
// 锁定三件事：
// ① TV 端全部键位（字母键盘 + 符号键盘全/半角两态，含浮层空格/完成键）contentDescription 非空白；
// ② 设计稿已标注键位与跨屏可交互元素的标注文案逐字锁定（上档 / 分词 / 下屏 / 删除 / 光标左右 /
//    进入远程输入模式 / 退出远程模式 / 中英 / 符号 / 全半 / 返回；「更多⌄」入口随 T66 · 决策 53 废止）；
// ③ 手机 H5 页（assets/remote/index.html）全部可交互元素 aria-label 存在。
// 候选 chips 与设置页控件的标注由视图层 / XML 资源承载（chips 标注 = 候选词本词，
// 设置页 contentDescription 走 strings.xml cd_* 资源），无纯 JVM 断言形态，核查见 issue T26 清单。
// 纯 JVM，不依赖 Android UI 与模拟器（TASKS T5 施工口径）。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.io.File
import org.junit.Test

class AccessibilityLabelsTest {

    // ---- ① TV 端：全键位标注非空白 ----

    @Test
    fun `字母键盘全部键位有无障碍标注`() {
        val keys = KeyboardLayout.allKeys()
        assertTrue("字母键盘键位不应为空", keys.isNotEmpty())
        for (key in keys) {
            assertTrue("字母键盘键位 ${key.id} 缺少无障碍标注", key.contentDescription.isNotBlank())
        }
    }

    @Test
    fun `符号键盘全角半角两态全部键位有无障碍标注`() {
        // T64 行步进窗口：两窗口态（默认态 + 下移态）并集 = 全矩阵 35 键位 + 数字/功能列 + 浮层键
        for (width in listOf(SymbolsWidth.FULL_WIDTH, SymbolsWidth.HALF_WIDTH)) {
            val keys = (0..SymbolsLayout.MAX_WINDOW_OFFSET).flatMap { offset -> SymbolsLayout.allKeys(width, offset) }
            assertTrue("符号键盘键位不应为空（$width）", keys.isNotEmpty())
            for (key in keys) {
                assertTrue("符号键盘键位 ${key.id}（$width）缺少无障碍标注", key.contentDescription.isNotBlank())
            }
        }
    }

    // ---- ② 设计稿已标注键位文案逐字锁定 ----

    @Test
    fun `设计稿已标注键位文案锁定`() {
        val byId = KeyboardLayout.keyById
        // 画板① aria-label：上档切换 / 下屏收起键盘 / 删除 / 光标左移 / 光标右移
        assertEquals("上档切换", byId.getValue("shift").contentDescription)
        assertEquals("下屏，收起键盘", byId.getValue("hide").contentDescription)
        assertEquals("删除", byId.getValue("backspace").contentDescription)
        assertEquals("光标左移", byId.getValue("cursor_left").contentDescription)
        assertEquals("光标右移", byId.getValue("cursor_right").contentDescription)
        // T66 · 决策 51-②（画板 1/2 aria-label）：分词键三细节语义
        assertEquals(
            "分词：组合态内切分拼音（如 xi'an），选词后不上屏；无组合态时忽略",
            byId.getValue("segment").contentDescription,
        )
        // T65 · 决策 46-② / 47-① / 48-①：上档与状态键运行态描述含当前态 + 动作
        assertEquals("上档切换，当前大写", KeyboardLayout.shiftContentDescription(CaseState.UPPER))
        assertEquals("上档切换，当前小写", KeyboardLayout.shiftContentDescription(CaseState.LOWER))
        assertEquals("中英文切换，当前为中文", KeyboardLayout.langContentDescription(chinese = true))
        assertEquals("中英文切换，当前为英文", KeyboardLayout.langContentDescription(chinese = false))
        assertEquals("全角半角切换，当前为半角", SymbolsLayout.widthContentDescription(halfWidth = true))
        assertEquals("全角半角切换，当前为全角", SymbolsLayout.widthContentDescription(halfWidth = false))
        // 功能键动作语义（与 SymbolsLayout 返回键「返回字母键盘」同构的动作描述口径）
        assertEquals("中英文切换", byId.getValue("lang").contentDescription)
        assertEquals("进入符号键盘", byId.getValue("symbols").contentDescription)
        // 符号键盘（画板③④ aria）：全/半、返回、删除、下屏
        val symbolsById = SymbolsLayout.allKeys(SymbolsWidth.FULL_WIDTH).associateBy { it.id }
        assertEquals("全角半角切换", symbolsById.getValue(SymbolsLayout.TOGGLE_WIDTH_ID).contentDescription)
        assertEquals("返回字母键盘", symbolsById.getValue(SymbolsLayout.BACK_ID).contentDescription)
        assertEquals("删除", symbolsById.getValue("delete").contentDescription)
        assertEquals("下屏，收起键盘", symbolsById.getValue("hide").contentDescription)
    }

    @Test
    fun `跨屏按钮标注文案锁定`() {
        // 画板① 引导卡主按钮 / 画板⑤「退出远程」按钮 aria-label（RemoteUiText 常量，T17 既有）
        assertEquals("进入远程输入模式", RemoteUiText.GUIDE_BUTTON_DESCRIPTION)
        assertEquals("退出远程模式", RemoteUiText.EXIT_BUTTON_DESCRIPTION)
        // 「更多⌄」入口两态标注随 T66 · 决策 53 废止删除（FR-A15 展开机制废止，
        // 行内翻页箭头为装饰件、无障碍不标注，不设 contentDescription）
    }

    // ---- ③ 手机 H5 页：全部可交互元素 aria-label ----

    /**
     * H5 页可交互元素全集（FR-C2~C6/C8，5 个）：重连按钮、编辑卡唤起键盘的透明输入层、
     * 删除、清空、上屏。装饰件（状态点 / 分隔线 / 电视图标 / 光标）已标 aria-hidden，
     * 状态与提示走 aria-live，不在交互元素口径内。
     * 单测工作目录 = :app 模块目录，直读 assets 源文件断言。
     */
    @Test
    fun `手机H5页交互元素aria-label齐全`() {
        val html = File("src/main/assets/remote/index.html").readText()
        val expectedLabels = listOf(
            "重连", // btn-reconnect（FR-C8 断连重连入口）
            "远程输入内容", // ghost-input（FR-C3 编辑卡唤起键盘输入层）
            "删除一个字符", // btn-delete（FR-C5）
            "清空全部内容", // btn-clear（FR-C5）
            "上屏", // btn-commit（FR-C6）
        )
        for (label in expectedLabels) {
            assertTrue("H5 页缺少 aria-label「$label」", html.contains("aria-label=\"$label\""))
        }
    }
}
