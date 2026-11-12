// T54 Round 2 · 设置页焦点接管决策（纯 Kotlin，无框架依赖，单元测试锁定）。
// T54 缺陷修复 Round 1 修订接管语义（2026-11-12，docs/issues/open/T54.md 真机 FAIL 项）：
// tap 设置页按钮使焦点离开编辑框、IMM 输入会话解绑（API 23 视图级绑定，设备档案坑 23）；
// 会话解绑后键码不再经宿主窗口 pre-IME 分路（真机在案事实），直达 IME 隐藏态分支被消费，
// 并被「键盘自发重显」劫持——T54 四口径（DPAD 宿主移焦 / OK 激活焦点控件）真机不可达。
// 修复 = 键盘收起沿把焦点移回编辑框：输入会话保持绑定，键码持续经宿主 pre-IME 分路
// 按宿主语义路由（T54 四口径成立），IME 侧 T59 拍板口径零改动。
// 与调用方（SettingsActivity 全局布局监听）约定：窗口可见底部帧回到全屏高度
// （adjustResize 缩放还原）即判键盘已收起；此刻编辑框若不持视图焦点（tap 过按钮），
// 则在收起沿把焦点移回编辑框。仅在可见性跳变沿执行，防后续布局（状态行变化等）抢走
// 用户已移到按钮上的焦点。
// 显示阈值取 1080p 下键盘面板高度的保守下限：可见底部帧低于全屏高 - 阈值判为输入法可见；
// 中间帧（缩放过渡）按可见判，避免显示过渡期误触发接管。
package com.sofatype.ime

object SettingsFocusTakeover {

    /** 输入法可见性判定阈值（px）：可见底部帧低于全屏高减该值即判输入法可见。 */
    const val IME_FRAME_THRESHOLD_PX = 80

    /** 输入法窗口是否可见（adjustResize 口径：可见底部帧显著低于全屏高即键盘占位中）。 */
    fun isImeShown(visibleFrameBottom: Int, screenHeightPx: Int): Boolean =
        visibleFrameBottom < screenHeightPx - IME_FRAME_THRESHOLD_PX

    /**
     * 是否应接管焦点（T54 缺陷修复 Round 1 修订）：键盘已收起且编辑框不持视图焦点
     * （tap 过按钮使焦点离开编辑框）→ 在收起沿把焦点移回编辑框，保持输入会话绑定
     * （pre-IME 分路通道保活）。键盘显示中不接管；编辑框已持焦点时无操作。
     */
    fun shouldTakeFocus(imeShown: Boolean, editorHasFocus: Boolean): Boolean =
        !imeShown && !editorHasFocus
}
