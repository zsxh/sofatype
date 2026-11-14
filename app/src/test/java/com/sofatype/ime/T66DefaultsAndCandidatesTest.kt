// T66 · 键盘默认态与候选交互修订单测（docs/issues/open/T66.md · PRD 决策 51~54，2026-11-13）。
// 锁定五件事：
// ① 决策 52 符号键盘入口角态随入口模式：中文态 → 全角（画板 3/10）、英文态 → 半角（画板 4/11）；
//    手动 [全/半] 仅本次停留生效（返回字母键盘后再进入重新跟随，不记忆手动选择）；
// ② 决策 53 候选栏单行行内翻页（服务层接线）：「更多⌄」展开机制废止、翻页箭头固定候选框两侧
//    （装饰件：不进焦点链、无障碍不标注、明暗表可翻页性）、翻页落点分向随 T67 · 决策 55
//   （下翻落新页首、上翻落回翻页尾）、空格 = 当前页第一候选；
// ③ 决策 54 焦点键底色 .28：Token 色值锁定（rgba(56,189,248,.28) → #4738BDF8）；
// ④ 决策 51-①/② 补充锁定：分词键模型（无组合态忽略 / 组合态插符 / 上屏剥离）在模型测试与
//    服务层扫描两侧覆盖（详见 ChineseComposerTest / T65KeyFaceDefaultsTest / KeyboardLayoutTest）；
// ⑤ 决策 53 页容量按候选栏宽度自适应（CandidatePager.paginate 可用宽度入口，箭头占位扣除）。
// SofatypeImeService 为 Android 服务层，纯 JVM 单测无法实例化（无 Robolectric，仓库口径），
// 接线以源码扫描锁定——先例：T55ModeSwitchCompositionResetTest、CandidatesBarOccupancyTest。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T66DefaultsAndCandidatesTest {

    /** 路径口径 = app/ 模块目录（T55ModeSwitchCompositionResetTest 同口径）。 */
    private fun mainSource(path: String): String =
        File("src/main/$path").readText()

    private val service: String get() = mainSource("java/com/sofatype/ime/SofatypeImeService.kt")
    private val dimens: String get() = mainSource("res/values/dimens.xml")
    private val colors: String get() = mainSource("res/values/colors.xml")
    private val keyboardLayoutXml: String get() = mainSource("res/layout/ime_keyboard.xml")

    /** 取函数体：从签名行起、到下一个成员级 KDoc 边界行止（T55 同口径）。 */
    private fun serviceBody(signature: String): String =
        service.substringAfter(signature).substringBefore("\n    /**")

    // ── ① 决策 52 符号键盘入口角态随入口模式 ──

    @Test
    fun `进入符号键盘角态随入口模式`() {
        val switch = serviceBody("private fun switchKeyboardMode(mode: KeyboardMode)")
        assertTrue(
            "进符号键盘须按语言模式赋角态（中文 → 全角）",
            switch.contains("LanguageMode.CHINESE -> SymbolsWidth.FULL_WIDTH"),
        )
        assertTrue(
            "进符号键盘须按语言模式赋角态（英文 → 半角）",
            switch.contains("LanguageMode.ENGLISH -> SymbolsWidth.HALF_WIDTH"),
        )
        val widthAssignIdx = switch.indexOf("symbolsWidth = when (languageMode)")
        val offsetIdx = switch.indexOf("symbolsWindowOffset = 0")
        val rebuildIdx = switch.indexOf("rebuildKeys(")
        assertTrue("角态赋值须先于窗口复位与键区重建", widthAssignIdx >= 0 && offsetIdx > widthAssignIdx && rebuildIdx > offsetIdx)
        // 手动 [全/半] 仅本次停留生效：入口赋值语义 = 返回后再进入重新跟随（不读任何记忆字段）
        assertFalse(
            "角态入口赋值不得依赖记忆（决策 52：不记忆手动 [全/半] 选择）",
            switch.contains("moreExpanded") || switch.contains("manualWidth"),
        )
    }

    // ── ② 决策 53 候选栏单行行内翻页（服务层接线） ──

    @Test
    fun `候选栏为单行行内翻页模型且更多入口废止`() {
        // 「更多⌄」展开机制废止（FR-A15）：入口 tag / 标注常量 / 展开行容器 / 展开态高度切换全部移除
        assertFalse("「更多⌄」入口 tag 须移除（决策 53）", service.contains("MORE_TOGGLE_ID"))
        assertFalse("「更多⌄」标注常量须移除（决策 53）", service.contains("MORE_DESC_COLLAPSED"))
        assertFalse("展开行容器须移除（决策 53）", service.contains("moreBar"))
        assertFalse("展开态主卡高度切换须移除（决策 53）", service.contains("applyMoreGeometry"))
        assertFalse("展开行刷新链须移除（决策 53）", service.contains("refreshMoreRow"))
        assertFalse("布局文件展开行容器须移除（决策 53）", keyboardLayoutXml.contains("@+id/more_bar"))
        assertFalse("展开态高度 Token 须移除（决策 53）", dimens.contains("ime_card_height_expanded"))
        // 单行翻页模型接线
        assertTrue(
            "候选分页须走 CandidatePager（页区间 = 全局下标，坑 10 相对坐标系消除）",
            service.contains("CandidatePager.paginate("),
        )
        val bind = serviceBody("private fun bindCandidates()")
        assertTrue(
            "分页可用宽度须扣除两端箭头占位（决策 53 箭头固定候选框两侧）",
            bind.contains("candidateArrowReservedPx()"),
        )
        val render = serviceBody("private fun renderCandidatesRow()")
        assertTrue(
            "候选行结构 = 左箭头 + 当前页 chips + 弹性占位 + 右箭头（画板 2）",
            render.contains("createCandArrowView()") && render.contains("1f"),
        )
        val handleRow = serviceBody("private fun handleCandidateRowDirection(currentId: String, direction: FocusDirection)")
        assertTrue(
            "候选行方向导航须走 CandidatePager.move（页缘翻页）",
            handleRow.contains("CandidatePager.move(index, direction, candPages)"),
        )
        val flip = serviceBody("private fun flipCandidatePage(page: Int, focusGlobalIndex: Int)")
        val renderIdx = flip.indexOf("renderCandidatesRow()")
        val focusIdx = flip.indexOf("focusCandidateChip(focusGlobalIndex)")
        assertTrue(
            "翻页后焦点落点须在装载新页之后（决策 53 × T67 · 决策 55：落点分向，装载先行）",
            renderIdx >= 0 && focusIdx > renderIdx,
        )
    }

    @Test
    fun `翻页箭头为装饰件不进焦点链且明暗表可翻页性`() {
        val arrow = serviceBody("private fun createCandArrowView()")
        assertTrue(
            "箭头须无障碍不标注（决策 53：aria-hidden 同义，不进无障碍树）",
            arrow.contains("contentDescription = null") &&
                arrow.contains("IMPORTANT_FOR_ACCESSIBILITY_NO"),
        )
        assertTrue(
            "箭头须不可聚焦（决策 53：不进焦点链，方向导航走 onKeyDown 候选行语义）",
            arrow.contains("isFocusable = false") && arrow.contains("isFocusableInTouchMode = false"),
        )
        assertTrue(
            "箭头键面为 < > 字符样式（决策 53 / 画板 2）",
            arrow.contains("Typeface.BOLD") && service.contains("left.text = \"<\"") && service.contains("right.text = \">\""),
        )
        val refresh = serviceBody("private fun refreshCandArrowAppearance()")
        assertTrue(
            "可翻页方向提亮 #E8EEF5（text_body）、不可翻变暗 rgba(232,239,246,.35)（text_hint_faint）",
            refresh.contains("R.color.text_body") && refresh.contains("R.color.text_hint_faint"),
        )
        assertTrue(
            "可翻页判定随当前页号（回翻 = 页号 > 0、后翻 = 页号 < 末页）",
            refresh.contains("candPage > 0") && refresh.contains("candPage < candPages.size - 1"),
        )
        // 箭头规格 Token（决策 53：约 30px 加粗、宽 28px；1080p ÷ 2 = sp/dp）
        assertTrue(dimens.contains("<dimen name=\"ime_cand_arrow_text\">15sp</dimen>"))
        assertTrue(dimens.contains("<dimen name=\"ime_cand_arrow_width\">14dp</dimen>"))
        assertTrue(dimens.contains("<dimen name=\"ime_cand_arrow_gap\">5dp</dimen>"))
    }

    @Test
    fun `空格选中当前页第一候选`() {
        // 决策 8 × 决策 53：「有候选选首候选」修订为「选当前页第一候选」；无候选输入空格不变
        val space = serviceBody("KeyAction.Space -> {").substringBefore("\n    /**")
        assertTrue(
            "空格选词须取当前页页首候选（candPages[candPage].first）",
            space.contains("candPages.getOrNull(candPage)?.first") &&
                space.contains("pageFirstCandidate"),
        )
        assertFalse(
            "空格不得再直选全局首候选（composer.firstCandidate() 口径随决策 53 修订）",
            space.contains("composer.firstCandidate()"),
        )
        assertTrue(
            "无候选 / 非组合态输入空格口径不变（finishComposition + 空格 / 直出空格）",
            space.contains("commitText(\" \")"),
        )
    }

    @Test
    fun `候选集变化复位页号回首页`() {
        val bind = serviceBody("private fun bindCandidates()")
        assertTrue(
            "候选装载须先复位页态（候选集变化即回首页，决策 53）",
            bind.contains("resetCandidatePageState()"),
        )
        val refresh = serviceBody("private fun refreshCandidatesBar()")
        assertTrue(
            "候选清空路径须同步复位页态",
            refresh.contains("resetCandidatePageState()"),
        )
    }

    // ── ③ 决策 54 焦点键底色 .28 ──

    @Test
    fun `焦点底色Token加深为028`() {
        // rgba(56,189,248,.28)：alpha 0.28 × 255 = 71.4 → 71 = 0x47（colors.xml 换算口径）
        assertTrue(
            "accent_wash_strong 须为 #4738BDF8（决策 54：焦点键底 .16 → .28）",
            colors.contains("<color name=\"accent_wash_strong\">#4738BDF8</color>"),
        )
        assertFalse(
            "不得残留 .16 旧值 #2938BDF8（决策 54 返工 T65 已实现部分）",
            colors.contains("#2938BDF8"),
        )
        // 承载件引用不变：焦点底仍统一走 accent_wash_strong Token（bg_key / chip / done / 按钮）
        for (drawable in listOf("bg_key", "bg_chip_highlight", "bg_key_done", "bg_btn_accent", "bg_side_button")) {
            assertTrue(
                "$drawable 焦点底须引用 accent_wash_strong Token",
                mainSource("res/drawable/$drawable.xml").contains("@color/accent_wash_strong"),
            )
        }
    }
}
