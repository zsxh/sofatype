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
// 『已上屏』」的确认信号）；回发经 attachConnection 注入的连接级 [WsMessageSender]。
// T17 增量（FR-B9 / 决策 34）：hello 成功经 [onPaired] 通知 UI 层（状态机切「已连接」，
// 副卡切已连接卡面 / 主卡状态行切绿点）；deviceName 随回调上送供状态行展示
//（决策 34：会话内存驻留，不持久化、不入日志；hello 未携带时回调 null）。
// T35 增量（决策 35 滚动 token / 自动顶替 / 断连回退）：token 消费成功后由 Manager 立即
// 滚动签发新 token（本类不持有签发时序）；[attachConnection] 同步缓存关闭句柄；
// [onSessionClosed] 仅在本连接已配对时上抛（未配对连接的断开不入会话登记）。
// T18 增量（FR-B8 刷新重连 · lead 2026-11-07 拍板 A / FR-C8）：
// ① state { connected:true } 增加当前配对地址字段 url（TV → 手机）——配对成功时 Manager
//   已滚动签发新 token 并刷新配对地址，手机页据此 history.replaceState 更新地址栏，
//   刷新页面即持有效新码重新配对（FR-B8「支持刷新重连」P0）。url 仅随 connected:true 下发
//  （被拒连接未通过配对校验，不下发有效配对地址，防未认证方套取 token）；FR-B10 逐 token
//   一次性语义不变（该码本就整卡展示在 TV 副卡上，无新增暴露面）；手机端不落任何存储。
// ② 配对后连接上的 disconnect { reason }（手机 → TV，PRD 第 7 章双向）按已知消息分发：
//   仅记事件、连接保持——会话实际断开由 TCP 关闭驱动（页面关闭 / 刷新必然关连接），
//   断连回退与清理见 Manager.onPairedSessionClosed。
// T72 增量（决策 61 · 连接代号）：hello 增 deviceType（手机端判定的设备类型，TV 不解析 UA）
// 与 deviceKey（保号键，缺失 / 空白 / 超长一律按匿名处理）；配对成功回调上送两字段供
// Manager 签发序号；state 增 clientCode（经 [clientCodeProvider] 取值，state 为代号唯一
// 事实源、无新增消息类型；仅 connected:true 携带，被拒连接不下发）。
// deviceKey / 序号不入日志（隐私红线，决策 61）。
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
    /**
     * 配对成功回调（T17 · FR-B9 / 决策 34，T72 · 决策 61 扩参）：hello 首次校验通过即触发，
     * 参数 = (hello.deviceName, hello.deviceType, hello.deviceKey)。deviceName 未携带时 null；
     * deviceType 缺失 / 未知回浏览器兜底（[DeviceType.fromCode]）；deviceKey 缺失 / 空白 /
     * 超长（>[MAX_DEVICE_KEY_LENGTH]）一律 null（按匿名处理，决策 61）。
     * 仅配对成功触发一次；被拒（无效 / 重放 / 缺失 token）不触发。
     * 回调在 WS 读线程执行，处理方自行保证线程安全。
     */
    private val onPaired: (deviceName: String?, deviceType: DeviceType, deviceKey: String?) -> Unit = { _, _, _ -> },
    /**
     * 已配对会话关闭回调（T35 · 决策 35 断连回退）：本连接**已配对**且 WS 会话结束时触发
     *（对端关闭 / 协议错误 / 被顶替关闭）；未配对连接的会话结束不触发（不入会话登记）。
     * 被顶替的旧连接也会触发本回调，处理方按「仅当前登记会话生效」口径幂等处理。
     * 回调在连接处理线程执行，处理方自行保证线程安全。
     */
    private val onPairedSessionClosed: () -> Unit = {},
    /**
     * 连接控制句柄上抛回调（T35 · 决策 35）：WS 升级完成后服务注入回发器与关闭句柄时触发，
     * 处理方（Manager）据此登记该连接的控制通道供顶替使用。回调在连接处理线程执行。
     */
    private val onConnectionAttached: (sender: WsMessageSender, close: (Int) -> Unit) -> Unit = { _, _ -> },
    /**
     * 当前配对地址提供者（T18 · FR-B8 刷新重连，lead 拍板 2026-11-07）：hello 校验通过后构建
     * state 载荷时取值（此时 Manager 已完成滚动签发，返回滚动新码地址）；null / 未提供 = 不带
     * url 字段（既有测试基线与占位页口径不变）。仅在 connected:true 时消费。
     */
    private val pairingUrlProvider: () -> String? = { null },
    /**
     * 连接代号提供者（T72 · 决策 61）：构建 state 载荷时取值（Manager 在 onPaired 内完成序号
     * 签发，返回当前连接的连接代号全文，如「微信设备 01」）；null / 未提供 = 不带 clientCode
     * 字段（既有测试基线不变）。仅在 connected:true 时消费——state 为代号唯一事实源
     *（决策 61），被拒连接未通过配对校验，不下发代号。
     */
    private val clientCodeProvider: () -> String? = { null },
) : WsSessionDelegate {

    /** 本连接配对态：hello 成功后置位；业务消息仅在此态分发（未配对连接不得注入内容）。 */
    private var paired = false

    /** 本连接主动回发器（T16）：WS 升级完成后由服务注入；断连后 send 返回 false。 */
    @Volatile
    private var sender: WsMessageSender? = null

    override fun attachConnection(sender: WsMessageSender, close: (Int) -> Unit) {
        this.sender = sender
        // T35：控制句柄上抛（Manager 登记供顶替 / 断连编排）
        onConnectionAttached(sender, close)
    }

    override fun onSessionClosed() {
        // T35：仅已配对连接的断开上抛（未配对连接结束 = 无会话登记，无需回退）
        if (paired) onPairedSessionClosed()
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
                // T17（FR-B9 / 决策 34）：配对成功通知 UI 状态机，deviceName 随回调上送（未携带 = null）。
                // T72（决策 61）：deviceType / deviceKey 随回调上送供序号签发；deviceType 缺失 /
                // 未知回浏览器兜底，deviceKey 缺失 / 空白 / 超长按匿名（null）处理
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
            RemoteInputMessage.Disconnect -> {
                // T18（PRD 第 7 章 disconnect 双向）：手机端页面关闭 / 刷新前的通知，仅记事件；
                // 会话断开由随之而来的 TCP 关闭驱动（onSessionClosed → Manager 断连回退与清理）
                logEvent("收到手机端断连通知（disconnect）")
                WsReply.None
            }
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

    /**
     * deviceKey 净化（T72 · 决策 61）：非空、长度不超 [MAX_DEVICE_KEY_LENGTH]（H5 上报为随机
     * UUID，超长取值视作异常客户端）才可作为保号键；其余一律 null（按匿名新设备签发）。
     * 键值仅内存驻留，不落日志。
     */
    private fun sanitizeDeviceKey(raw: String?): String? =
        raw?.takeIf { it.isNotBlank() && it.length <= MAX_DEVICE_KEY_LENGTH }

    /**
     * state 消息载荷（PRD 第 7 章定式 + T18 lead 拍板增量 + T72 决策 61 增量）：
     * { type, connected, tvName, charLimit }；connected:true 时附当前配对地址 url（滚动新码，供
     * 手机页刷新重连）与连接代号 clientCode（state 为代号唯一事实源，决策 61）；connected:false
     * （被拒）不带 url 与 clientCode——未通过配对校验的连接不得获得有效配对地址与代号（FR-B10）。
     */
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

    /** committed 回执载荷（T16 · PRD 第 7 章 commit 的 TV → 手机确认）：{ type, ok }。 */
    private fun buildCommitted(ok: Boolean): String =
        "{\"$KEY_TYPE\":\"$MSG_COMMITTED\",\"ok\":$ok}"

    companion object {
        /** 单次远程输入字数上限（FR-C7）。 */
        const val DEFAULT_CHAR_LIMIT = 200

        const val KEY_TYPE = "type"
        const val KEY_TOKEN = "token"

        /** hello 携带的配对设备名字段（PRD 第 7 章；T17 决策 34：会话内存驻留用于展示）。 */
        const val KEY_DEVICE_NAME = "deviceName"

        /** hello 携带的设备类型字段（T72 · 决策 61：手机端判定，TV 不解析 UA）。 */
        const val KEY_DEVICE_TYPE = "deviceType"

        /** hello 携带的保号键字段（T72 · 决策 61：H5 随机 UUID 存 localStorage，TV 内存驻留）。 */
        const val KEY_DEVICE_KEY = "deviceKey"

        /** 保号键最大长度（T72 · 决策 61 净化口径：H5 上报为随机 UUID，超长按匿名处理）。 */
        const val MAX_DEVICE_KEY_LENGTH = 128
        const val MSG_HELLO = "hello"
        const val MSG_STATE = "state"

        /** TV → 手机上屏回执消息类型（T16）。 */
        const val MSG_COMMITTED = "committed"

        /** TV → 手机「连接已被替换」通知消息类型（T35 · FR-B11 / 决策 35）。 */
        const val MSG_REPLACED = "replaced"

        /** 断连通知消息类型（T18 · PRD 第 7 章 disconnect 双向）。 */
        const val MSG_DISCONNECT = "disconnect"

        /** TV → 手机断连通知的 reason 取值：电视端退出远程（T18 · FR-B7）。 */
        const val REASON_TV_EXIT = "tv-exit"

        /**
         * TV → 手机断连通知的 reason 取值：电视端网络变化重建（R2 · PRD 5.3 行 2，lead 批准的
         * 第 7 章草案 reason 取值增量）——手机端呈现「连接已断开」+ FR-C8 重连引导，不等同电视退出。
         */
        const val REASON_NETWORK_REBUILD = "network-rebuild"

        /** 配对被拒关闭码（RFC 6455 §7.4.2：4000–4999 为应用自定义段）。 */
        const val CLOSE_POLICY_REJECTED = 4001

        /** 连接被新设备顶替的关闭码（T35 · 决策 35，应用段 4xxx）。 */
        const val CLOSE_REPLACED = 4002
    }
}
