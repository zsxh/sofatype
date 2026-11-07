// T14 · 一次性配对 token（FR-B10）：服务启动时签发、配对成功后立即失效。
// 纯 Kotlin/JVM，可被单元测试覆盖。随机源 SecureRandom，token = 16 字节随机数
// 经 Base64URL（RFC 4648 §5，去 padding）编码，URL 直拼无需转义。
// 仅驻内存，不持久化（隐私红线）；token 只在服务运行期间有效，服务停止即清空。
package com.sofatype.ime.remote

import java.security.SecureRandom

class PairingTokenStore(private val random: SecureRandom = SecureRandom()) {

    private val lock = Any()

    private var active: String? = null

    /** 当前是否存在有效 token。 */
    val isActive: Boolean
        get() = synchronized(lock) { active != null }

    /** 当前有效 token（不消费）；无有效 token 返回 null。 */
    fun peek(): String? = synchronized(lock) { active }

    /** 签发新 token 并替换旧值（旧 token 立即作废）。 */
    fun issue(): String = synchronized(lock) {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        // 复用 T13 RFC 4648 编码（既有单测锁定），标准字母表换 URL 安全字母表并去 padding
        Base64Codec.encode(bytes).replace('+', '-').replace('/', '_').trimEnd('=')
            .also { active = it }
    }

    /**
     * 校验并一次性消费：与当前 token 一致 → 置空并返回 true（配对成功）；
     * 缺失 / 不一致 / 已消费 → 返回 false，不影响当前有效值（防误伤：猜错不吊销正确 token）。
     */
    fun consume(candidate: String?): Boolean = synchronized(lock) {
        val current = active ?: return false
        if (candidate.isNullOrEmpty() || candidate != current) return false
        active = null
        true
    }

    /** 清空当前 token（服务停止时调用；token 生命周期 = 服务会话）。 */
    fun clear() = synchronized(lock) { active = null }

    companion object {
        /** token 随机字节数：128 位，Base64URL 后 22 字符。 */
        const val TOKEN_BYTES = 16
    }
}
