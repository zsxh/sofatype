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

        val registry = PairedSessionRegistry()
        val session = "session-a"
        registry.attach(session, connection())
        assertNull(registry.attach(session, connection()))
    }

    @Test
    fun detachOnlyClearsCurrentRegistration() {

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

        val registry = PairedSessionRegistry()
        assertFalse(registry.clear())
        registry.attach("session-a", connection())
        assertTrue(registry.clear())
        assertFalse("清空后无已配对会话", registry.hasPairedSession)
    }

    @Test
    fun currentConnectionReturnsLatestRegistrationHandle() {

        val registry = PairedSessionRegistry()
        assertNull("无登记时返回 null", registry.currentConnection())
        val handle = connection()
        registry.attach("session-a", handle)
        assertSame(handle, registry.currentConnection())
        val replacedHandle = connection()
        registry.attach("session-b", replacedHandle)
        assertSame("顶替后返回新连接句柄", replacedHandle, registry.currentConnection())
        registry.detachIfCurrent("session-b")
        assertNull("断连清除后返回 null", registry.currentConnection())
    }
}
