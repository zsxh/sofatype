// T65 · 键盘键面样式、默认态与按键高亮原则调整单测（docs/issues/open/T65.md · 决策 46~50）。
// 锁定七件事：
// ① 默认态初始化：字母键盘默认英文输入 + 大写（⇧ 触发态）、符号键盘默认半角（决策 46-③ / 48-①）；
// ② 状态键当前态渲染：[中/英][全/半] 方案 C 斜杠排版 + 当前态亮青加粗下划线 + 含当前态描述（决策 46-② / 47-①）；
// ③ ⇧ 激活态形态：图标亮青 + 图标下方 3px 短下划线条、图标 32px 笔画加粗（决策 46-④ / 47-①）；
// ④ 键面恒显大写与输出解耦（决策 48-②）；
// ⑤ 中文态按[⇧]落英文小写：组合与候选取消不落定、固定小写不翻转（决策 49）；
// ⑥ 远程模式默认焦点：[退出远程] 进入即持有焦点（决策 50-①）；
// ⑦ 焦点底色口径：全部焦点键 accent-wash 淡青底 .16 + border-accent 描边 + 焦点文字 #E8EEF5
//    （决策 50-②）；[完成] 常态不高亮（决策 46-①）与非选中键样式一致（决策 47-②）。
// SofatypeImeService 为 Android 服务层，纯 JVM 单测无法实例化（无 Robolectric，仓库口径），
// 接线以源码扫描锁定——先例：T55ModeSwitchCompositionResetTest、T59HiddenKeySemanticsTest。
package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T65KeyFaceDefaultsTest {

    /** 路径口径 = app/ 模块目录（T55ModeSwitchCompositionResetTest / T63TestTypingPageTest 同口径）。 */
    private fun mainSource(path: String): String =
        File("src/main/$path").readText()

    private val service: String get() = mainSource("java/com/sofatype/ime/SofatypeImeService.kt")
    private val layoutModel: String get() = mainSource("java/com/sofatype/ime/KeyboardLayout.kt")
    private val symbolsModel: String get() = mainSource("java/com/sofatype/ime/SymbolsLayout.kt")

    /** 取函数体：从签名行起、到下一个成员级 KDoc 边界行止（T55 同口径）。 */
    private fun serviceBody(signature: String): String =
        service.substringAfter(signature).substringBefore("\n    /**")

    // ── ① 默认态初始化（决策 46-③ / 48-①） ──

    @Test
    fun `字段默认态为英文输入加大写与符号半角`() {
        assertTrue(
            "大小写默认态须为大写（决策 48-①：键盘打开即 ⇧ 触发态）",
            service.contains("private var caseState = CaseState.UPPER"),
        )
        assertTrue(
            "语言默认态须为英文（决策 46-③ / 48-①：字母键盘默认英文输入）",
            service.contains("private var languageMode = LanguageMode.ENGLISH"),
        )
        assertTrue(
            "全半角默认态须为半角（决策 46-③：符号键盘默认半角）",
            service.contains("private var symbolsWidth = SymbolsWidth.HALF_WIDTH"),
        )
    }

    @Test
    fun `键盘视图创建复位默认态`() {
        val body = serviceBody("override fun onCreateInputView()")
        val upperIdx = body.indexOf("caseState = CaseState.UPPER")
        val englishIdx = body.indexOf("languageMode = LanguageMode.ENGLISH")
        val halfIdx = body.indexOf("symbolsWidth = SymbolsWidth.HALF_WIDTH")
        assertTrue(
            "键盘视图创建须复位英文 + 大写 + 半角默认态（决策 46-③ / 48-①）",
            upperIdx >= 0 && englishIdx >= 0 && halfIdx >= 0,
        )
    }

    // ── ② 状态键当前态渲染（决策 46-② 方案 C / 47-①） ──

    @Test
    fun `状态键模型带两态字符选项`() {
        val lang = KeyboardLayout.keyById.getValue("lang")
        val width = SymbolsLayout.allKeys(SymbolsWidth.HALF_WIDTH)
            .first { it.id == SymbolsLayout.TOGGLE_WIDTH_ID }
        assertEquals("[中/英] 两态字符（斜杠排版序）", "中" to "英", lang.stateOptions)
        assertEquals("[全/半] 两态字符（斜杠排版序）", "全" to "半", width.stateOptions)
    }

    @Test
    fun `服务层渲染状态键并随切换刷新`() {
        assertTrue(
            "状态键须走专用视图装配（createStateKeyView）",
            service.contains("key.stateOptions != null -> createStateKeyView(key)"),
        )
        val render = serviceBody("private fun applyStateKeyAppearance(view: View)")
        assertTrue(
            "[中/英] 当前态 = 中文时亮首字符（决策 46-②：指示随切换实时随动）",
            render.contains("if (languageMode == LanguageMode.CHINESE) 0 else 1"),
        )
        assertTrue(
            "[全/半] 当前态 = 半角时亮次字符（决策 46-②；默认半角即亮「半」）",
            render.contains("if (symbolsWidth == SymbolsWidth.HALF_WIDTH) 1 else 0"),
        )
        assertTrue(
            "当前态字符须亮青 #7DD3FC 加粗 + 下划线、另一态压暗（决策 46-② 方案 C）",
            render.contains("R.color.accent_text") &&
                render.contains("Typeface.BOLD") &&
                render.contains("R.color.text_faint"),
        )
        assertTrue(
            "状态键无障碍描述须含当前态（决策 46-②）",
            render.contains("langContentDescription") && render.contains("widthContentDescription"),
        )
        val toggleLanguage = service.substringAfter("KeyAction.ToggleLanguage -> {")
            .substringBefore("字母键盘 ↔ 符号键盘切换（FR-A5）")
        assertTrue(
            "[中/英] 切换后须刷新状态键指示（决策 46-② 实时随动）",
            toggleLanguage.contains("applyStateKeyAppearance()"),
        )
        // Round 2 打回 D1 同源锁定：中文态按[⇧]切英文（决策 49 路径）同样须刷新 [中/英] 指示
        val toggleCase = service.substringAfter("KeyAction.ToggleCase -> {")
            .substringBefore("KeyAction.OpenSymbols")
        val chineseBranch = toggleCase.substringAfter("if (languageMode == LanguageMode.CHINESE)")
            .substringBefore("} else {")
        assertTrue(
            "中文态按[⇧]切英文后须刷新状态键指示（决策 46-② 实时随动，Round 2 D1）",
            chineseBranch.contains("applyStateKeyAppearance()"),
        )
        val stateRefreshIdx = chineseBranch.indexOf("applyStateKeyAppearance()")
        val englishIdxForState = chineseBranch.indexOf("languageMode = LanguageMode.ENGLISH")
        assertTrue(
            "状态键指示刷新须在语言切换赋值之后（读最新模式）",
            stateRefreshIdx > englishIdxForState,
        )
    }

    // ── ③ ⇧ 激活态形态（决策 46-④ / 47-①） ──

    @Test
    fun `上档键激活态为图标亮青加下划线条`() {
        val applyCase = serviceBody("private fun applyCaseState()")
        assertTrue(
            "⇧ 触发态图标须亮青（决策 47-①：激活底色与描边废止）",
            applyCase.contains("if (upper) R.color.accent_text else R.color.text_secondary"),
        )
        val barIdx = applyCase.indexOf("SHIFT_BAR_TAG")
        val visibleIdx = applyCase.indexOf("View.VISIBLE else View.GONE")
        assertTrue("⇧ 触发态须显隐下划线条（决策 47-①）", barIdx >= 0 && visibleIdx > barIdx)
        assertFalse(
            "⇧ 键不得再走图标键直绘路径（T65 改容器视图：图标 + 下划线条）",
            serviceBody("private fun createIconKey(key: Key)").contains("KeyIcon.SHIFT"),
        )
        assertTrue(
            "⇧ 键视图须含图标与下划线条 tag（触发态刷新锚点）",
            service.contains("SHIFT_ICON_TAG") && service.contains("SHIFT_BAR_TAG"),
        )
    }

    @Test
    fun `上档图标放大加粗与下划线条规格`() {
        val shiftIcon = mainSource("res/drawable/ic_key_shift.xml")
        assertTrue(
            "⇧ 图标笔画须加粗（决策 46-④：1.8→2）",
            shiftIcon.contains("android:strokeWidth=\"2\""),
        )
        val dimens = mainSource("res/values/dimens.xml")
        assertTrue("⇧ 图标须 32px（决策 46-④：24→32px）", dimens.contains("<dimen name=\"ime_icon_shift\">16dp</dimen>"))
        assertTrue(
            "⇧ 触发态短条 36×3px（画板 trig-bar）",
            dimens.contains("<dimen name=\"ime_shift_underline_width\">18dp</dimen>") &&
                dimens.contains("<dimen name=\"ime_state_underline_height\">1.5dp</dimen>"),
        )
        val underline = mainSource("res/drawable/bg_key_underline.xml")
        assertTrue(
            "下划线条色须走 accent_text Token（决策 47-① 当前项加亮）",
            underline.contains("@color/accent_text"),
        )
    }

    // ── ④ 键面恒显大写与输出解耦（决策 48-②） ──

    @Test
    fun `键面恒大写与输出大小写解耦`() {
        val a = KeyboardLayout.keyById.getValue("a")
        // 键面不随大小写输出状态或输入模式变化
        assertEquals("A", KeyboardLayout.displayLabel(a))
        // 输出仅由 ⇧ 触发态表达：触发 = 大写、未触发 = 小写
        assertEquals("a", KeyboardLayout.outputValue(a, CaseState.LOWER))
        assertEquals("A", KeyboardLayout.outputValue(a, CaseState.UPPER))
        // 服务层键面装配走恒大写口径（无大小写参数）
        assertTrue(
            "服务层键面装配须用键面恒显大写口径（决策 48-②）",
            serviceBody("private fun createTextKey(key: Key)").contains("KeyboardLayout.displayLabel(key)"),
        )
        assertFalse(
            "键面装配不得再随大小写状态取键面（键面与输出解耦）",
            service.contains("displayLabel(key, caseState)"),
        )
    }

    // ── ⑤ 中文态按[⇧]落英文小写（决策 49，修订决策 43-①） ──

    @Test
    fun `中文态按上档取消组合固定落英文小写`() {
        val toggleCase = service.substringAfter("KeyAction.ToggleCase -> {")
            .substringBefore("KeyAction.OpenSymbols")
        val chineseBranch = toggleCase.substringAfter("if (languageMode == LanguageMode.CHINESE)")
            .substringBefore("} else {")
        assertTrue(
            "中文态按[⇧]须取消组合与候选、不落定上屏（决策 49 / 决策 43 取消语义不变）",
            chineseBranch.contains("cancelComposition()"),
        )
        assertTrue(
            "中文态按[⇧]须切英文输入态（决策 49）",
            chineseBranch.contains("languageMode = LanguageMode.ENGLISH"),
        )
        assertTrue(
            "中文态按[⇧]须固定落英文小写（决策 49：⇧ 未触发，不随该键翻转）",
            chineseBranch.contains("caseState = CaseState.LOWER"),
        )
        assertFalse(
            "中文态按[⇧]不得翻转大小写（决策 49 废止决策 43-① 翻转口径）",
            chineseBranch.contains("caseState = if (caseState"),
        )
        val refreshIdx = chineseBranch.indexOf("refreshCandidatesBar()")
        val englishIdx = chineseBranch.indexOf("languageMode = LanguageMode.ENGLISH")
        assertTrue("取消组合后须刷新候选栏清空 chips", refreshIdx > englishIdx)
        // Round 2 打回 D1：切英文后 [中/英] 键面与无障碍描述须随动（applyStateKeyAppearance 内含
        // langContentDescription 赋 contentDescription，键面亮「英」、描述「当前为英文」）
        val stateIdx = chineseBranch.indexOf("applyStateKeyAppearance()")
        assertTrue(
            "中文态按[⇧]切英文后须刷新 [中/英] 指示（键面 + 无障碍描述随动，决策 46-②）",
            stateIdx >= 0,
        )
        assertTrue("指示刷新须在候选栏刷新同拍（切英文后）", stateIdx > refreshIdx)
    }

    @Test
    fun `英文态按上档维持纯大小写切换`() {
        // FR-A3 不回归（T55 同口径）：大小写翻转与键面刷新在中文分支之外
        val toggleCase = service.substringAfter("KeyAction.ToggleCase -> {")
            .substringBefore("KeyAction.OpenSymbols")
        val chineseBranchEnd = toggleCase.indexOf("} else {")
        val flipIdx = toggleCase.indexOf("caseState = if (caseState == CaseState.UPPER)")
        val applyIdx = toggleCase.indexOf("applyCaseState()")
        assertTrue("英文态纯大小写翻转须保留（FR-A3）", flipIdx > chineseBranchEnd)
        assertTrue("键面/触发态刷新（applyCaseState）须保留", applyIdx > flipIdx)
    }

    // ── ⑥ 远程模式默认焦点（决策 50-①） ──

    @Test
    fun `进入远程模式退出远程键默认持有焦点`() {
        val renderMode = serviceBody("private fun renderMainCardMode(state: RemoteUiState)")
        assertTrue(
            "进远程模式沿（等待扫码 / 已连接）须焦点落[退出远程]（决策 50-①）",
            renderMode.contains("remoteExitButton?.requestFocus(View.FOCUS_DOWN)"),
        )
        val startView = serviceBody("override fun onStartInputView(info: EditorInfo?, restarting: Boolean)")
        val runningIdx = startView.indexOf("RemoteInputServerManager.isRunning()")
        val focusIdx = startView.indexOf("remoteExitButton?.requestFocus(View.FOCUS_DOWN)")
        assertTrue(
            "键盘重建 / 重显时远程运行中须焦点落[退出远程]（决策 50-① 两态默认焦点）",
            runningIdx >= 0 && focusIdx > runningIdx,
        )
    }

    // ── ⑦ 焦点底色口径与非选中键一致（决策 46-① / 47-② / 50-②） ──

    /** 取 drawable / layout 文件中 [marker] 起到下一个 `</item>`（或 `/>`）前的声明块。 */
    private fun blockAround(text: String, marker: String): String {
        val at = text.indexOf(marker)
        assertTrue("未找到锚点 $marker", at >= 0)
        val end = text.indexOf("</item>", at).let { if (it < 0) text.length else it }
        return text.substring(at, end)
    }

    @Test
    fun `全部焦点键统一淡青焦点底`() {
        val bgKey = mainSource("res/drawable/bg_key.xml")
        assertFalse(
            "焦点底不得再使用灰调 #242C39（决策 50-② 废止）",
            blockAround(bgKey, "state_focused").contains("@color/key_hover"),
        )
        assertTrue(
            "键帽焦点底须统一 accent-wash 淡青底 rgba(56,189,248,.16)（决策 50-②）",
            blockAround(bgKey, "state_focused").contains("@color/accent_wash_strong") &&
                blockAround(bgKey, "state_focused").contains("@color/border_accent"),
        )
        val chipHighlight = mainSource("res/drawable/bg_chip_highlight.xml")
        assertTrue(
            "候选 chip 焦点底须统一 accent-wash 淡青底（决策 50-②）",
            chipHighlight.contains("@color/accent_wash_strong"),
        )
        val chipAppearance = serviceBody("private fun applyChipAppearance(view: View)")
        val toggleAppearance = serviceBody("private fun applyToggleAppearance(view: TextView)")
        assertTrue(
            "焦点文字须 #E8EEF5（决策 50-②：#7DD3FC 焦点青字废止）",
            chipAppearance.contains("R.color.text_body") &&
                toggleAppearance.contains("R.color.text_body") &&
                !chipAppearance.contains("R.color.accent_text") &&
                !toggleAppearance.contains("R.color.accent_text"),
        )
    }

    @Test
    fun `完成键常态与普通键一致`() {
        // 决策 46-①：[完成] 常态不高亮、样式与普通键一致
        val bgKeyDone = mainSource("res/drawable/bg_key_done.xml")
        // 常态块 = 最后一个 <item>（selector 依序 pressed / focused / normal）
        val normalBlock = bgKeyDone.substringAfterLast("<item")
        assertTrue(
            "完成键常态底须为键帽同款 key 底（决策 46-① / 决策 44 常态口径）",
            normalBlock.contains("@color/key") && !normalBlock.contains("accent_wash"),
        )
        assertTrue(
            "完成键常态文字须与普通键一致 #E8EEF5（决策 46-①，青字废止）",
            serviceBody("private fun textColorOf(variant: KeyVariant)").contains("KeyVariant.ACCENT -> R.color.text_body"),
        )
        val focusBlock = blockAround(bgKeyDone, "state_focused")
        assertTrue(
            "完成键焦点底须统一 accent-wash 淡青底（决策 50-②）",
            focusBlock.contains("@color/accent_wash_strong"),
        )
    }

    @Test
    fun `非选中功能按键文字与字重一致`() {
        // 决策 47-②：文字 #E8EEF5、字重 400（青字与 600 字重废止）；背景键帽同款 + 中性边框维持
        val settingsLayout = mainSource("res/layout/activity_settings.xml")
        for (id in listOf("btn_check_update", "btn_test_typing")) {
            val block = settingsLayout.substringAfter("@+id/$id").substringBefore("/>")
            assertTrue("$id 文字须 #E8EEF5（决策 47-②）", block.contains("android:textColor=\"@color/text_body\""))
            assertFalse("$id 不得再用青字（决策 47-② 废止）", block.contains("@color/accent_text"))
            assertTrue("$id 字重须 400（决策 47-②）", block.contains("android:textStyle=\"normal\""))
        }
        val testLayout = mainSource("res/layout/activity_test_typing.xml")
        for (id in listOf("btn_fill_sample", "btn_select_all")) {
            val block = testLayout.substringAfter("@+id/$id").substringBefore("/>")
            assertTrue("$id 文字须 #E8EEF5（决策 47-②）", block.contains("android:textColor=\"@color/text_body\""))
            assertTrue("$id 字重须 400（决策 47-②）", block.contains("android:textStyle=\"normal\""))
        }
        val guideCard = mainSource("java/com/sofatype/ime/GuideCard.kt")
        val buttonBody = guideCard.substringAfter("val button = TextView(context).apply {")
            .substringBefore("content.addView(")
        assertTrue(
            "副卡[远程输入]文字须 #E8EEF5（决策 47-②）",
            buttonBody.contains("R.color.text_body") && !buttonBody.contains("R.color.accent_text"),
        )
        val keyboardLayout = mainSource("res/layout/ime_keyboard.xml")
        val exitBlock = keyboardLayout.substringAfter("@+id/remote_exit_button").substringBefore("/>")
        assertTrue(
            "远程模式[退出远程]文字须 #E8EEF5（决策 47-②）",
            exitBlock.contains("android:textColor=\"@color/text_body\"") &&
                !exitBlock.contains("@color/text_secondary"),
        )
    }

    @Test
    fun `下屏键图标为键盘面加下箭头`() {
        val hideIcon = mainSource("res/drawable/ic_key_hide.xml")
        assertTrue(
            "收起键图标须含键盘面矩形（决策 46-⑤）",
            hideIcon.contains("M5,3.5 H19 A2,2 0 0 1 21,5.5 V12.5"),
        )
        assertTrue(
            "收起键图标须含键盘下方加粗下箭头（决策 46-⑤）",
            hideIcon.contains("M8,17.5 L12,21 L16,17.5") && hideIcon.contains("android:strokeWidth=\"2.2\""),
        )
        val dimens = mainSource("res/values/dimens.xml")
        assertTrue("下屏图标键面 30px（画板 SVG 尺寸）", dimens.contains("<dimen name=\"ime_icon_hide\">15dp</dimen>"))
    }

    @Test
    fun `焦点键与状态键形态不回归`() {
        // 决策 50-②：焦点文字 #E8EEF5 —— 键帽文字键焦点态不换青字（背景 selector 承载焦点底）
        assertTrue(
            "键帽焦点底由 bg_key selector 表达（决策 44 / 50-② 口径）",
            serviceBody("private fun backgroundOf(variant: KeyVariant)").contains("KeyVariant.CHARACTER, KeyVariant.FUNCTION -> R.drawable.bg_key"),
        )
        // 远程模式两态默认焦点之外：远程模式唯一可聚焦件仍为[退出远程]
        assertTrue(
            "[退出远程]须保持可聚焦（决策 50-① 前提）",
            service.contains("remoteExitButton?.apply {") && service.contains("isFocusableInTouchMode = true"),
        )
    }
}
