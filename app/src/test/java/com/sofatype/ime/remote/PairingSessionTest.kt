// T14 · hello/state 配对会话单元测试（PRD 第 7 章、FR-B10）：
// 有效 token → state connected:true 且 token 失效；重放 / 错误 / 缺失 token → state connected:false + 4001；
// 非 hello 消息忽略；state 载荷转义正确。
// T15 · input / clear 业务分发；T16 · commit 分发、码点兜底截断、committed 回执（ack → sender）。
// T18 · state 增配对地址 url（FR-B8 刷新重连，lead 拍板 A：仅 connected:true 携带，被拒不带）+
// disconnect 断连通知分发（记事件、连接保持，PRD 第 7 章 disconnect 双向）。
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
        onInput: (String) -> Unit = {},
        onClear: () -> Unit = {},
        onCommit: (text: String, ack: (Boolean) -> Unit) -> Unit = { _, _ -> },
        onPaired: (deviceName: String?) -> Unit = {},
        onPairedSessionClosed: () -> Unit = {},
        onConnectionAttached: (WsMessageSender, (Int) -> Unit) -> Unit = { _, _ -> },
        pairingUrl: () -> String? = { null },
    ) = PairingSession(
        store,
        tvName,
        charLimit,
        log,
        onInput,
        onClear,
        onCommit,
        onPaired,
        onPairedSessionClosed,
        onConnectionAttached,
        pairingUrl,
    )

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

    // ---- T15 · 配对后业务消息分发（input / clear，PRD 第 7 章） ----

    @Test
    fun pairedConnectionDispatchesInputToCallback() {
        val store = PairingTokenStore()
        val token = store.issue()
        val received = mutableListOf<String>()
        val session = newSession(store, onInput = { received.add(it) })
        session.onTextMessage(helloJson(token))
        val reply = session.onTextMessage("""{"type":"input","text":"客厅"}""")
        assertEquals(WsReply.None, reply)
        assertEquals(listOf("客厅"), received)
    }

    @Test
    fun pairedConnectionDispatchesClearToCallback() {
        val store = PairingTokenStore()
        val token = store.issue()
        var cleared = 0
        val session = newSession(store, onClear = { cleared++ })
        session.onTextMessage(helloJson(token))
        assertEquals(WsReply.None, session.onTextMessage("""{"type":"clear"}"""))
        assertEquals(1, cleared)
    }

    @Test
    fun unpairedConnectionInputIsNotDispatched() {
        // 防未配对连接注入内容：input/clear 在 hello 成功前一律忽略且不回调
        val store = PairingTokenStore()
        store.issue()
        var callbackCount = 0
        val session = newSession(store, onInput = { callbackCount++ }, onClear = { callbackCount++ })
        assertEquals(WsReply.None, session.onTextMessage("""{"type":"input","text":"早到"}"""))
        assertEquals(WsReply.None, session.onTextMessage("""{"type":"clear"}"""))
        assertEquals(0, callbackCount)
        // 连接保持：随后仍可完成配对
        val token = store.peek()
        assertTrue(session.onTextMessage(helloJson(token!!)) is WsReply.Text)
    }

    @Test
    fun pairedConnectionIgnoresMalformedBusinessMessagesWithoutClosing() {
        val store = PairingTokenStore()
        val token = store.issue()
        var callbackCount = 0
        val events = mutableListOf<String>()
        val session = newSession(
            store,
            log = { events.add(it) },
            onInput = { callbackCount++ },
            onClear = { callbackCount++ },
        )
        session.onTextMessage(helloJson(token))
        // 非法 JSON / 未知类型 / 字段缺失：忽略、连接保持、不回调
        for (bad in listOf("not json", """{"type":"commit"}""", """{"type":"input"}""")) {
            assertEquals(WsReply.None, session.onTextMessage(bad))
        }
        assertEquals(0, callbackCount)
        // 连接仍可承载业务消息
        session.onTextMessage("""{"type":"input","text":"仍在线"}""")
        assertEquals(1, callbackCount)
    }

    @Test
    fun pairedConnectionReplayedHelloIsRejectedWithClose4001() {
        // T14 基线口径：token 一次性，配对后同连接重放 hello → state connected:false + 4001
        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store)
        session.onTextMessage(helloJson(token))
        val replay = session.onTextMessage(helloJson(token))
        assertTrue(replay is WsReply.TextThenClose)
        assertEquals(PairingSession.CLOSE_POLICY_REJECTED, (replay as WsReply.TextThenClose).closeCode)
        assertTrue(replay.text.contains(""""connected":false"""))
    }

    @Test
    fun helloTokenMustBeJsonString() {
        // token 为数字 / null 字面量：类型不符，按缺失拒绝（4001），不消费正确 token
        val store = PairingTokenStore()
        store.issue()
        val session = newSession(store)
        val reply = session.onTextMessage("""{"type":"hello","token":123,"deviceName":"browser"}""")
        assertTrue(reply is WsReply.TextThenClose)
        assertEquals(PairingSession.CLOSE_POLICY_REJECTED, (reply as WsReply.TextThenClose).closeCode)
        assertTrue("正确 token 未被误消费", store.isActive)
    }

    @Test
    fun businessMessagesNeverAppearInEventLog() {
        // 隐私红线：input / commit 文本属用户输入内容，事件日志不得包含
        val store = PairingTokenStore()
        val token = store.issue()
        val events = mutableListOf<String>()
        val session = newSession(store, log = { events.add(it) })
        session.onTextMessage(helloJson(token))
        session.onTextMessage("""{"type":"input","text":"绝密内容XYZ"}""")
        session.onTextMessage("""{"type":"commit","text":"绝密上屏ABC"}""")
        session.onTextMessage("""{"type":"clear"}""")
        assertTrue(events.none { it.contains("绝密内容XYZ") })
        assertTrue(events.none { it.contains("绝密上屏ABC") })
    }

    // ---- T16 · commit 上屏指令分发与 committed 回执（FR-B6/C6 · 决策 9/10） ----

    @Test
    fun pairedConnectionDispatchesCommitToCallback() {
        val store = PairingTokenStore()
        val token = store.issue()
        val received = mutableListOf<String>()
        val session = newSession(store, onCommit = { text, _ -> received.add(text) })
        session.onTextMessage(helloJson(token))
        val reply = session.onTextMessage("""{"type":"commit","text":"客厅"}""")
        // 异步处理口径：指令消息本身不回发（回执由 ack 触发），连接保持
        assertEquals(WsReply.None, reply)
        assertEquals(listOf("客厅"), received)
    }

    @Test
    fun unpairedConnectionCommitIsNotDispatched() {
        // 防未配对连接触发上屏：commit 在 hello 成功前一律忽略且不回调
        val store = PairingTokenStore()
        store.issue()
        var callbackCount = 0
        val session = newSession(store, onCommit = { _, _ -> callbackCount++ })
        assertEquals(WsReply.None, session.onTextMessage("""{"type":"commit","text":"早到的上屏"}"""))
        assertEquals(0, callbackCount)
        // 连接保持：随后仍可完成配对并正常分发
        val token = store.peek()
        assertTrue(session.onTextMessage(helloJson(token!!)) is WsReply.Text)
        session.onTextMessage("""{"type":"commit","text":"配对后合法"}""")
        assertEquals(1, callbackCount)
    }

    @Test
    fun commitPayloadIsClampedToCharLimit() {
        // 超限载荷按码点兜底截断（与显示缓冲同口径），不把超限文本直写宿主
        val store = PairingTokenStore()
        val token = store.issue()
        val received = mutableListOf<String>()
        val session = newSession(store, charLimit = 5, onCommit = { text, _ -> received.add(text) })
        session.onTextMessage(helloJson(token))
        session.onTextMessage("""{"type":"commit","text":"一二三四五六七八九十"}""")
        assertEquals(listOf("一二三四五"), received)
    }

    /** 记录 send 调用的假回发器（committed 回执断言用）。 */
    private class RecordingSender : WsMessageSender {
        val sent = mutableListOf<String>()
        override fun send(text: String): Boolean {
            sent.add(text)
            return true
        }
    }

    @Test
    fun commitAckTrueSendsCommittedOkTrue() {
        val store = PairingTokenStore()
        val token = store.issue()
        val sender = RecordingSender()
        val session = newSession(
            store,
            onCommit = { _, ack -> ack(true) },
        )
        session.attachConnection(sender) { _ -> }
        session.onTextMessage(helloJson(token))
        session.onTextMessage("""{"type":"commit","text":"客厅"}""")
        assertEquals(1, sender.sent.size)
        assertTrue(sender.sent[0].contains(""""type":"committed""""))
        assertTrue(sender.sent[0].contains(""""ok":true"""))
    }

    @Test
    fun commitAckFalseSendsCommittedOkFalse() {
        // 上屏写入失败（如无焦点输入框）：回执 ok:false，手机端提示失败、草稿保留
        val store = PairingTokenStore()
        val token = store.issue()
        val sender = RecordingSender()
        val session = newSession(
            store,
            onCommit = { _, ack -> ack(false) },
        )
        session.attachConnection(sender) { _ -> }
        session.onTextMessage(helloJson(token))
        session.onTextMessage("""{"type":"commit","text":"客厅"}""")
        assertEquals(1, sender.sent.size)
        assertTrue(sender.sent[0].contains(""""ok":false"""))
    }

    @Test
    fun commitAckWithoutSenderDoesNotThrow() {
        // 回发器未注入（WS 升级前 / 共享委托场景）：ack 静默丢弃，不抛异常、不影响处理方
        val store = PairingTokenStore()
        val token = store.issue()
        var handled = false
        val session = newSession(
            store,
            onCommit = { _, ack ->
                ack(true)
                handled = true
            },
        )
        session.onTextMessage(helloJson(token))
        session.onTextMessage("""{"type":"commit","text":"客厅"}""")
        assertTrue(handled)
    }

    @Test
    fun commitAckFailureReturnIsIgnoredBySession() {
        // 连接已断（send 返回 false）：ack 路径不抛异常，会话状态不受影响
        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store, onCommit = { _, ack -> ack(true) })
        session.attachConnection({ _ -> false }) { _ -> }
        session.onTextMessage(helloJson(token))
        val reply = session.onTextMessage("""{"type":"commit","text":"客厅"}""")
        assertEquals(WsReply.None, reply)
    }

    // ---- T17 · 配对成功回调（FR-B9 / 决策 34：deviceName 上送供状态行展示） ----

    @Test
    fun pairedCallbackInvokedWithDeviceNameOnSuccessfulHello() {
        val store = PairingTokenStore()
        val token = store.issue()
        val names = mutableListOf<String?>()
        val session = newSession(store, onPaired = { names.add(it) })
        session.onTextMessage(helloJson(token))
        assertEquals(listOf<String?>("browser"), names)
    }

    @Test
    fun pairedCallbackNotInvokedOnRejectedHello() {
        // 被拒（缺失 / 错误 / 重放）不触发配对成功回调
        val store = PairingTokenStore()
        val token = store.issue()
        var callbackCount = 0
        val session = newSession(store, onPaired = { callbackCount++ })
        session.onTextMessage(helloJson(null)) // 缺失 token
        session.onTextMessage(helloJson("wrong-guess")) // 错误 token
        session.onTextMessage(helloJson(token))
        session.onTextMessage(helloJson(token)) // 重放已消费 token
        assertEquals(1, callbackCount) // 仅第三次（合法首次）触发
    }

    @Test
    fun pairedCallbackReceivesNullWhenHelloLacksDeviceName() {
        val store = PairingTokenStore()
        val token = store.issue()
        val names = mutableListOf<String?>()
        val session = newSession(store, onPaired = { names.add(it) })
        session.onTextMessage("""{"type":"hello","token":"$token"}""")
        assertEquals(listOf<String?>(null), names)
    }

    // ---- T35 · 连接控制与断连回调（决策 35：滚动 token / 自动顶替 / 断连回退） ----

    @Test
    fun connectionAttachedCallbackReceivesSenderAndCloseHandle() {
        // WS 升级完成后 attachConnection 触发控制句柄上抛（Manager 登记供顶替编排）
        val store = PairingTokenStore()
        var attachedSender: WsMessageSender? = null
        var closeCode = 0
        val session = newSession(
            store,
            onConnectionAttached = { sender, close -> attachedSender = sender; close(PairingSession.CLOSE_REPLACED) },
        )
        session.attachConnection({ _ -> true }) { code -> closeCode = code }
        assertTrue(attachedSender != null)
        assertEquals(PairingSession.CLOSE_REPLACED, closeCode)
    }

    @Test
    fun sessionClosedCallbackFiresOnlyAfterPaired() {
        // 未配对连接的会话结束不触发（不回退等待态）；配对成功后的断开才触发（T35 决策 35）。
        // 服务契约：onSessionClosed 由服务在会话结束时调用一次（finally），无重复通知路径。
        val store = PairingTokenStore()
        val token = store.issue()
        var closedCount = 0
        val session = newSession(store, onPairedSessionClosed = { closedCount++ })
        session.onTextMessage(helloJson("wrong-guess")) // 未配对即被拒
        session.onSessionClosed()
        assertEquals("被拒连接断开不触发已配对关闭回调", 0, closedCount)
        session.onTextMessage(helloJson(token))
        session.onSessionClosed()
        assertEquals("已配对连接断开触发一次", 1, closedCount)
    }

    // ---- T18 · state 增配对地址 url（FR-B8 刷新重连，lead 拍板 A）+ disconnect 断连通知分发 ----

    @Test
    fun connectedStateCarriesRollingPairingUrl() {
        // hello 校验通过 → state 附当前配对地址 url（Manager 在 onPaired 内滚动签发后提供，此处以固定值模拟）：
        // 手机页据此 history.replaceState 更新地址栏，刷新页面即持有效新码重新配对（FR-B8）
        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store, pairingUrl = { "http://10.0.2.15:40000/?token=rolled" })
        val reply = session.onTextMessage(helloJson(token))
        val body = (reply as WsReply.Text).text
        assertTrue(body, body.contains(""""url":"http://10.0.2.15:40000/?token=rolled""""))
    }

    @Test
    fun rejectedStateDoesNotCarryPairingUrl() {
        // FR-B10 防套取：被拒连接未通过配对校验，不得获得有效配对地址
        val store = PairingTokenStore()
        store.issue()
        val session = newSession(store, pairingUrl = { "http://10.0.2.15:40000/?token=rolled" })
        val reply = session.onTextMessage(helloJson("wrong-guess"))
        val body = (reply as WsReply.TextThenClose).text
        assertFalse(body, body.contains(""""url""""))
    }

    @Test
    fun stateOmitsUrlFieldWhenProviderReturnsNull() {
        // 未提供 url（缺省 / 防御分支）：state 不带该字段，既有测试与占位页基线不受影响
        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store)
        val reply = session.onTextMessage(helloJson(token))
        val body = (reply as WsReply.Text).text
        assertFalse(body, body.contains(""""url""""))
    }

    @Test
    fun connectedStateEscapesPairingUrl() {
        // url 含需转义字符（引号 / &）时按 JSON 转义下发，解析回环一致
        val store = PairingTokenStore()
        val token = store.issue()
        val rawUrl = """http://10.0.2.15:40000/?a="x"&b=y"""
        val session = newSession(store, pairingUrl = { rawUrl })
        val reply = session.onTextMessage(helloJson(token))
        val fields = MinimalJson.parseTopLevelObject((reply as WsReply.Text).text)
        assertEquals(rawUrl, fields["url"])
    }

    @Test
    fun pairedConnectionDispatchesDisconnectAndKeepsSession() {
        // 手机端页面关闭 / 刷新前发 disconnect（PRD 第 7 章 disconnect 双向）：
        // 记事件、不回发、连接保持（后续业务消息仍分发；实际断开由 TCP 关闭驱动）
        val store = PairingTokenStore()
        val token = store.issue()
        val events = mutableListOf<String>()
        val received = mutableListOf<String>()
        val session = newSession(store, log = { events.add(it) }, onInput = { received.add(it) })
        session.onTextMessage(helloJson(token))
        assertEquals(WsReply.None, session.onTextMessage("""{"type":"disconnect","reason":"page-unload"}"""))
        session.onTextMessage("""{"type":"input","text":"通知后仍在线"}""")
        assertEquals(listOf("通知后仍在线"), received)
        assertTrue(events.any { it.contains("disconnect") })
    }

    @Test
    fun unpairedConnectionDisconnectIsSilentlyIgnored() {
        // 未配对连接的 disconnect 不触发事件、连接保持（仍可完成配对）
        val store = PairingTokenStore()
        store.issue()
        val events = mutableListOf<String>()
        val session = newSession(store, log = { events.add(it) })
        assertEquals(WsReply.None, session.onTextMessage("""{"type":"disconnect","reason":"page-unload"}"""))
        assertFalse(events.any { it.contains("disconnect") })
        val token = store.peek()
        assertTrue(session.onTextMessage(helloJson(token!!)) is WsReply.Text)
    }
}
