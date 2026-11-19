package com.sofatype.ime.remote

data class NicCandidate(
    val name: String,
    val ipv4: String?,
    val isUp: Boolean,
    val isLoopback: Boolean,

    val isDefaultRoute: Boolean,
)

object BindAddressSelector {

    fun select(candidates: List<NicCandidate>): NicCandidate? =
        candidates.asSequence()
            .filter { it.isUp && !it.isLoopback && it.ipv4 != null }
            .sortedWith(
                compareByDescending<NicCandidate> { it.isDefaultRoute }
                    .thenByDescending { isSiteLocalIpv4(it.ipv4!!) }
                    .thenBy { it.name },
            )
            .firstOrNull()

    fun pickIpv4(addresses: List<String>): String? =
        addresses.asSequence()
            .filter { parseIpv4Octets(it) != null && !isLoopbackIpv4(it) }
            .sortedWith(compareByDescending<String> { isSiteLocalIpv4(it) }.thenBy { it })
            .firstOrNull()

    fun isSiteLocalIpv4(address: String): Boolean {
        val octets = parseIpv4Octets(address) ?: return false
        return when {
            octets[0] == 10 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            else -> false
        }
    }

    fun isLoopbackIpv4(address: String): Boolean {
        val octets = parseIpv4Octets(address) ?: return false
        return octets[0] == 127
    }

    private fun parseIpv4Octets(address: String): IntArray? {
        val parts = address.split('.')
        if (parts.size != 4) return null
        val octets = IntArray(4)
        for (i in 0 until 4) {
            val value = parts[i].toIntOrNull() ?: return null
            if (value < 0 || value > 255) return null
            octets[i] = value
        }
        return octets
    }
}
