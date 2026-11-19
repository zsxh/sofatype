package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T63TestTypingPageTest {

    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    private val settingsLayout: String get() = File("src/main/res/layout/activity_settings.xml").readText()
    private val testTypingLayout: String get() = File("src/main/res/layout/activity_test_typing.xml").readText()
    private val manifest: String get() = File("src/main/AndroidManifest.xml").readText()

    private fun activityBlock(manifestText: String, nameAttr: String): String {
        val marker = "android:name=\"$nameAttr\""
        val at = manifestText.indexOf(marker)
        assertTrue("清单缺少 $nameAttr 声明", at >= 0)
        val start = manifestText.lastIndexOf("<activity", at)
        val end = manifestText.indexOf("/>", at)
        assertTrue("清单 $nameAttr 声明块不完整", start >= 0 && end > start)
        return manifestText.substring(start, end + 2)
    }

    private fun viewBlock(layoutText: String, idName: String): String {
        val marker = "@+id/$idName"
        val at = layoutText.indexOf(marker)
        assertTrue("布局缺少 @$idName", at >= 0)
        val start = layoutText.lastIndexOf("<", at)
        val end = layoutText.indexOf(">", at)
        assertTrue("布局 @$idName 声明块不完整", start >= 0 && end > start)
        return layoutText.substring(start, end + 1)
    }

    @Test
    fun `门控纯决策为debug与release均显示三件套`() {
        assertTrue("debug 构建须显示测试三件套", TestTypingGate.contentVisible(true))
        assertTrue(
            "release 构建须显示测试三件套（T79 · 决策 65：原仅 debug 门控放开）",
            TestTypingGate.contentVisible(false),
        )
    }

    @Test
    fun `设置页布局移除调试区并新增测试打字入口行`() {
        assertFalse(
            "设置页不得再保留 test_input_area 调试区（FR-D1 本批次细项 2：整体迁出）",
            settingsLayout.contains("@+id/test_input_area"),
        )
        assertFalse(
            "设置页不得再保留调试输入框（已迁至测试打字页）",
            settingsLayout.contains("@+id/test_input\""),
        )
        val entry = viewBlock(settingsLayout, "btn_test_typing")

        val checkUpdate = viewBlock(settingsLayout, "btn_check_update")
        for (attr in listOf(
            "android:background=\"@drawable/bg_btn_accent\"",
            "android:textColor=\"@color/text_body\"",
            "android:textSize=\"@dimen/btn_text\"",
            "android:textStyle=\"normal\"",
            "android:minWidth=\"0dp\"",
        )) {
            assertTrue("测试打字入口与检查更新样式须同款：缺 $attr", entry.contains(attr))
            assertTrue("既有检查更新按钮样式锚点缺失：$attr", checkUpdate.contains(attr))
        }
        assertTrue(
            "测试打字入口须有无障碍标注（FR-D1 本批次细项 5）",
            entry.contains("android:contentDescription=\"@string/cd_btn_test_typing\""),
        )
    }

    @Test
    fun `测试打字页布局三件套与标注齐全`() {
        assertTrue(
            "测试打字页根视图须为 SettingsRootView（pre-IME 分路入口，与设置页同构）",
            testTypingLayout.contains("com.sofatype.ime.SettingsRootView") &&
                testTypingLayout.contains("@+id/test_typing_root"),
        )
        val input = viewBlock(testTypingLayout, "test_input")
        assertTrue(
            "测试输入框须有无障碍标注与占位文案（FR-D1 本批次细项 5 / 画板 9）",
            input.contains("android:contentDescription=\"@string/cd_test_input\"") &&
                input.contains("android:hint=\"@string/test_input_hint\"") &&
                input.contains("android:importantForAccessibility=\"yes\""),
        )
        for (id in listOf("btn_fill_sample", "btn_select_all")) {
            val block = viewBlock(testTypingLayout, id)
            assertTrue("$id 须有无障碍标注", block.contains("android:contentDescription="))
            assertTrue(
                "$id 须与检查更新同款（bg_btn_accent：常态 key 底无描边，决策 44）",
                block.contains("android:background=\"@drawable/bg_btn_accent\""),
            )
        }
        assertTrue(
            "测试打字页底部提示须落位（画板 9：调试走查页 · 由设置页「测试打字」进入）",
            testTypingLayout.contains("@string/test_hint"),
        )

        val content = viewBlock(testTypingLayout, "test_typing_content")
        assertTrue("三件套容器须默认 gone（门控基线，由 Activity 置 VISIBLE）", content.contains("android:visibility=\"gone\""))
    }

    @Test
    fun `测试打字页无intent_filter不从桌面可达`() {
        val block = activityBlock(manifest, ".TestTypingActivity")
        assertFalse(
            "测试打字页不得携带 intent-filter（FR-D1 本批次细项 3：无 LAUNCHER 入口）",
            block.contains("intent-filter"),
        )
        assertTrue(
            "测试打字页须声明 adjustResize（T54 焦点接管收起检测前提）",
            block.contains("android:windowSoftInputMode=\"adjustResize\""),
        )

        assertTrue(
            "设置页 LAUNCHER 入口不得回归（T60 既有）",
            activityBlock(manifest, ".SettingsActivity").contains("intent-filter"),
        )
    }

    @Test
    fun `设置页入口接线且调试区接线迁出`() {
        val activity = mainSource("SettingsActivity.kt")
        assertTrue(
            "设置页须接线测试打字入口跳转（FR-D1 本批次细项 1）",
            activity.contains("startActivity(Intent(this, TestTypingActivity::class.java))"),
        )
        for (moved in listOf("R.id.test_input_area", "R.id.test_input", "btn_fill_sample", "btn_select_all")) {
            assertFalse(
                "调试区接线须已迁出设置页：$moved（T63 整体迁入测试打字页）",
                activity.contains(moved),
            )
        }
    }

    @Test
    fun `测试打字页门控与三件套接线落源码`() {
        val activity = mainSource("TestTypingActivity.kt")
        assertTrue(
            "门控须走 TestTypingGate 纯决策（单点维护，T79 · 决策 65 口径）",
            activity.contains("TestTypingGate.contentVisible(BuildConfig.DEBUG)"),
        )
        assertTrue(
            "「填入示例文本」接线须存在（原设置页调试区行为原样迁入）",
            activity.contains("input.setText(R.string.sample_text)"),
        )
        assertTrue(
            "「全选」接线须存在（原设置页调试区行为原样迁入）",
            activity.contains("input.selectAll()"),
        )
        assertTrue(
            "初始焦点须落测试输入框（遥控 OK 唤起键盘链路）",
            activity.contains("input.requestFocus()"),
        )
    }

    @Test
    fun `测试打字页BACK返回设置页且分路接线同构`() {
        val activity = mainSource("TestTypingActivity.kt")
        assertTrue(
            "须接线 SettingsRootView.preImeKeyHandler（与设置页同构）",
            activity.contains("preImeKeyHandler = { handlePreImeKey(it) }"),
        )
        val backIdx = activity.indexOf("SettingsKeyRouter.Route.BACK")
        assertTrue("BACK 路由分支须存在", backIdx >= 0)
        val body = activity.substring(backIdx, activity.indexOf("SettingsKeyRouter.Route.CONFIRM", backIdx))
        assertTrue(
            "隐藏态 BACK 须结束本页返回设置页（FR-D1 本批次细项 3）",
            body.contains("finish()") && body.contains("返回设置页"),
        )
        assertTrue(
            "键盘显示态须透传 IME（打字链路零改动，T54 R2 口径）",
            activity.contains("if (lastImeShown == true) return false"),
        )
    }

    @Test
    fun `preIME动作仅在DOWN执行防双触发`() {

        for (name in listOf("SettingsActivity.kt", "TestTypingActivity.kt")) {
            val text = mainSource(name)
            assertTrue(
                "$name 须仅在 DOWN 执行动作、非 DOWN 仅消费（防双触发）",
                text.contains("if (event.action != KeyEvent.ACTION_DOWN)"),
            )
        }
    }
}
