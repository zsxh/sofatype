// T71 · 决策 60 · 轮转规划（纯 Kotlin 模型，单元测试锁定 LogRotationTest）。
// 单文件环形语义：现有文件长度 + 本批待写入字节将超档位上限时触发轮转——
// 保留文件末尾（最新）的 keepBytes 字节、丢弃开头（最早）部分。keepBytes 取档位上限的一半
//（留一半余量供轮转后续写，避免每次冲刷都触发轮转）。纯数值判定，不做文件 IO。
package com.sofatype.ime.debug

/** 一次轮转的执行参数：保留末尾 keepBytes 字节，丢弃开头 discardBytes 字节。 */
data class RotationPlan(val keepBytes: Long, val discardBytes: Long)

object LogRotation {

    /**
     * 轮转判定：currentSize + incomingBytes > maxBytes 时返回轮转计划（保留末尾一半档位），
     * 未超限返回 null（不轮转）。maxBytes 下限 1 字节（防除零与空计划）。
     */
    fun plan(currentSize: Long, incomingBytes: Long, maxBytes: Long): RotationPlan? {
        val limit = if (maxBytes < 1) 1 else maxBytes
        if (currentSize + incomingBytes <= limit) return null
        val keep = limit / 2
        val discard = (currentSize - keep).coerceAtLeast(0)
        return RotationPlan(keepBytes = keep, discardBytes = discard)
    }
}
