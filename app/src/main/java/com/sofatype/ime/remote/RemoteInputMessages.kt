package com.sofatype.ime.remote

sealed class RemoteInputMessage {

    data class Input(val text: String) : RemoteInputMessage()

    object Clear : RemoteInputMessage()

    data class Commit(val text: String) : RemoteInputMessage()

    object Disconnect : RemoteInputMessage()

    object Ignore : RemoteInputMessage()
}

object RemoteInputMessages {

    const val KEY_TYPE = "type"
    const val KEY_TEXT = "text"
    const val MSG_INPUT = "input"
    const val MSG_CLEAR = "clear"
    const val MSG_COMMIT = "commit"
    const val MSG_DISCONNECT = "disconnect"

    fun dispatch(fields: Map<String, MinimalJson.Value>): RemoteInputMessage =
        when ((fields[KEY_TYPE] as? MinimalJson.Value.Str)?.value) {
            MSG_INPUT -> {

                val text = fields[KEY_TEXT] as? MinimalJson.Value.Str
                if (text != null) RemoteInputMessage.Input(text.value) else RemoteInputMessage.Ignore
            }
            MSG_CLEAR -> RemoteInputMessage.Clear
            MSG_COMMIT -> {

                val text = fields[KEY_TEXT] as? MinimalJson.Value.Str
                if (text != null) RemoteInputMessage.Commit(text.value) else RemoteInputMessage.Ignore
            }
            MSG_DISCONNECT -> RemoteInputMessage.Disconnect
            else -> RemoteInputMessage.Ignore
        }

    fun parse(text: String): RemoteInputMessage = try {
        dispatch(MinimalJson.parseTopLevelObjectTyped(text))
    } catch (_: MinimalJson.JsonParseException) {
        RemoteInputMessage.Ignore
    }
}
