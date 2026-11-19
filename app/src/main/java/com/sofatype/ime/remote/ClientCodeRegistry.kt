package com.sofatype.ime.remote

data class ClientCode(

    val type: DeviceType,

    val number: Int,
) {

    val numberText: String
        get() = number.toString().padStart(MIN_NUMBER_WIDTH, '0')

    val text: String
        get() = "${type.label}$DEVICE_SUFFIX $numberText"

    companion object {

        private const val MIN_NUMBER_WIDTH = 2

        const val DEVICE_SUFFIX = "设备"
    }
}

class ClientCodeRegistry(

    private val capacity: Int = DEFAULT_CAPACITY,

    private val ttlMs: Long = DEFAULT_TTL_MS,
) {

    private class Entry(
        var type: DeviceType,
        val number: Int,
        var lastSeenMs: Long,
    )

    private val entries = HashMap<String, Entry>()

    @Volatile
    private var pinnedKey: String? = null

    fun issue(deviceKey: String?, type: DeviceType, nowMs: Long): ClientCode = synchronized(this) {
        purgeExpired(nowMs)
        if (deviceKey == null) {
            return ClientCode(type, nextNumber())
        }
        val hit = entries[deviceKey]
        if (hit != null) {
            hit.type = type
            hit.lastSeenMs = nowMs
            return ClientCode(type, hit.number)
        }
        if (entries.size >= capacity) evictOldestUnpinned()
        val number = nextNumber()
        entries[deviceKey] = Entry(type, number, nowMs)
        ClientCode(type, number)
    }

    fun touch(deviceKey: String?, nowMs: Long) {
        if (deviceKey == null) return
        synchronized(this) {
            entries[deviceKey]?.lastSeenMs = nowMs
        }
    }

    fun pin(deviceKey: String?) {
        pinnedKey = deviceKey
    }

    fun unpin() {
        pinnedKey = null
    }

    fun clear() = synchronized(this) {
        entries.clear()
    }

    private fun purgeExpired(nowMs: Long) {
        val expiredBefore = nowMs - ttlMs
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            val pinned = item.key == pinnedKey
            if (!pinned && item.value.lastSeenMs <= expiredBefore) iterator.remove()
        }
    }

    private fun evictOldestUnpinned() {
        val oldest = entries.entries
            .filter { it.key != pinnedKey }
            .minByOrNull { it.value.lastSeenMs } ?: return
        entries.remove(oldest.key)
    }

    private fun nextNumber(): Int = (entries.values.maxOfOrNull { it.number } ?: 0) + 1

    companion object {

        const val DEFAULT_CAPACITY = 8

        const val DEFAULT_TTL_MS = 15 * 60 * 1000L
    }
}
