// T15 · 远程输入缓冲模型（FR-C7 字数上限 + 第 8 章隐私）。
// 仅驻内存、不持久化（会话结束随服务停止清空）；上限按 Unicode 码点计
//（与手机端 H5 同口径），截断不拆散代理对（emoji 等增补字符保持完整）。
// 纯 Kotlin/JVM，可被单元测试覆盖；不得记录缓冲内容（隐私红线）。
package com.sofatype.ime.remote

class RemoteTextBuffer(val charLimit: Int = PairingSession.DEFAULT_CHAR_LIMIT) {

    private var current: String = ""

    /** 当前缓冲文本；空串表示无内容。 */
    val text: String
        get() = synchronized(this) { current }

    /** 是否有非空内容。 */
    val hasText: Boolean
        get() = synchronized(this) { current.isNotEmpty() }

    /**
     * 全量写入（对应 input 消息）：超限时按码点截断到 [charLimit]，返回实际存储文本。
     * 服务端为防御性兜底（手机端 H5 已先行限流），正常路径不会触发截断。
     */
    fun set(text: String): String = synchronized(this) {
        current = clamp(text, charLimit)
        current
    }

    /** 清空（对应 clear 消息 / 会话结束）。幂等。 */
    fun clear() = synchronized(this) {
        current = ""
    }

    companion object {

        /**
         * 按码点截断到 [limit]：增补字符（代理对，2 个 char）计 1 个码点且保持完整——
         * 边界落在代理对中间时整对排除（不产生半只 emoji）。
         */
        fun clamp(text: String, limit: Int): String {
            if (limit <= 0) return ""
            if (text.length <= limit) {
                // 快路径：UTF-16 单元数不超限则码点数必不超限，原样返回
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
