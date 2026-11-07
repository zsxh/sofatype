// T13 · 监听地址选择（决策 16：多网络接口时取默认路由 IPv4；不监听 0.0.0.0）。
// 纯 Kotlin：候选由调用方收集（Android 侧见 RemoteInputServerManager），
// 选择规则全部在本层，可被 JVM 单元测试覆盖。
package com.sofatype.ime.remote

/** 一个网络接口的候选描述：ipv4 已由调用方按 [BindAddressSelector.pickIpv4] 选定（可为 null）。 */
data class NicCandidate(
    val name: String,
    val ipv4: String?,
    val isUp: Boolean,
    val isLoopback: Boolean,
    /** 该接口是否持有默认路由（决策 16 的选取优先级最高项）。 */
    val isDefaultRoute: Boolean,
)

object BindAddressSelector {

    /**
     * 选取监听地址，规则（全部确定性、可单测）：
     * 1. 过滤：接口 up、非回环、有 IPv4；
     * 2. 组内优先：默认路由接口 > 站点本地地址（10/172.16-31/192.168）> 其他地址；
     * 3. 同组按接口名升序，保证确定性。
     * 无候选返回 null。
     */
    fun select(candidates: List<NicCandidate>): NicCandidate? =
        candidates.asSequence()
            .filter { it.isUp && !it.isLoopback && it.ipv4 != null }
            .sortedWith(
                compareByDescending<NicCandidate> { it.isDefaultRoute }
                    .thenByDescending { isSiteLocalIpv4(it.ipv4!!) }
                    .thenBy { it.name },
            )
            .firstOrNull()

    /**
     * 同一接口有多个 IPv4 时取值规则：跳过回环与无法解析的值，
     * 站点本地优先，组内按字符串升序保证确定性；无可用值返回 null。
     */
    fun pickIpv4(addresses: List<String>): String? =
        addresses.asSequence()
            .filter { parseIpv4Octets(it) != null && !isLoopbackIpv4(it) }
            .sortedWith(compareByDescending<String> { isSiteLocalIpv4(it) }.thenBy { it })
            .firstOrNull()

    /** IPv4 站点本地段（RFC 1918）：10.0.0.0/8、172.16.0.0/12、192.168.0.0/16。 */
    fun isSiteLocalIpv4(address: String): Boolean {
        val octets = parseIpv4Octets(address) ?: return false
        return when {
            octets[0] == 10 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            else -> false
        }
    }

    /** IPv4 回环段：127.0.0.0/8。 */
    fun isLoopbackIpv4(address: String): Boolean {
        val octets = parseIpv4Octets(address) ?: return false
        return octets[0] == 127
    }

    /** 解析点分 IPv4；非 4 段 / 非数字 / 越界（>255 或负数）返回 null。 */
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
