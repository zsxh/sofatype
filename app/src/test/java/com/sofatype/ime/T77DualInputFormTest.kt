package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class T77DualInputFormTest {

    private fun mainSource(path: String): String = File("src/main/$path").readText()

    private val testTypingLayout: String get() = mainSource("res/layout/activity_test_typing.xml")
    private val strings: String get() = mainSource("res/values/strings.xml")
    private val activity: String get() = mainSource("java/com/sofatype/ime/TestTypingActivity.kt")

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
    fun `第二输入框落位门控容器且声明于首框之后`() {
        val contentIdx = testTypingLayout.indexOf("@+id/test_typing_content")
        val hintIdx = testTypingLayout.indexOf("@+id/test_hint")
        val inputIdx = testTypingLayout.indexOf("@+id/test_input\"")
        val input2Idx = testTypingLayout.indexOf("@+id/test_input_2")
        assertTrue("布局须含 test_input_2（T77 双框表单）", input2Idx >= 0)
        assertTrue(
            "test_input_2 须声明于 test_input 之后（首框在前：既有三件套语义与 T63 扫描口径不回归）",
            inputIdx >= 0 && inputIdx < input2Idx,
        )
        assertTrue(
            "test_input_2 须位于 test_typing_content 容器内（T79 · 决策 65：debug / release 一致显示）",
            contentIdx >= 0 && hintIdx > contentIdx && input2Idx in (contentIdx + 1) until hintIdx,
        )
    }

    @Test
    fun `第二输入框规格与首框同Token体系且无障碍标注齐备`() {
        val block = viewBlock(testTypingLayout, "test_input_2")
        for (attr in listOf(
            "android:layout_height=\"@dimen/test_input_height\"",
            "android:background=\"@drawable/bg_test_input\"",
            "android:contentDescription=\"@string/cd_test_input_2\"",
            "android:hint=\"@string/test_input_2_hint\"",
            "android:importantForAccessibility=\"yes\"",
            "android:paddingStart=\"@dimen/test_input_padding\"",
            "android:paddingEnd=\"@dimen/test_input_padding\"",
            "android:singleLine=\"true\"",
            "android:textColor=\"@color/text_body\"",
            "android:textColorHint=\"@color/text_muted\"",
            "android:textCursorDrawable=\"@null\"",
            "android:textSize=\"@dimen/test_input_text\"",
        )) {
            assertTrue("test_input_2 须与 test_input 同 Token 体系：缺 $attr", block.contains(attr))
        }
        assertFalse(
            "test_input_2 声明块不得内嵌中文文案（hint / contentDescription 走 strings 资源）",
            Regex("[\\u4e00-\\u9fa5]").containsMatchIn(block),
        )
    }

    @Test
    fun `第二输入框标注字符串资源落位且非空`() {
        for (name in listOf("test_input_2_hint", "cd_test_input_2")) {
            assertTrue(
                "strings 资源缺 $name 或为空（无障碍标注走资源，不硬编码）",
                Regex("<string name=\"$name\">[^<]+</string>").containsMatchIn(strings),
            )
        }
    }

    @Test
    fun `双框目标纯决策为持焦框优先无则回落首框`() {

        assertEquals("第二框", SettingsFocusTakeover.takeoverTarget("第二框", "首框"))
        assertEquals("首框", SettingsFocusTakeover.takeoverTarget("首框", "首框"))

        assertEquals("首框", SettingsFocusTakeover.takeoverTarget(null, "首框"))
    }

    @Test
    fun `双框接线收起沿目标与restartInput目标落源码`() {
        assertTrue("Activity 须接线 test_input_2（T77 双框）", activity.contains("R.id.test_input_2"))
        assertTrue(
            "须装配双框清单（收起沿接管与 T62 重建会话共用目标判定）",
            activity.contains("val editors = listOf(input, input2)"),
        )

        val edgeIdx = activity.indexOf("imeShown != lastImeShown")
        assertTrue("可见性跳变沿判定存在（T54 R2 口径不回归）", edgeIdx >= 0)
        val edgeEnd = activity.indexOf("lastImeShown = imeShown", edgeIdx)
        assertTrue("跳变沿分支闭合存在", edgeEnd > edgeIdx)
        val edgeBody = activity.substring(edgeIdx, edgeEnd)
        assertTrue(
            "收起沿目标须走 takeoverTarget 双框决策",
            edgeBody.contains("val target = SettingsFocusTakeover.takeoverTarget(focusedEditor, input)"),
        )
        assertTrue(
            "收起沿须无条件 requestFocusFromTouch（退出触摸模式口径不回归）",
            edgeBody.contains("target.requestFocusFromTouch()"),
        )
        assertTrue(
            "回落接管日志锚点保留（T54 走查取证口径）",
            edgeBody.contains("焦点接管：键盘已收起，焦点回 test_input"),
        )
        assertTrue(
            "保持持焦框日志锚点（T74 双框切换取证）",
            edgeBody.contains("焦点仍在 test_input_2"),
        )

        assertTrue(
            "T62 重建会话目标须走 takeoverTarget 双框决策",
            activity.contains("val restartTarget = SettingsFocusTakeover.takeoverTarget("),
        )
        assertTrue(
            "收到广播须 restartInput（目标 = 当前持焦框，无则 test_input）",
            activity.contains("imm?.restartInput(restartTarget)"),
        )

        assertTrue("初始焦点仍须落 test_input（遥控 OK 直达唤起键盘链路）", activity.contains("input.requestFocus()"))
    }
}
