// T63 · 测试打字独立页守卫单测（docs/PRD_20261112.md FR-D1）：
// ① 门控纯决策（TestTypingGate）：决策 21「仅 debug 构建显示」沿用口径锁定（核对记录见类注释）；
// ② 布局扫描：设置页调试区移除 + 第三行「测试打字」入口（样式与检查更新同款 + 无障碍标注）；
//    测试打字页三件套与底部提示落位、全部可交互元素标注齐全；
// ③ 清单扫描：测试打字页无 intent-filter（无 LAUNCHER 入口，不从桌面可达）+ adjustResize 检测通道；
// ④ 接线扫描（Activity 为 Android 交互层，纯 JVM 无法实例化，源码扫描先例：
//    SettingsFocusTakeoverTest / T54DefectFixTest）：设置页入口跳转接线、调试区接线迁出、
//    测试打字页门控与 T54/T62 机制随迁、BACK 返回设置页。
// 纯 JVM，不依赖 Android UI 与模拟器（仓库口径）。
package com.sofatype.ime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T63TestTypingPageTest {

    /** 路径口径 = app/ 模块目录（SettingsFocusTakeoverTest 同口径）。 */
    private fun mainSource(name: String): String =
        File("src/main/java/com/sofatype/ime/$name").readText()

    private val settingsLayout: String get() = File("src/main/res/layout/activity_settings.xml").readText()
    private val testTypingLayout: String get() = File("src/main/res/layout/activity_test_typing.xml").readText()
    private val manifest: String get() = File("src/main/AndroidManifest.xml").readText()

    /** 提取清单中指定 Activity 的声明块（自 `<activity` 起至 `/>` 止）。 */
    private fun activityBlock(manifestText: String, nameAttr: String): String {
        val marker = "android:name=\"$nameAttr\""
        val at = manifestText.indexOf(marker)
        assertTrue("清单缺少 $nameAttr 声明", at >= 0)
        val start = manifestText.lastIndexOf("<activity", at)
        val end = manifestText.indexOf("/>", at)
        assertTrue("清单 $nameAttr 声明块不完整", start >= 0 && end > start)
        return manifestText.substring(start, end + 2)
    }

    /** 提取布局中指定 id 控件的声明块（自节点标签起至开标签结束 `>` 止，容器/自闭合通用）。 */
    private fun viewBlock(layoutText: String, idName: String): String {
        val marker = "@+id/$idName"
        val at = layoutText.indexOf(marker)
        assertTrue("布局缺少 @$idName", at >= 0)
        val start = layoutText.lastIndexOf("<", at)
        val end = layoutText.indexOf(">", at)
        assertTrue("布局 @$idName 声明块不完整", start >= 0 && end > start)
        return layoutText.substring(start, end + 1)
    }

    // ── ① 门控纯决策（决策 21 沿用口径：现行调试区实现 = BuildConfig.DEBUG 可见性）──

    @Test
    fun `门控纯决策为debug构建显示三件套`() {
        assertTrue("debug 构建须显示测试三件套（决策 21 沿用）", TestTypingGate.contentVisible(true))
        assertFalse("release 构建须隐藏测试三件套（决策 21 沿用）", TestTypingGate.contentVisible(false))
    }

    // ── ② 布局扫描：设置页 ──

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
        // 画板 7 第三行：样式与检查更新同款（bg_btn_accent = 常态 key 底无描边、焦点态 wash+描边）
        val checkUpdate = viewBlock(settingsLayout, "btn_check_update")
        for (attr in listOf(
            "android:background=\"@drawable/bg_btn_accent\"",
            "android:textColor=\"@color/accent_text\"",
            "android:textSize=\"@dimen/btn_text\"",
            "android:textStyle=\"bold\"",
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
        // 门控缺省态：三件套默认 GONE，debug 由 Activity 置 VISIBLE（release 整体隐藏）
        val content = viewBlock(testTypingLayout, "test_typing_content")
        assertTrue("三件套容器须默认 gone（决策 21 门控基线）", content.contains("android:visibility=\"gone\""))
    }

    // ── ③ 清单扫描：无 LAUNCHER 入口 ──

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
        // 设置页桌面入口（T60）不回归：既有 LAUNCHER filter 保留
        assertTrue(
            "设置页 LAUNCHER 入口不得回归（T60 既有）",
            activityBlock(manifest, ".SettingsActivity").contains("intent-filter"),
        )
    }

    // ── ④ 接线扫描 ──

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
            "门控须走 TestTypingGate 纯决策（决策 21 沿用口径单点维护）",
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
        // T63 模拟器实测：遥控 OK 单击在 DOWN/UP 各执行一次 performClick，startActivity 双发
        // 堆叠两个测试打字页实例（BACK 语义被副本实例破坏）；两个承载页同修同锁。
        for (name in listOf("SettingsActivity.kt", "TestTypingActivity.kt")) {
            val text = mainSource(name)
            assertTrue(
                "$name 须仅在 DOWN 执行动作、非 DOWN 仅消费（防双触发）",
                text.contains("if (event.action != KeyEvent.ACTION_DOWN)"),
            )
        }
    }
}
