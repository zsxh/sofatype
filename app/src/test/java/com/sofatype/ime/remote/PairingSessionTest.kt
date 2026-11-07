// T14 · hello/state 配对会话单元测试（PRD 第 7 章、FR-B10）：
// 有效 token → state connected:true 且 token 失效；重放 / 错误 / 缺失 token → state connected:false + 4001；
// 非 hello 消息忽略；state 载荷转义正确。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingSessionTest {

    private fun newSession(
        store: PairingTokenStore = PairingTokenStore(),
        tvName: String = "测试电视",
        charLimit: Int = 200,
        log: (String) -> Unit = {},
    ) = PairingSession(store, tvName, charLimit, log)

    private fun helloJson(token: String?): String =
        if (token == null) """{"type":"hello","deviceName":"browser"}"""
        else """{"type":"hello","token":"$token","deviceName":"browser"}"""

    @Test
    fun validHelloReturnsConnectedStateWithTvNameAndCharLimit() {
        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store, tvName = "客厅电视", charLimit = 200)
        val reply = session.onTextMessage(helloJson(token))
        assertTrue(reply is WsReply.Text)
        val body = (reply as WsReply.Text).text
        assertTrue(body.contains(""""type":"state""""))
        assertTrue(body.contains(""""connected":true"""))
        assertTrue(body.contains(""""tvName":"客厅电视""""))
        assertTrue(body.contains(""""charLimit":200"""))
    }

    @Test
    fun successfulPairingConsumesTokenImmediately() {
        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store)
        session.onTextMessage(helloJson(token))
        assertFalse("配对成功后 token 立即失效（FR-B10）", store.isActive)
        assertNull(store.peek())
    }

    @Test
    fun replayedTokenIsRejectedWithClose4001() {
        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store)
        session.onTextMessage(helloJson(token))
        val replay = session.onTextMessage(helloJson(token))
        assertTrue(replay is WsReply.TextThenClose)
        replay as WsReply.TextThenClose
        assertEquals(PairingSession.CLOSE_POLICY_REJECTED, replay.closeCode)
        assertTrue(replay.text.contains(""""connected":false"""))
    }

    @Test
    fun wrongTokenIsRejectedWithoutConsumingValidToken() {
        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store)
        val reply = session.onTextMessage(helloJson("wrong-guess"))
        assertTrue(reply is WsReply.TextThenClose)
        assertEquals(4001, (reply as WsReply.TextThenClose).closeCode)
        assertTrue("正确 token 未被误吊销", store.isActive)
        assertEquals(token, store.peek())
    }

    @Test
    fun missingTokenIsRejected() {
        val store = PairingTokenStore()
        store.issue()
        val session = newSession(store)
        val reply = session.onTextMessage(helloJson(null))
        assertTrue(reply is WsReply.TextThenClose)
        assertEquals(4001, (reply as WsReply.TextThenClose).closeCode)
    }

    @Test
    fun emptyTokenIsRejected() {
        val store = PairingTokenStore()
        store.issue()
        val session = newSession(store)
        val reply = session.onTextMessage(helloJson(""))
        assertTrue(reply is WsReply.TextThenClose)
    }

    @Test
    fun malformedJsonIsRejected() {
        val store = PairingTokenStore()
        store.issue()
        val session = newSession(store)
        for (bad in listOf("not json", "[1,2]", """{"type":}""")) {
            val reply = session.onTextMessage(bad)
            assertTrue("应拒绝：$bad", reply is WsReply.TextThenClose)
            assertEquals(4001, (reply as WsReply.TextThenClose).closeCode)
        }
    }

    @Test
    fun nonHelloMessagesAreIgnoredAndConnectionStaysOpen() {
        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store)
        for (text in listOf(
            """{"type":"input","text":"hi"}""",
            """{"deviceName":"browser"}""",
            """{"type":"commit"}""",
        )) {
            assertEquals(WsReply.None, session.onTextMessage(text))
        }
        assertTrue("忽略路径不消费 token", store.isActive)
        assertEquals(token, store.peek())
    }

    @Test
    fun statePayloadEscapesTvNameRoundTrippable() {
        val store = PairingTokenStore()
        store.issue()
        val session = newSession(store, tvName = "带\"引号\"与\\反斜杠的电视")
        val reply = session.onTextMessage(helloJson(store.peek()))
        assertTrue(reply is WsReply.Text)
        val fields = MinimalJson.parseTopLevelObject((reply as WsReply.Text).text)
        assertEquals("带\"引号\"与\\反斜杠的电视", fields["tvName"])
    }

    @Test
    fun eventsDoNotContainTokenOrDeviceName() {
        // 事件日志注入：只记事件，不包含 token 或 deviceName 内容
        val store = PairingTokenStore()
        val token = store.issue()
        val events = mutableListOf<String>()
        val session = newSession(store, log = { events.add(it) })
        session.onTextMessage(helloJson(token))
        assertTrue(events.isNotEmpty())
        assertTrue(events.none { it.contains(token) })
        assertTrue(events.none { it.contains("browser") })
    }
}
