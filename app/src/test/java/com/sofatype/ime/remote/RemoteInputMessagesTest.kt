// T15 · 业务消息解析分发单元测试（PRD 第 7 章 input / clear）：
// input 全量文本解析、clear 解析、未知类型 / 非法 JSON / 字段类型不合规 → 忽略不断连。
// T16 · commit { text } 上屏指令解析（FR-B6/C6）。
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
            """{"type":"hello","token":"t"}""",
            """{"type":"cursor","index":3}""",
        )) {
            assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse(json))
        }
    }

    // ---- T16 · commit 上屏指令解析（FR-B6/C6） ----

    @Test
    fun parsesCommitMessageWithText() {
        val message = RemoteInputMessages.parse("""{"type":"commit","text":"客厅"}""")
        assertEquals(RemoteInputMessage.Commit("客厅"), message)
    }

    @Test
    fun parsesCommitMessageWithEmptyText() {
        // 空串是合法载荷（H5 空内容按钮置灰不会发送，防御性解析仍接受）
        assertEquals(RemoteInputMessage.Commit(""), RemoteInputMessages.parse("""{"type":"commit","text":""}"""))
    }

    @Test
    fun parsesCommitMessageWithEscapedText() {
        val message = RemoteInputMessages.parse("""{"type":"commit","text":"行一\n行二"}""")
        assertEquals(RemoteInputMessage.Commit("行一\n行二"), message)
    }

    @Test
    fun commitWithoutTextFieldIsIgnored() {
        assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse("""{"type":"commit"}"""))
    }

    @Test
    fun commitWithNonStringTextFieldIsIgnored() {
        assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse("""{"type":"commit","text":123}"""))
        assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse("""{"type":"commit","text":null}"""))
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
        for (json in listOf(
            """{"type":"input","text":"同步"}""",
            """{"type":"commit","text":"上屏内容"}""",
        )) {
            val fields = MinimalJson.parseTopLevelObjectTyped(json)
            assertEquals(RemoteInputMessages.parse(json), RemoteInputMessages.dispatch(fields))
        }
    }
}
