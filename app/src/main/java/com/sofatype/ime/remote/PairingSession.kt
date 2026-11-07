// T14 · hello/state 配对握手（PRD 第 7 章、FR-B10）。
// 手机 → TV：hello { token, deviceName }；TV → 手机：state { connected, tvName, charLimit }。
// token 校验（一次性）：有效 → 回 state { connected:true } 且 token 立即失效，连接保持（承载业务消息）；
// 缺失 / 无效 / 已使用 → 回 state { connected:false } 后以 4001 关闭连接（RFC 6455 应用段关闭码）。
// deviceName 按协议接收后即丢弃：不存储、不展示、不记日志（隐私红线，AGENTS 禁止事项 2）。
// T15 增量：配对成功后的连接进入业务分发——input { text } / clear {} 经 [RemoteInputMessages]
// 解析后回调 [onInput] / [onClear]；未配对连接的业务消息仍按 T14 口径忽略（防未配对先注内容）；
// 配对后重放 hello 沿用 T14 基线（token 已消费 → state connected:false + 4001）；
// 配对后非法 JSON / 未知类型按忽略处理，不断开已建立的会话。
// T16 增量（FR-B6/C6 · 决策 9/10）：commit { text } 上屏指令——配对后经 [onCommit] 交付
//（文本按 charLimit 码点兜底截断，与显示缓冲同口径），写入结果经 [ack] 异步回报；
// ack 触发本连接 committed { ok } 回执（TV → 手机，PRD「上屏成功后手机端草稿清空并提示
// 『已上屏』」的确认信号）；回发经 attachSender 注入的连接级 [WsMessageSender]。
// 本实例持有每连接的配对态：由 RemoteInputServerManager 以会话工厂按连接创建（一连接一实例）。
package com.sofatype.ime.remote

class PairingSession(
    private val tokens: PairingTokenStore,
    private val tvName: String,
    private val charLimit: Int = DEFAULT_CHAR_LIMIT,
    /** 事件日志注入点（只记事件，不记内容）；Android 侧由 Manager 接 logcat。 */
    private val logEvent: (String) -> Unit = {},
    /** 业务回调（T15）：input 全量文本（已按会话口径原样上送，上限由缓冲模型兜底）。 */
    private val onInput: (String) -> Unit = {},
    /** 业务回调（T15）：clear 清空。 */
    private val onClear: () -> Unit = {},
    /**
     * 业务回调（T16 · FR-B6）：commit 上屏指令。text 为指令载荷（已按 [charLimit] 码点兜底截断）；
     * [ack] 由会话提供，处理方在上屏写入完成后调用（true = 已写入光标处 / false = 写入失败），
     * 会话据此回发 committed { ok } 给手机端。
     */
    private val onCommit: (text: String, ack: (Boolean) -> Unit) -> Unit = { _, _ -> },
) : WsSessionDelegate {

    /** 本连接配对态：hello 成功后置位；业务消息仅在此态分发（未配对连接不得注入内容）。 */
    private var paired = false

    /** 本连接主动回发器（T16）：WS 升级完成后由服务注入；断连后 send 返回 false。 */
    @Volatile
    private var sender: WsMessageSender? = null

    override fun attachSender(sender: WsMessageSender) {
        this.sender = sender
    }

    override fun onTextMessage(text: String): WsReply {
        val fields = try {
            MinimalJson.parseTopLevelObjectTyped(text)
        } catch (_: MinimalJson.JsonParseException) {
            // 配对后连接上的非法 JSON：忽略并保持会话；未配对连接按 T14 口径拒绝
            if (paired) {
                logEvent("业务消息忽略：不是合法 JSON 对象")
                return WsReply.None
            }
            logEvent("配对被拒：消息不是合法 JSON 对象")
            return rejected()
        }
        val type = (fields[KEY_TYPE] as? MinimalJson.Value.Str)?.value
        if (type == MSG_HELLO) {
            // hello 通道（含配对后重放，T14 基线口径不变）：token 一次性，重放必被拒 4001
            return if (tokens.consume((fields[KEY_TOKEN] as? MinimalJson.Value.Str)?.value)) {
                paired = true
                logEvent("配对成功：token 已消费失效")
                WsReply.Text(buildState(connected = true))
            } else {
                logEvent("配对被拒：token 缺失、无效或已使用")
                rejected()
            }
        }
        if (!paired) {
            // 未配对连接的非 hello 消息：忽略不回发，连接保持（业务消息在配对成功后才分发）
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
                // T16（FR-B6）：上屏指令。文本码点兜底截断（与显示缓冲同口径，防超限载荷直写宿主），
                // 交 Manager → IME 写入光标处；ack 由处理方在写入完成后调用（异步回执 committed）
                logEvent("收到上屏指令，写入电视输入框")
                onCommit(RemoteTextBuffer.clamp(message.text, charLimit), ::ackCommit)
                WsReply.None
            }
            RemoteInputMessage.Ignore -> WsReply.None
        }
    }

    /**
     * commit 处理结果回执（T16 · FR-B6/C6）：ok = 上屏写入成功。
     * 回发经本连接 [sender]（任意线程可调，写通道已做帧级互斥）；
     * 回发器缺失（委托未注入 / 已断开）静默丢弃，不影响处理方。
     */
    private fun ackCommit(ok: Boolean) {
        sender?.send(buildCommitted(ok))
    }

    private fun rejected(): WsReply =
        WsReply.TextThenClose(buildState(connected = false), CLOSE_POLICY_REJECTED)

    /** state 消息载荷（PRD 第 7 章定式）：{ type, connected, tvName, charLimit }。 */
    private fun buildState(connected: Boolean): String =
        "{\"$KEY_TYPE\":\"$MSG_STATE\",\"connected\":$connected," +
            "\"tvName\":\"${MinimalJson.escape(tvName)}\",\"charLimit\":$charLimit}"

    /** committed 回执载荷（T16 · PRD 第 7 章 commit 的 TV → 手机确认）：{ type, ok }。 */
    private fun buildCommitted(ok: Boolean): String =
        "{\"$KEY_TYPE\":\"$MSG_COMMITTED\",\"ok\":$ok}"

    companion object {
        /** 单次远程输入字数上限（FR-C7）。 */
        const val DEFAULT_CHAR_LIMIT = 200

        const val KEY_TYPE = "type"
        const val KEY_TOKEN = "token"
        const val MSG_HELLO = "hello"
        const val MSG_STATE = "state"

        /** TV → 手机上屏回执消息类型（T16）。 */
        const val MSG_COMMITTED = "committed"

        /** 配对被拒关闭码（RFC 6455 §7.4.2：4000–4999 为应用自定义段）。 */
        const val CLOSE_POLICY_REJECTED = 4001
    }
}
