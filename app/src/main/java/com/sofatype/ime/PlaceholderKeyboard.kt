// T3 · 占位键盘纯逻辑（FR-A1 最小闭环）。
// 不依赖 Android 类，可脱离模拟器独立单元测试；真实键位模型属 T5+，勿在此扩展。
package com.sofatype.ime

/** 按键动作：占位键盘一次点按产生的效果。 */
sealed class KeyAction {
    /** 向焦点输入框光标处提交文本。 */
    data class CommitText(val text: String) : KeyAction()

    /** 收起键盘（FR-A1）。 */
    object HideSelf : KeyAction()
}

/** 占位键盘键位。 */
enum class PlaceholderKey {
    /** 示例字符键：提交 1 个字符，验证最小输入闭环。 */
    SAMPLE,

    /** 下屏键：收起键盘（FR-A1）。 */
    DISMISS,
}

/** 键位 → 动作的映射（唯一决策逻辑，T3 单元测试对象）。 */
object PlaceholderKeyboard {
    /** 示例字符：固定单字符，满足「输入 1 个字符」验收口径。 */
    const val SAMPLE_CHAR = "A"

    fun actionFor(key: PlaceholderKey): KeyAction = when (key) {
        PlaceholderKey.SAMPLE -> KeyAction.CommitText(SAMPLE_CHAR)
        PlaceholderKey.DISMISS -> KeyAction.HideSelf
    }
}
