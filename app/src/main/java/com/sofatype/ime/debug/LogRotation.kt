package com.sofatype.ime.debug

data class RotationPlan(val keepBytes: Long, val discardBytes: Long)

object LogRotation {

    fun plan(currentSize: Long, incomingBytes: Long, maxBytes: Long): RotationPlan? {
        val limit = if (maxBytes < 1) 1 else maxBytes
        if (currentSize + incomingBytes <= limit) return null
        val keep = limit / 2
        val discard = (currentSize - keep).coerceAtLeast(0)
        return RotationPlan(keepBytes = keep, discardBytes = discard)
    }
}
