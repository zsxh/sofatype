// T82 · 决策 66 · B2 设置页检测引导（修复段 Round 1）：默认输入法夺回后恢复通道的
// 纯判定模型。真机诊断（docs/issues/open/T82.md 改动①）定性夺回链 = 平台对持系统窗口的
// IME 包 force-stop × AOSP 原生重选系统默认输入法，触发沿 = 自家 Activity 切走沿 + 空闲屏保沿；
// 应用侧不夺回、只在设置页 onResume 检测 default_input_method（Settings.Secure，
// 公开读取零权限）≠ 本 IME 时给出可见提示，并由「一键选回」按钮调
// InputMethodManager.showInputMethodPicker()（公开 API 零权限）打开系统选择器。
// 本类只承载判定，不依赖 Android 框架（单测锁定）；读值与提示显隐接线在 ImeTakeoverPrompt。
package com.sofatype.ime

/**
 * 默认输入法夺回检测判定（T82 · 决策 66）。
 *
 * 本 IME 的 flattened ComponentName（「包名/类名」，类名同包时简写为「.类名」），
 * 与 AndroidManifest 声明的 `.SofatypeImeService` 及 `ime set` / `settings get secure
 * default_input_method` 的取值口径一致。
 */
object ImeTakeoverCheck {

    const val OWN_IME_ID = "com.sofatype.ime/.SofatypeImeService"

    /**
     * 是否处于夺回态（应显示提示）：default_input_method 当前读值 ≠ 本 IME 即夺回态。
     * 读值为 null（设置项未落）或空串同样判夺回——此时系统默认也不是本 IME；
     * 精确全串比较，不做大小写或空白归一（与 `ime set` 写入值口径一致）。
     * 默认态（= 本 IME）返回 false：页面零新增提示、零行为变化（决策 66 默认态口径）。
     */
    fun needsPrompt(currentDefaultIme: String?): Boolean = currentDefaultIme != OWN_IME_ID
}
