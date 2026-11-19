package com.sofatype.ime.remote

import java.security.SecureRandom

class PairingTokenStore(private val random: SecureRandom = SecureRandom()) {

    private val lock = Any()

    private var active: String? = null

    val isActive: Boolean
        get() = synchronized(lock) { active != null }

    fun peek(): String? = synchronized(lock) { active }

    fun issue(): String = synchronized(lock) {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)

        Base64Codec.encode(bytes).replace('+', '-').replace('/', '_').trimEnd('=')
            .also { active = it }
    }

    fun consume(candidate: String?): Boolean = synchronized(lock) {
        val current = active ?: return false
        if (candidate.isNullOrEmpty() || candidate != current) return false
        active = null
        true
    }

    fun clear() = synchronized(lock) { active = null }

    companion object {

        const val TOKEN_BYTES = 16
    }
}
