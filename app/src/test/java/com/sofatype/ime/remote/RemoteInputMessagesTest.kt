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

    @Test
    fun parsesCommitMessageWithText() {
        val message = RemoteInputMessages.parse("""{"type":"commit","text":"客厅"}""")
        assertEquals(RemoteInputMessage.Commit("客厅"), message)
    }

    @Test
    fun parsesCommitMessageWithEmptyText() {

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

        assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse("""{"type":"input","text":123}"""))
        assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse("""{"type":"input","text":true}"""))
        assertEquals(RemoteInputMessage.Ignore, RemoteInputMessages.parse("""{"type":"input","text":null}"""))
    }

    @Test
    fun typedFieldDispatchMatchesParseFromString() {

        for (json in listOf(
            """{"type":"input","text":"同步"}""",
            """{"type":"commit","text":"上屏内容"}""",
            """{"type":"disconnect","reason":"page-unload"}""",
        )) {
            val fields = MinimalJson.parseTopLevelObjectTyped(json)
            assertEquals(RemoteInputMessages.parse(json), RemoteInputMessages.dispatch(fields))
        }
    }

    @Test
    fun parsesDisconnectMessageWithReason() {
        val message = RemoteInputMessages.parse("""{"type":"disconnect","reason":"page-unload"}""")
        assertEquals(RemoteInputMessage.Disconnect, message)
    }

    @Test
    fun parsesDisconnectMessageWithoutReason() {

        assertEquals(RemoteInputMessage.Disconnect, RemoteInputMessages.parse("""{"type":"disconnect"}"""))
    }
}
