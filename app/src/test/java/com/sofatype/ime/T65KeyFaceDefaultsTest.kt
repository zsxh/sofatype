package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T65KeyFaceDefaultsTest {

    private fun mainSource(path: String): String =
        File("src/main/$path").readText()

    private val service: String get() = mainSource("java/com/sofatype/ime/SofatypeImeService.kt")

    private fun serviceBody(signature: String): String =
        service.substringAfter(signature).substringBefore("\n    }")

    @Test
    fun `字段默认态为中文输入与小写`() {
        assertTrue(
            "大小写默认态须为小写（决策 51-③：英文态默认小写输出）",
            service.contains("private var caseState = CaseState.LOWER"),
        )
        assertTrue(
            "语言默认态须为中文（决策 51-①：开机后首次打开 = 中文输入）",
            service.contains("private var languageMode = LanguageMode.CHINESE"),
        )
    }

    @Test
    fun `键盘视图创建不复位语言与大小写会话记忆`() {

        val body = serviceBody("override fun onCreateInputView()")
        assertFalse(
            "键盘视图创建不得重置语言模式（决策 51-① 会话记忆：记住上次中/英）",
            body.contains("languageMode ="),
        )
        assertFalse(
            "键盘视图创建不得重置大小写状态（FR-A13 会话记忆）",
            body.contains("caseState ="),
        )
        assertTrue(
            "键盘视图创建须复位字母键盘布局模式",
            body.contains("keyboardMode = KeyboardMode.LETTERS"),
        )
        assertTrue(
            "键盘视图创建须按当前语言模式装配字母格网（决策 51 行 1 头键随模式分化）",
            body.contains("KeyboardLayout.rows(languageMode)"),
        )
    }

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
            "[全/半] 当前态 = 半角时亮次字符（决策 46-②）",
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

        val toggleLanguage = serviceBody("KeyAction.ToggleLanguage -> {").substringBefore("\n    }")
        assertTrue(
            "中英切换须重建键区（决策 51 行 1 头键随模式分化）",
            toggleLanguage.contains("rebuildKeys(requireNotNull(keyboardView)"),
        )
        assertTrue(
            "中英切换后焦点须保持 [中/英] 键（键区重建后视图重建）",
            toggleLanguage.contains("keyViews[\"lang\"]?.requestFocus"),
        )

        val toggleCase = serviceBody("KeyAction.ToggleCase -> {").substringBefore("KeyAction.OpenSymbols")
        assertFalse(
            "中文态按[⇧]切英文路径须已删除（决策 49 废止，中文 → 英文唯一路径 = [中/英] 键）",
            toggleCase.contains("languageMode"),
        )
    }

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

        assertTrue("⇧ 图标须 30px（T98 口径键内图标统一 30px）", dimens.contains("<dimen name=\"ime_icon_shift\">15dp</dimen>"))
        assertTrue(
            "⇧ 触发态短条 30×3px（T68 画板 trig-bar）",
            dimens.contains("<dimen name=\"ime_shift_underline_width\">15dp</dimen>") &&
                dimens.contains("<dimen name=\"ime_state_underline_height\">1.5dp</dimen>"),
        )
        assertTrue(
            "状态键当前态下划线 2px（T68 画板 st-on thickness:2px，与 ⇧ 3px 短条分列）",
            dimens.contains("<dimen name=\"ime_state_char_underline_height\">1dp</dimen>"),
        )
        val underline = mainSource("res/drawable/bg_key_underline.xml")
        assertTrue(
            "下划线条色须走 accent_text Token（决策 47-① 当前项加亮）",
            underline.contains("@color/accent_text"),
        )
    }

    @Test
    fun `键面随语言模式与大小写输出状态`() {
        val a = KeyboardLayout.keyById.getValue("a")

        assertEquals("A", KeyboardLayout.displayLabel(a, chineseMode = true, upperCase = false))
        assertEquals("a", KeyboardLayout.displayLabel(a, chineseMode = false, upperCase = false))
        assertEquals("A", KeyboardLayout.displayLabel(a, chineseMode = false, upperCase = true))

        assertEquals("a", KeyboardLayout.outputValue(a, CaseState.LOWER))
        assertEquals("A", KeyboardLayout.outputValue(a, CaseState.UPPER))

        val createTextKey = serviceBody("private fun createTextKey(key: Key)")
        assertTrue(
            "服务层键面装配须随语言模式与大小写状态（决策 51-②/③）",
            createTextKey.contains("KeyboardLayout.displayLabel(") &&
                createTextKey.contains("languageMode == LanguageMode.CHINESE") &&
                createTextKey.contains("caseState == CaseState.UPPER"),
        )

        val applyCase = serviceBody("private fun applyCaseState()")
        assertTrue(
            "⇧ 切换须刷新字母键面（决策 51-③ 键面随大小写输出状态）",
            applyCase.contains("refreshLetterKeyFaces()"),
        )
        val refreshFaces = serviceBody("private fun refreshLetterKeyFaces()")
        assertTrue(
            "字母键面刷新须同步文字与朗读标注（TalkBack 同文）",
            refreshFaces.contains("view.text = label") && refreshFaces.contains("view.contentDescription = label"),
        )
    }

    @Test
    fun `英文态按上档维持纯大小写切换`() {

        val toggleCase = serviceBody("KeyAction.ToggleCase -> {").substringBefore("\n    }")
        val flipIdx = toggleCase.indexOf("caseState = if (caseState == CaseState.UPPER)")
        val applyIdx = toggleCase.indexOf("applyCaseState()")
        assertTrue("英文态纯大小写翻转须保留（FR-A3）", flipIdx >= 0)
        assertTrue("键面/触发态刷新（applyCaseState）须保留", applyIdx > flipIdx)
    }

    @Test
    fun `分词键仅在组合态生效且中英切换为唯一切英路径`() {

        val segmentBranch = serviceBody("KeyAction.Segment -> {").substringBefore("\n    }")
        assertTrue(
            "分词键须在组合态内插切分符（composer.appendSegment）",
            segmentBranch.contains("composer.appendSegment()"),
        )
        val compositionGuard = segmentBranch.indexOf("composer.composition.isNotEmpty()")
        val appendIdx = segmentBranch.indexOf("composer.appendSegment()")
        assertTrue("分词键须先判组合态（无组合态忽略，决策 51-②）", compositionGuard >= 0 && appendIdx > compositionGuard)
        assertTrue(
            "分词插符后须刷新组合串与候选栏",
            segmentBranch.contains("updateComposingText()") && segmentBranch.contains("refreshCandidatesBar()"),
        )

        val toggleLanguage = serviceBody("KeyAction.ToggleLanguage -> {").substringBefore("\n    }")
        assertTrue(
            "中英切换须先取消组合态（决策 43-② 复位口径不变）",
            toggleLanguage.contains("cancelComposition()"),
        )
        val lowerIdx = toggleLanguage.indexOf("caseState = CaseState.LOWER")
        val englishIdx = toggleLanguage.indexOf("LanguageMode.ENGLISH")
        assertTrue(
            "切英文须固定落小写（决策 51-③：英文态默认小写输出）",
            lowerIdx >= 0 && englishIdx >= 0 && lowerIdx > englishIdx,
        )

        val finish = serviceBody("private fun finishComposition()")
        assertTrue(
            "组合串落定须剥离切分符（决策 51-②：原样上屏不带切分符）",
            finish.contains("composer.committedText()"),
        )

        assertTrue(
            "字母格网装配须随语言模式（决策 51 行 1 头键分化）",
            serviceBody("private fun rebuildKeys(root: View, requestInitialFocus: Boolean)")
                .contains("KeyboardLayout.rows(languageMode)"),
        )
    }

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
            "键帽焦点底须统一 accent-wash 淡青底 rgba(56,189,248,.28)（决策 50-② × 决策 54）",
            blockAround(bgKey, "state_focused").contains("@color/accent_wash_strong") &&
                blockAround(bgKey, "state_focused").contains("@color/border_accent"),
        )
        val chipHighlight = mainSource("res/drawable/bg_chip_highlight.xml")
        assertTrue(
            "候选 chip 焦点底须统一 accent-wash 淡青底（决策 50-②）",
            chipHighlight.contains("@color/accent_wash_strong"),
        )
        val chipAppearance = serviceBody("private fun applyChipAppearance(view: View)")
        assertTrue(
            "焦点文字须 #E8EEF5（决策 50-②：#7DD3FC 焦点青字废止）",
            chipAppearance.contains("R.color.text_body") &&
                !chipAppearance.contains("R.color.accent_text"),
        )

    }

    @Test
    fun `完成键常态与普通键一致`() {

        val bgKeyDone = mainSource("res/drawable/bg_key_done.xml")

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

        assertTrue("下屏图标键面 30px（T98 口径键内图标统一 30px）", dimens.contains("<dimen name=\"ime_icon_hide\">15dp</dimen>"))
    }

    @Test
    fun `焦点键与状态键形态不回归`() {

        assertTrue(
            "键帽焦点底由 bg_key selector 表达（决策 44 / 50-② 口径）",
            serviceBody("private fun backgroundOf(variant: KeyVariant)").contains("KeyVariant.CHARACTER, KeyVariant.FUNCTION -> R.drawable.bg_key"),
        )

        assertTrue(
            "[退出远程]须保持可聚焦（决策 50-① 前提）",
            service.contains("remoteExitButton?.apply {") && service.contains("isFocusableInTouchMode = true"),
        )
    }
}
