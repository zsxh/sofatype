// T14 · hello/state 配对握手（PRD 第 7 章、FR-B10）。
// 手机 → TV：hello { token, deviceName }；TV → 手机：state { connected, tvName, charLimit }。
// token 校验（一次性）：有效 → 回 state { connected:true } 且 token 立即失效，连接保持（T15 起承载业务消息）；
// 缺失 / 无效 / 已使用 → 回 state { connected:false } 后以 4001 关闭连接（RFC 6455 应用段关闭码）。
// deviceName 按协议接收后即丢弃：不存储、不展示、不记日志（隐私红线，AGENTS 禁止事项 2）。
// 非 hello 消息在 T14 不处理（忽略、连接保持），业务消息随 T15 接入。
package com.sofatype.ime.remote

class PairingSession(
    private val tokens: PairingTokenStore,
    private val tvName: String,
    private val charLimit: Int = DEFAULT_CHAR_LIMIT,
    /** 事件日志注入点（只记事件，不记内容）；Android 侧由 Manager 接 logcat。 */
    private val logEvent: (String) -> Unit = {},
) : WsSessionDelegate {

    override fun onTextMessage(text: String): WsReply {
        val fields = try {
            MinimalJson.parseTopLevelObject(text)
        } catch (_: MinimalJson.JsonParseException) {
            logEvent("配对被拒：消息不是合法 JSON 对象")
            return rejected()
        }
        if (fields[KEY_TYPE] != MSG_HELLO) {
            // 非 hello 消息：T14 忽略不回发，连接保持
            return WsReply.None
        }
        return if (tokens.consume(fields[KEY_TOKEN])) {
            logEvent("配对成功：token 已消费失效")
            WsReply.Text(buildState(connected = true))
        } else {
            logEvent("配对被拒：token 缺失、无效或已使用")
            rejected()
        }
    }

    private fun rejected(): WsReply =
        WsReply.TextThenClose(buildState(connected = false), CLOSE_POLICY_REJECTED)

    /** state 消息载荷（PRD 第 7 章定式）：{ type, connected, tvName, charLimit }。 */
    private fun buildState(connected: Boolean): String =
        "{\"$KEY_TYPE\":\"$MSG_STATE\",\"connected\":$connected," +
            "\"tvName\":\"${MinimalJson.escape(tvName)}\",\"charLimit\":$charLimit}"

    companion object {
        /** 单次远程输入字数上限（FR-C7）。 */
        const val DEFAULT_CHAR_LIMIT = 200

        const val KEY_TYPE = "type"
        const val KEY_TOKEN = "token"
        const val MSG_HELLO = "hello"
        const val MSG_STATE = "state"

        /** 配对被拒关闭码（RFC 6455 §7.4.2：4000–4999 为应用自定义段）。 */
        const val CLOSE_POLICY_REJECTED = 4001
    }
}
