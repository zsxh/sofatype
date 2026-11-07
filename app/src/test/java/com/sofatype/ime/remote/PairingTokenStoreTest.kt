// T14 · 一次性配对 token 单元测试（FR-B10）：签发格式、一次性消费、错误 token 不影响有效值、
// 重签发作废旧值、清空生命周期。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingTokenStoreTest {

    private fun newStore() = PairingTokenStore()

    @Test
    fun issueReturnsUrlSafeTokenWithoutPadding() {
        val store = newStore()
        val token = store.issue()
        assertEquals("16 字节 → Base64URL 22 字符", 22, token.length)
        assertTrue("仅 URL 安全字母表", token.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_" })
        assertFalse("不含 padding", token.contains('='))
        assertTrue(store.isActive)
        assertEquals(token, store.peek())
    }

    @Test
    fun consumeValidTokenSucceedsOnceThenInvalid() {
        val store = newStore()
        val token = store.issue()
        assertTrue("首次消费成功", store.consume(token))
        assertFalse("token 已失效", store.isActive)
        assertNull(store.peek())
        assertFalse("同 token 二次消费被拒", store.consume(token))
    }

    @Test
    fun consumeWrongTokenRejectedAndDoesNotRevokeValidToken() {
        val store = newStore()
        val token = store.issue()
        assertFalse(store.consume("wrong-token-guess"))
        assertFalse(store.consume(""))
        assertFalse(store.consume(null))
        assertTrue("猜错不吊销正确 token", store.isActive)
        assertEquals(token, store.peek())
        assertTrue("正确 token 仍可消费", store.consume(token))
    }

    @Test
    fun issueReplacesPreviousTokenAndOldValueIsRejected() {
        val store = newStore()
        val first = store.issue()
        val second = store.issue()
        assertNotEquals("每次签发新随机值", first, second)
        assertFalse("旧 token 已作废", store.consume(first))
        assertTrue("新 token 有效", store.consume(second))
    }

    @Test
    fun tokensAreRandomAcrossInstances() {
        val first = newStore().issue()
        val second = newStore().issue()
        assertNotEquals(first, second)
    }

    @Test
    fun clearInvalidatesCurrentToken() {
        val store = newStore()
        store.issue()
        store.clear()
        assertFalse(store.isActive)
        assertNull(store.peek())
        assertFalse(store.consume(store.peek()))
    }

    @Test
    fun consumeBeforeAnyIssueIsRejected() {
        val store = newStore()
        assertFalse(store.consume("anything"))
        assertFalse(store.isActive)
    }
}
