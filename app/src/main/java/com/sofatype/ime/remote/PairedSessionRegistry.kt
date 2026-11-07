// T35 · 已配对会话登记表（决策 35 滚动 token + 自动顶替的纯逻辑部分）。
// 职责：登记当前唯一已配对连接（回发器 + 关闭句柄），新配对到达时交出旧连接供顶替；
// 会话关闭时按身份清除（防旧连接迟到的关闭事件误清新连接的登记）。
// 纯 Kotlin，不依赖 Android 类，可单元测试锁定顶替与清除语义；线程安全由调用方（Manager
// synchronized）保证，本类不加锁（单读线程路径 + Manager 串行化）。
package com.sofatype.ime.remote

/**
 * 连接控制句柄：[sender] 向该连接回发文本（如 replaced 通知）；
 * [close] 按给定应用关闭码关闭该连接（RFC 6455 应用段 4xxx）。
 */
class PairedConnection(
    val sender: WsMessageSender,
    val close: (Int) -> Unit,
)

class PairedSessionRegistry {

    /** 当前已配对连接：身份键（PairingSession 实例）+ 控制句柄；无已配对会话为 null。 */
    @Volatile
    private var current: Pair<Any, PairedConnection>? = null

    /** 当前是否存在已配对会话（UI 状态机「已连接」判定源）。 */
    val hasPairedSession: Boolean
        get() = current != null

    /**
     * 登记新配对连接，返回被顶替的旧连接（无旧连接返回 null）。
     * 同一身份重复登记（hello 重放后的再次配对不可能，防御）按更新句柄处理、不视为顶替。
     */
    fun attach(id: Any, connection: PairedConnection): PairedConnection? {
        val previous = current
        current = id to connection
        return previous?.takeIf { it.first !== id }?.second
    }

    /**
     * 会话关闭：仅当关闭者仍是登记中的当前会话时清除（被顶替的旧连接迟到关闭不清新登记）。
     * 返回是否发生了清除（true = 当前会话真断开，UI 需回等待态）。
     */
    fun detachIfCurrent(id: Any): Boolean {
        val currentEntry = current ?: return false
        if (currentEntry.first !== id) return false
        current = null
        return true
    }

    /** 清空登记（服务停止时调用）；返回清空前是否存在已配对会话。 */
    fun clear(): Boolean {
        val had = current != null
        current = null
        return had
    }
}
