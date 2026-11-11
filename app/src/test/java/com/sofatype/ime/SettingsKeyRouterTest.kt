// T54 Round 2 · pre-IME 键路由决策守卫单测（纯函数）：
// 遥控器键码路由全表 + 焦点方向常量映射；确保分路语义与 IME 侧确认键口径一致。
package com.sofatype.ime

import android.view.KeyEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsKeyRouterTest {

    @Test
    fun `方向键路由到对应焦点方向`() {
        assertEquals(
            SettingsKeyRouter.Route.FOCUS_UP,
            SettingsKeyRouter.route(KeyEvent.KEYCODE_DPAD_UP),
        )
        assertEquals(
            SettingsKeyRouter.Route.FOCUS_DOWN,
            SettingsKeyRouter.route(KeyEvent.KEYCODE_DPAD_DOWN),
        )
        assertEquals(
            SettingsKeyRouter.Route.FOCUS_LEFT,
            SettingsKeyRouter.route(KeyEvent.KEYCODE_DPAD_LEFT),
        )
        assertEquals(
            SettingsKeyRouter.Route.FOCUS_RIGHT,
            SettingsKeyRouter.route(KeyEvent.KEYCODE_DPAD_RIGHT),
        )
    }

    @Test
    fun `确认键集合与 IME 侧口径一致`() {
        // OK / 回车 / 小键盘回车 = 确认键（FR-A11 口径，与 SofatypeImeService.isConfirmKey 同集）
        assertEquals(
            SettingsKeyRouter.Route.CONFIRM,
            SettingsKeyRouter.route(KeyEvent.KEYCODE_DPAD_CENTER),
        )
        assertEquals(SettingsKeyRouter.Route.CONFIRM, SettingsKeyRouter.route(KeyEvent.KEYCODE_ENTER))
        assertEquals(
            SettingsKeyRouter.Route.CONFIRM,
            SettingsKeyRouter.route(KeyEvent.KEYCODE_NUMPAD_ENTER),
        )
    }

    @Test
    fun `BACK 路由到返回`() {
        assertEquals(SettingsKeyRouter.Route.BACK, SettingsKeyRouter.route(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun `其余键码透传不接管`() {
        // 打字键 / 功能键不属遥控导航语义，pre-IME 阶段放行
        assertEquals(SettingsKeyRouter.Route.PASS, SettingsKeyRouter.route(KeyEvent.KEYCODE_MENU))
        assertEquals(SettingsKeyRouter.Route.PASS, SettingsKeyRouter.route(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(SettingsKeyRouter.Route.PASS, SettingsKeyRouter.route(KeyEvent.KEYCODE_A))
        assertEquals(SettingsKeyRouter.Route.PASS, SettingsKeyRouter.route(KeyEvent.KEYCODE_DEL))
    }

    @Test
    fun `路由决策映射框架焦点方向常量`() {
        assertEquals(
            View.FOCUS_UP,
            SettingsKeyRouter.focusDirection(SettingsKeyRouter.Route.FOCUS_UP),
        )
        assertEquals(
            View.FOCUS_DOWN,
            SettingsKeyRouter.focusDirection(SettingsKeyRouter.Route.FOCUS_DOWN),
        )
        assertEquals(
            View.FOCUS_LEFT,
            SettingsKeyRouter.focusDirection(SettingsKeyRouter.Route.FOCUS_LEFT),
        )
        assertEquals(
            View.FOCUS_RIGHT,
            SettingsKeyRouter.focusDirection(SettingsKeyRouter.Route.FOCUS_RIGHT),
        )
    }
}
