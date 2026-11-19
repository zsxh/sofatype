package com.sofatype.ime.remote

class PairedConnection(
    val sender: WsMessageSender,
    val close: (Int) -> Unit,
)

class PairedSessionRegistry {

    @Volatile
    private var current: Pair<Any, PairedConnection>? = null

    val hasPairedSession: Boolean
        get() = current != null

    fun currentConnection(): PairedConnection? = current?.second

    fun attach(id: Any, connection: PairedConnection): PairedConnection? {
        val previous = current
        current = id to connection
        return previous?.takeIf { it.first !== id }?.second
    }

    fun detachIfCurrent(id: Any): Boolean {
        val currentEntry = current ?: return false
        if (currentEntry.first !== id) return false
        current = null
        return true
    }

    fun clear(): Boolean {
        val had = current != null
        current = null
        return had
    }
}
