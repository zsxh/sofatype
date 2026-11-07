// T35 · 已配对会话登记表单元测试（决策 35：滚动 token / 自动顶替 / 断连回退的纯逻辑）：
// 锁定 attach 交出旧连接（顶替源）、同身份重复登记不算顶替、detachIfCurrent 仅清当前登记
//（被顶替旧连接的迟到关闭不清新登记）、clear 语义。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PairedSessionRegistryTest {

    private fun connection(): PairedConnection =
        PairedConnection(sender = { _ -> true }, close = { _ -> })

    @Test
    fun attachReturnsPreviousConnectionForReplacement() {
        // 新设备配对：登记新连接并交出旧连接（供 Manager 回发 replaced 后关闭，FR-B11）
        val registry = PairedSessionRegistry()
        val old = connection()
        val new = connection()
        assertNull("首次配对无被顶替连接", registry.attach("session-a", old))
        assertTrue("登记后存在已配对会话", registry.hasPairedSession)
        val replaced = registry.attach("session-b", new)
        assertSame("顶替时交出旧连接", old, replaced)
    }

    @Test
    fun reattachingSameIdentityIsNotTreatedAsReplacement() {
        // 防御：同一身份重复登记（理论不可达）只更新句柄，不视为顶替
        val registry = PairedSessionRegistry()
        val session = "session-a"
        registry.attach(session, connection())
        assertNull(registry.attach(session, connection()))
    }

    @Test
    fun detachOnlyClearsCurrentRegistration() {
        // 被顶替旧连接的迟到关闭不清新登记；当前会话断开才清除（断连回退依据）
        val registry = PairedSessionRegistry()
        val oldSession = "session-a"
        val newSession = "session-b"
        registry.attach(oldSession, connection())
        registry.attach(newSession, connection())
        assertFalse("旧连接迟到关闭不影响新登记", registry.detachIfCurrent(oldSession))
        assertTrue(registry.hasPairedSession)
        assertTrue("当前会话断开清除登记", registry.detachIfCurrent(newSession))
        assertFalse("无登记后重复清除返回 false", registry.detachIfCurrent(newSession))
        assertFalse("清除后无已配对会话", registry.hasPairedSession)
    }

    @Test
    fun detachWithoutAnyRegistrationReturnsFalse() {
        val registry = PairedSessionRegistry()
        assertFalse(registry.detachIfCurrent("session-x"))
    }

    @Test
    fun clearReportsWhetherSessionExisted() {
        // 服务停止清空登记：返回清空前是否存在已配对会话（决定是否需要 UI 通知）
        val registry = PairedSessionRegistry()
        assertFalse(registry.clear())
        registry.attach("session-a", connection())
        assertTrue(registry.clear())
        assertFalse("清空后无已配对会话", registry.hasPairedSession)
    }
}
