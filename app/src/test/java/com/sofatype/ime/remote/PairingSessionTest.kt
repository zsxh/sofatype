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
        onPaired: (deviceName: String?, deviceType: DeviceType, deviceKey: String?) -> Unit = { _, _, _ -> },
        onPairedSessionClosed: () -> Unit = {},
        onConnectionAttached: (WsMessageSender, (Int) -> Unit) -> Unit = { _, _ -> },
        pairingUrl: () -> String? = { null },
        clientCode: () -> String? = { null },
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
        clientCode,
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

        val store = PairingTokenStore()
        val token = store.issue()
        val events = mutableListOf<String>()
        val session = newSession(store, log = { events.add(it) })
        session.onTextMessage(helloJson(token))
        assertTrue(events.isNotEmpty())
        assertTrue(events.none { it.contains(token) })
        assertTrue(events.none { it.contains("browser") })
    }

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

        val store = PairingTokenStore()
        store.issue()
        var callbackCount = 0
        val session = newSession(store, onInput = { callbackCount++ }, onClear = { callbackCount++ })
        assertEquals(WsReply.None, session.onTextMessage("""{"type":"input","text":"早到"}"""))
        assertEquals(WsReply.None, session.onTextMessage("""{"type":"clear"}"""))
        assertEquals(0, callbackCount)

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

        for (bad in listOf("not json", """{"type":"commit"}""", """{"type":"input"}""")) {
            assertEquals(WsReply.None, session.onTextMessage(bad))
        }
        assertEquals(0, callbackCount)

        session.onTextMessage("""{"type":"input","text":"仍在线"}""")
        assertEquals(1, callbackCount)
    }

    @Test
    fun pairedConnectionReplayedHelloIsRejectedWithClose4001() {

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

    @Test
    fun pairedConnectionDispatchesCommitToCallback() {
        val store = PairingTokenStore()
        val token = store.issue()
        val received = mutableListOf<String>()
        val session = newSession(store, onCommit = { text, _ -> received.add(text) })
        session.onTextMessage(helloJson(token))
        val reply = session.onTextMessage("""{"type":"commit","text":"客厅"}""")

        assertEquals(WsReply.None, reply)
        assertEquals(listOf("客厅"), received)
    }

    @Test
    fun unpairedConnectionCommitIsNotDispatched() {

        val store = PairingTokenStore()
        store.issue()
        var callbackCount = 0
        val session = newSession(store, onCommit = { _, _ -> callbackCount++ })
        assertEquals(WsReply.None, session.onTextMessage("""{"type":"commit","text":"早到的上屏"}"""))
        assertEquals(0, callbackCount)

        val token = store.peek()
        assertTrue(session.onTextMessage(helloJson(token!!)) is WsReply.Text)
        session.onTextMessage("""{"type":"commit","text":"配对后合法"}""")
        assertEquals(1, callbackCount)
    }

    @Test
    fun commitPayloadIsClampedToCharLimit() {

        val store = PairingTokenStore()
        val token = store.issue()
        val received = mutableListOf<String>()
        val session = newSession(store, charLimit = 5, onCommit = { text, _ -> received.add(text) })
        session.onTextMessage(helloJson(token))
        session.onTextMessage("""{"type":"commit","text":"一二三四五六七八九十"}""")
        assertEquals(listOf("一二三四五"), received)
    }

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

        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store, onCommit = { _, ack -> ack(true) })
        session.attachConnection({ _ -> false }) { _ -> }
        session.onTextMessage(helloJson(token))
        val reply = session.onTextMessage("""{"type":"commit","text":"客厅"}""")
        assertEquals(WsReply.None, reply)
    }

    @Test
    fun pairedCallbackInvokedWithDeviceNameOnSuccessfulHello() {
        val store = PairingTokenStore()
        val token = store.issue()
        val names = mutableListOf<String?>()
        val session = newSession(store, onPaired = { name, _, _ -> names.add(name) })
        session.onTextMessage(helloJson(token))
        assertEquals(listOf<String?>("browser"), names)
    }

    @Test
    fun pairedCallbackNotInvokedOnRejectedHello() {

        val store = PairingTokenStore()
        val token = store.issue()
        var callbackCount = 0
        val session = newSession(store, onPaired = { _, _, _ -> callbackCount++ })
        session.onTextMessage(helloJson(null))
        session.onTextMessage(helloJson("wrong-guess"))
        session.onTextMessage(helloJson(token))
        session.onTextMessage(helloJson(token))
        assertEquals(1, callbackCount)
    }

    @Test
    fun pairedCallbackReceivesNullWhenHelloLacksDeviceName() {
        val store = PairingTokenStore()
        val token = store.issue()
        val names = mutableListOf<String?>()
        val session = newSession(store, onPaired = { name, _, _ -> names.add(name) })
        session.onTextMessage("""{"type":"hello","token":"$token"}""")
        assertEquals(listOf<String?>(null), names)
    }

    @Test
    fun pairedCallbackCarriesDeviceTypeAndDeviceKey() {

        val store = PairingTokenStore()
        val paired = mutableListOf<Triple<String?, DeviceType, String?>>()
        val session = newSession(
            store,
            onPaired = { name, type, key -> paired.add(Triple(name, type, key)) },
        )
        session.onTextMessage(
            """{"type":"hello","token":"${store.issue()}","deviceName":"微信内置","deviceType":"wechat","deviceKey":"0f52-uuid-a"}""",
        )
        assertEquals(1, paired.size)
        assertEquals("微信内置", paired[0].first)
        assertEquals(DeviceType.WECHAT, paired[0].second)
        assertEquals("0f52-uuid-a", paired[0].third)
    }

    @Test
    fun missingOrUnknownDeviceTypeFallsBackToBrowser() {

        val store = PairingTokenStore()
        val types = mutableListOf<DeviceType>()
        var session = newSession(store, onPaired = { _, type, _ -> types.add(type) })
        session.onTextMessage("""{"type":"hello","token":"${store.issue()}"}""")
        session = newSession(store, onPaired = { _, type, _ -> types.add(type) })
        session.onTextMessage("""{"type":"hello","token":"${store.issue()}","deviceType":"robot"}""")
        assertEquals(listOf(DeviceType.BROWSER, DeviceType.BROWSER), types)
    }

    @Test
    fun deviceTypeMustBeJsonStringFallsBackToBrowser() {

        val store = PairingTokenStore()
        val token = store.issue()
        val types = mutableListOf<DeviceType>()
        val session = newSession(store, onPaired = { _, type, _ -> types.add(type) })
        val reply = session.onTextMessage("""{"type":"hello","token":"$token","deviceType":7}""")
        assertTrue(reply is WsReply.Text)
        assertEquals(listOf(DeviceType.BROWSER), types)
    }

    @Test
    fun blankOrOversizedDeviceKeyTreatedAsAnonymous() {

        val store = PairingTokenStore()
        val oversized = "x".repeat(129)
        val paired = mutableListOf<Pair<DeviceType, String?>>()
        var session = newSession(store, onPaired = { _, t, k -> paired.add(t to k) })
        session.onTextMessage("""{"type":"hello","token":"${store.issue()}","deviceType":"apple","deviceKey":"  "}""")
        session = newSession(store, onPaired = { _, t, k -> paired.add(t to k) })
        session.onTextMessage("""{"type":"hello","token":"${store.issue()}","deviceType":"apple","deviceKey":"$oversized"}""")
        session = newSession(store, onPaired = { _, t, k -> paired.add(t to k) })
        session.onTextMessage("""{"type":"hello","token":"${store.issue()}","deviceType":"apple"}""")
        assertEquals(3, paired.size)
        assertTrue(paired.all { it.second == null })
        assertTrue(paired.all { it.first == DeviceType.APPLE })
    }

    @Test
    fun deviceKeyWithinLimitIsAcceptedVerbatim() {

        val store = PairingTokenStore()
        val key = "y".repeat(128)
        var received: String? = null
        val session = newSession(store, onPaired = { _, _, k -> received = k })
        session.onTextMessage("""{"type":"hello","token":"${store.issue()}","deviceKey":"$key"}""")
        assertEquals(key, received)
    }

    @Test
    fun connectedStateCarriesClientCode() {

        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store, clientCode = { "微信设备 01" })
        val reply = session.onTextMessage(helloJson(token))
        val body = (reply as WsReply.Text).text
        assertTrue(body, body.contains(""""clientCode":"微信设备 01""""))
        val fields = MinimalJson.parseTopLevelObject(body)
        assertEquals("微信设备 01", fields["clientCode"])
    }

    @Test
    fun stateOmitsClientCodeWhenProviderReturnsNull() {

        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store)
        val reply = session.onTextMessage(helloJson(token))
        val body = (reply as WsReply.Text).text
        assertFalse(body, body.contains(""""clientCode""""))
    }

    @Test
    fun rejectedStateDoesNotCarryClientCode() {

        val store = PairingTokenStore()
        store.issue()
        val session = newSession(store, clientCode = { "微信设备 01" })
        val reply = session.onTextMessage(helloJson("wrong-guess"))
        val body = (reply as WsReply.TextThenClose).text
        assertFalse(body, body.contains(""""clientCode""""))
    }

    @Test
    fun connectedStateEscapesClientCode() {

        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store, clientCode = { """带"引号"代号""" })
        val reply = session.onTextMessage(helloJson(token))
        val fields = MinimalJson.parseTopLevelObject((reply as WsReply.Text).text)
        assertEquals("""带"引号"代号""", fields["clientCode"])
    }

    @Test
    fun connectionAttachedCallbackReceivesSenderAndCloseHandle() {

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

        val store = PairingTokenStore()
        val token = store.issue()
        var closedCount = 0
        val session = newSession(store, onPairedSessionClosed = { closedCount++ })
        session.onTextMessage(helloJson("wrong-guess"))
        session.onSessionClosed()
        assertEquals("被拒连接断开不触发已配对关闭回调", 0, closedCount)
        session.onTextMessage(helloJson(token))
        session.onSessionClosed()
        assertEquals("已配对连接断开触发一次", 1, closedCount)
    }

    @Test
    fun connectedStateCarriesRollingPairingUrl() {

        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store, pairingUrl = { "http://10.0.2.15:40000/?token=rolled" })
        val reply = session.onTextMessage(helloJson(token))
        val body = (reply as WsReply.Text).text
        assertTrue(body, body.contains(""""url":"http://10.0.2.15:40000/?token=rolled""""))
    }

    @Test
    fun rejectedStateDoesNotCarryPairingUrl() {

        val store = PairingTokenStore()
        store.issue()
        val session = newSession(store, pairingUrl = { "http://10.0.2.15:40000/?token=rolled" })
        val reply = session.onTextMessage(helloJson("wrong-guess"))
        val body = (reply as WsReply.TextThenClose).text
        assertFalse(body, body.contains(""""url""""))
    }

    @Test
    fun stateOmitsUrlFieldWhenProviderReturnsNull() {

        val store = PairingTokenStore()
        val token = store.issue()
        val session = newSession(store)
        val reply = session.onTextMessage(helloJson(token))
        val body = (reply as WsReply.Text).text
        assertFalse(body, body.contains(""""url""""))
    }

    @Test
    fun connectedStateEscapesPairingUrl() {

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

        val store = PairingTokenStore()
        store.issue()
        val events = mutableListOf<String>()
        val session = newSession(store, log = { events.add(it) })
        assertEquals(WsReply.None, session.onTextMessage("""{"type":"disconnect","reason":"page-unload"}"""))
        assertFalse(events.any { it.contains("disconnect") })
        val token = store.peek()
        assertTrue(session.onTextMessage(helloJson(token!!)) is WsReply.Text)
    }

    @Test
    fun directUrlAccessWithoutTokenReturnsGuidePageTriggerState() {

        val store = PairingTokenStore()
        store.issue()
        val session = newSession(store, pairingUrl = { "http://10.0.2.15:40000/?token=rolled" })
        val reply = session.onTextMessage(helloJson(""))
        assertTrue(reply is WsReply.TextThenClose)
        reply as WsReply.TextThenClose
        assertEquals(PairingSession.CLOSE_POLICY_REJECTED, reply.closeCode)
        val fields = MinimalJson.parseTopLevelObject(reply.text)
        assertEquals("state", fields["type"])
        assertEquals("false", fields["connected"])
        assertFalse(reply.text.contains(""""url""""))
        assertTrue(fields.containsKey("charLimit"))
        assertTrue(fields.containsKey("tvName"))
    }

    @Test
    fun tokenLeftOverFromEndedSessionIsRejectedLikeExpired() {

        val store = PairingTokenStore()
        val staleToken = store.issue()
        store.clear()
        val fresh = store.issue()
        val session = newSession(store)
        val reply = session.onTextMessage(helloJson(staleToken))
        assertTrue(reply is WsReply.TextThenClose)
        assertEquals(PairingSession.CLOSE_POLICY_REJECTED, (reply as WsReply.TextThenClose).closeCode)
        assertTrue((reply as WsReply.TextThenClose).text.contains(""""connected":false"""))
        assertEquals(fresh, store.peek())
        assertTrue("当前会话新 token 不受旧 token 失败影响", session.onTextMessage(helloJson(fresh)) is WsReply.Text)
    }

    @Test
    fun consumedTokenIsRejectedWhileRolledTokenStillPairs() {

        val store = PairingTokenStore()
        val first = store.issue()
        val session = newSession(store)
        assertTrue(session.onTextMessage(helloJson(first)) is WsReply.Text)
        assertFalse(store.isActive)
        val rolled = store.issue()
        val replay = session.onTextMessage(helloJson(first))
        assertTrue(replay is WsReply.TextThenClose)
        assertEquals(PairingSession.CLOSE_POLICY_REJECTED, (replay as WsReply.TextThenClose).closeCode)
        assertTrue((replay as WsReply.TextThenClose).text.contains(""""connected":false"""))
        val nextSession = newSession(store)
        assertTrue("滚动新码可正常配对", nextSession.onTextMessage(helloJson(rolled)) is WsReply.Text)
    }
}
