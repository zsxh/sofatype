package com.sofatype.ime.remote

class PairingSession(
    private val tokens: PairingTokenStore,
    private val tvName: String,
    private val charLimit: Int = DEFAULT_CHAR_LIMIT,

    private val logEvent: (String) -> Unit = {},

    private val onInput: (String) -> Unit = {},

    private val onClear: () -> Unit = {},

    private val onCommit: (text: String, ack: (Boolean) -> Unit) -> Unit = { _, _ -> },

    private val onPaired: (deviceName: String?, deviceType: DeviceType, deviceKey: String?) -> Unit = { _, _, _ -> },

    private val onPairedSessionClosed: () -> Unit = {},

    private val onConnectionAttached: (sender: WsMessageSender, close: (Int) -> Unit) -> Unit = { _, _ -> },

    private val pairingUrlProvider: () -> String? = { null },

    private val clientCodeProvider: () -> String? = { null },
) : WsSessionDelegate {

    private var paired = false

    @Volatile
    private var sender: WsMessageSender? = null

    override fun attachConnection(sender: WsMessageSender, close: (Int) -> Unit) {
        this.sender = sender

        onConnectionAttached(sender, close)
    }

    override fun onSessionClosed() {

        if (paired) onPairedSessionClosed()
    }

    override fun onTextMessage(text: String): WsReply {
        val fields = try {
            MinimalJson.parseTopLevelObjectTyped(text)
        } catch (_: MinimalJson.JsonParseException) {

            if (paired) {
                logEvent("业务消息忽略：不是合法 JSON 对象")
                return WsReply.None
            }
            logEvent("配对被拒：消息不是合法 JSON 对象")
            return rejected()
        }
        val type = (fields[KEY_TYPE] as? MinimalJson.Value.Str)?.value
        if (type == MSG_HELLO) {

            return if (tokens.consume((fields[KEY_TOKEN] as? MinimalJson.Value.Str)?.value)) {
                paired = true
                logEvent("配对成功：token 已消费失效")

                val deviceType = DeviceType.fromCode((fields[KEY_DEVICE_TYPE] as? MinimalJson.Value.Str)?.value)
                val deviceKey = sanitizeDeviceKey((fields[KEY_DEVICE_KEY] as? MinimalJson.Value.Str)?.value)
                onPaired((fields[KEY_DEVICE_NAME] as? MinimalJson.Value.Str)?.value, deviceType, deviceKey)
                WsReply.Text(buildState(connected = true))
            } else {
                logEvent("配对被拒：token 缺失、无效或已使用")
                rejected()
            }
        }
        if (!paired) {

            return WsReply.None
        }
        return when (val message = RemoteInputMessages.dispatch(fields)) {
            is RemoteInputMessage.Input -> {
                onInput(message.text)
                WsReply.None
            }
            RemoteInputMessage.Clear -> {
                onClear()
                WsReply.None
            }
            is RemoteInputMessage.Commit -> {

                logEvent("收到上屏指令，写入电视输入框")
                onCommit(RemoteTextBuffer.clamp(message.text, charLimit), ::ackCommit)
                WsReply.None
            }
            RemoteInputMessage.Ignore -> WsReply.None
            RemoteInputMessage.Disconnect -> {

                logEvent("收到手机端断连通知（disconnect）")
                WsReply.None
            }
        }
    }

    private fun ackCommit(ok: Boolean) {
        sender?.send(buildCommitted(ok))
    }

    private fun rejected(): WsReply =
        WsReply.TextThenClose(buildState(connected = false), CLOSE_POLICY_REJECTED)

    private fun sanitizeDeviceKey(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() && it.length <= MAX_DEVICE_KEY_LENGTH }

    private fun buildState(connected: Boolean): String {
        val urlField = if (connected) {
            pairingUrlProvider()?.let { ",\"url\":\"${MinimalJson.escape(it)}\"" } ?: ""
        } else {
            ""
        }
        val clientCodeField = if (connected) {
            clientCodeProvider()?.let { ",\"clientCode\":\"${MinimalJson.escape(it)}\"" } ?: ""
        } else {
            ""
        }
        return "{\"$KEY_TYPE\":\"$MSG_STATE\",\"connected\":$connected," +
            "\"tvName\":\"${MinimalJson.escape(tvName)}\",\"charLimit\":$charLimit$urlField$clientCodeField}"
    }

    private fun buildCommitted(ok: Boolean): String =
        "{\"$KEY_TYPE\":\"$MSG_COMMITTED\",\"ok\":$ok}"

    companion object {

        const val DEFAULT_CHAR_LIMIT = 200

        const val KEY_TYPE = "type"
        const val KEY_TOKEN = "token"

        const val KEY_DEVICE_NAME = "deviceName"

        const val KEY_DEVICE_TYPE = "deviceType"

        const val KEY_DEVICE_KEY = "deviceKey"

        const val MAX_DEVICE_KEY_LENGTH = 128
        const val MSG_HELLO = "hello"
        const val MSG_STATE = "state"

        const val MSG_COMMITTED = "committed"

        const val MSG_REPLACED = "replaced"

        const val MSG_DISCONNECT = "disconnect"

        const val REASON_TV_EXIT = "tv-exit"

        const val REASON_NETWORK_REBUILD = "network-rebuild"

        const val CLOSE_POLICY_REJECTED = 4001

        const val CLOSE_REPLACED = 4002
    }
}
