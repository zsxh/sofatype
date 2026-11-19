package com.sofatype.ime.remote

class RemoteTextBuffer(val charLimit: Int = PairingSession.DEFAULT_CHAR_LIMIT) {

    private var current: String = ""

    val text: String
        get() = synchronized(this) { current }

    val hasText: Boolean
        get() = synchronized(this) { current.isNotEmpty() }

    fun set(text: String): String = synchronized(this) {
        current = clamp(text, charLimit)
        current
    }

    fun clear() = synchronized(this) {
        current = ""
    }

    companion object {

        fun clamp(text: String, limit: Int): String {
            if (limit <= 0) return ""
            if (text.length <= limit) {

                return text
            }
            var count = 0
            var i = 0
            while (i < text.length) {
                if (count == limit) return text.substring(0, i)
                val c = text[i]
                i += if (c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate()) 2 else 1
                count++
            }
            return text
        }
    }
}
