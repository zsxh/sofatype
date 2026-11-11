// T54 Round 2 · 设置页 pre-IME 键路由决策（纯 Kotlin，无框架依赖，单元测试锁定）。
// 背景（docs/issues/open/T54.md）：API 34 实证输入会话为窗口级绑定——视图焦点移出编辑框
// 后，遥控键仍先路由进隐藏态 IME 被消费。宿主侧唯一合法前置通道是框架的 pre-IME 派发
// （ViewRootImpl 把键先送到根视图 dispatchKeyPreIme）：键盘显示态透传 IME（打字与键盘内
// 导航零改动），键盘隐藏态按宿主语义路由（方向键=焦点搜索、OK=焦点控件激活、BACK=退出页）。
package com.sofatype.ime

import android.view.KeyEvent
import android.view.View

object SettingsKeyRouter {

    /** 键路由决策结果：四向焦点移动 / 确认激活 / 返回退出 / 透传（交回框架与 IME 链路）。 */
    enum class Route { FOCUS_UP, FOCUS_DOWN, FOCUS_LEFT, FOCUS_RIGHT, CONFIRM, BACK, PASS }

    /** 遥控器键码 → 路由决策（OK/回车=确认键口径与 IME 侧 isConfirmKey 一致）。 */
    fun route(keyCode: Int): Route = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> Route.FOCUS_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Route.FOCUS_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Route.FOCUS_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Route.FOCUS_RIGHT
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        -> Route.CONFIRM
        KeyEvent.KEYCODE_BACK -> Route.BACK
        else -> Route.PASS
    }

    /** 路由决策 → 框架焦点搜索方向常量（FocusFinder 口径）。 */
    fun focusDirection(route: Route): Int = when (route) {
        Route.FOCUS_UP -> View.FOCUS_UP
        Route.FOCUS_DOWN -> View.FOCUS_DOWN
        Route.FOCUS_LEFT -> View.FOCUS_LEFT
        Route.FOCUS_RIGHT -> View.FOCUS_RIGHT
        else -> 0
    }

    /**
     * T59（户主拍板 2026-11-11）CONFIRM 激活决策（纯函数，单元测试锁定）：
     * 焦点在可输入控件（EditText）→ 唤起输入法（showSoftInput），与 IME 隐藏态
     * 确认键「消费+唤起」口径同语义（FR-A1「在可输入焦点处确认后弹出键盘」）；
     * 其余控件 → 激活焦点控件（performClick，T54 既有口径保留）。
     */
    fun confirmActivatesIme(focused: View?): Boolean = focused is android.widget.EditText
}
