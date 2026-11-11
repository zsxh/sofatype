// T54 Round 2 · 设置页焦点接管决策（纯 Kotlin，无框架依赖，单元测试锁定）。
// 背景（docs/issues/open/T54.md Round 1 定位）：键盘收起后视图焦点滞留测试输入框，
// 输入会话不终结，遥控键全部先路由进隐藏态 IME 被消费——设置页全键无响应。
// 本决策与调用方（SettingsActivity 全局布局监听）约定：窗口可见底部帧回到全屏高度
// （adjustResize 缩放还原）即判键盘已收起；此刻编辑框若仍持视图焦点，则须把焦点
// 移出编辑框，输入会话随焦点离开编辑框而终结，遥控键恢复宿主派发。
// 显示阈值取 1080p 下键盘面板高度的保守下限：可见底部帧低于全屏高 - 阈值判为输入法可见；
// 中间帧（缩放过渡）按可见判，避免显示过渡期误触发接管。
package com.sofatype.ime

object SettingsFocusTakeover {

    /** 输入法可见性判定阈值（px）：可见底部帧低于全屏高减该值即判输入法可见。 */
    const val IME_FRAME_THRESHOLD_PX = 80

    /** 输入法窗口是否可见（adjustResize 口径：可见底部帧显著低于全屏高即键盘占位中）。 */
    fun isImeShown(visibleFrameBottom: Int, screenHeightPx: Int): Boolean =
        visibleFrameBottom < screenHeightPx - IME_FRAME_THRESHOLD_PX

    /** 是否应接管焦点：键盘已收起且编辑框仍持视图焦点（死态成形条件）。 */
    fun shouldTakeFocus(imeShown: Boolean, editorHasFocus: Boolean): Boolean =
        !imeShown && editorHasFocus
}
