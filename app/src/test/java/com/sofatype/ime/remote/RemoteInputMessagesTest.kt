// T15 · 业务消息解析分发单元测试（PRD 第 7 章 input / clear）：
// input 全量文本解析、clear 解析、未知类型 / 非法 JSON / 字段类型不合规 → 忽略不断连。
package com.sofatype.ime.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteInputMessagesTest {

    @Test
    fun parsesInputMessageWithFullText() {
        val message = RemoteInputMessages.parse("""{"type":"input","text":"客厅的小米盒子"}""")
        assertEquals(RemoteInputMessage.Input("客厅的小米盒子"), message)
    }

    @Test
    fun parsesInputMessageWithEmptyText() {
        val message = RemoteInputMessages.parse("""{"type":"input","text":""}""")
        assertEquals(RemoteInputMessage.Input(""), message)
    }

    @Test
    fun parsesInputMessageWithEscapedText() {
        val message = RemoteInputMessages.parse("""{"type":"input","text":"行一\n行二\t引\"号"}""")
        assertEquals(RemoteInputMessage.Input("行一\n行二\t引\"号"), message)
    }

    @Test
    fun parsesClearMessage() {
        assertEquals(RemoteInputMessage.Clear, RemoteInputMessages.parse("""{"type":"clear"}"""))
    }

    @Test
    fun clearMessageIgnoresExtraFields() {
        assertEquals(RemoteInputMessage.Clear, RemoteInputMessages.parse("""{"type":"clear","extra":1}"""))
    }

    @Test
    fun unknownTypeIsIgnored() {
        for (json in listOf(
            """{"type":"commit","text":"hi"}""", // commit 属 T16，本任务按未知忽略
            """{"type":"hello","token":"t"}""",
            """{"type":"cursor","index":3}""",
        )) {
            assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse(json))
        }
    }

    @Test
    fun missingTypeFieldIsIgnored() {
        assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse("""{"text":"hi"}"""))
    }

    @Test
    fun nonObjectJsonIsIgnored() {
        for (text in listOf("", "not json", "[1,2]", """{"type":}""")) {
            assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse(text))
        }
    }

    @Test
    fun inputWithoutTextFieldIsIgnored() {
        assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse("""{"type":"input"}"""))
    }

    @Test
    fun inputWithNonStringTextFieldIsIgnored() {
        // text 为数字 / 字面量（类型化解析可区分）：不合规，忽略
        assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse("""{"type":"input","text":123}"""))
        assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse("""{"type":"input","text":true}"""))
        assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse("""{"type":"input","text":null}"""))
    }

    @Test
    fun typedFieldDispatchMatchesParseFromString() {
        // PairingSession 复用同一次类型化解析：dispatch(fields) 与 parse(text) 结果一致
        val json = """{"type":"input","text":"同步"}"""
        val fields = MinimalJson.parseTopLevelObjectTyped(json)
        assertEquals(RemoteInputMessages.parse(json), RemoteInputMessages.dispatch(fields))
    }
}
