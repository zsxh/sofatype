// T15 · 业务消息解析与分发（PRD 第 7 章）：
// input { text } = 当前编辑区全量文本（实时显示），clear {} = 清空。
// 配对成功后的连接才进入业务分发（见 PairingSession）；解析失败 / 未知类型 / 字段缺失
// 一律按「忽略、连接保持」处理（不回发、不断连，向前兼容后续消息类型如 cursor / commit）。
// 纯 Kotlin/JVM，可被单元测试覆盖；任何路径不得记录消息内容（隐私红线）。
package com.sofatype.ime.remote

/** 业务消息解析结果（PRD 第 7 章 input / clear）。 */
sealed class RemoteInputMessage {
    /** 手机 → TV 全量编辑文本；TV 端实时显示。data 类：值语义相等，便于测试断言。 */
    data class Input(val text: String) : RemoteInputMessage()

    /** 手机 → TV 清空指令；TV 端同步清空显示。 */
    object Clear : RemoteInputMessage()

    /** 非业务消息（解析失败 / 未知类型 / 字段不合规）：忽略，连接保持。 */
    object Ignore : RemoteInputMessage()
}

object RemoteInputMessages {

    const val KEY_TYPE = "type"
    const val KEY_TEXT = "text"
    const val MSG_INPUT = "input"
    const val MSG_CLEAR = "clear"

    /** 从已解析的类型化字段分发（PairingSession 复用同一次解析，避免二次解析）。 */
    fun dispatch(fields: Map<String, MinimalJson.Value>): RemoteInputMessage =
        when ((fields[KEY_TYPE] as? MinimalJson.Value.Str)?.value) {
            MSG_INPUT -> {
                // text 必须是 JSON 字符串：缺失或数字/字面量一律忽略
                val text = fields[KEY_TEXT] as? MinimalJson.Value.Str
                if (text != null) RemoteInputMessage.Input(text.value) else RemoteInputMessage.Ignore
            }
            MSG_CLEAR -> RemoteInputMessage.Clear
            else -> RemoteInputMessage.Ignore
        }

    /** 从原始文本解析分发（独立使用入口）。 */
    fun parse(text: String): RemoteInputMessage = try {
        dispatch(MinimalJson.parseTopLevelObjectTyped(text))
    } catch (_: MinimalJson.JsonParseException) {
        RemoteInputMessage.Ignore
    }
}
