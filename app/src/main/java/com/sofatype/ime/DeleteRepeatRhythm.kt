package com.sofatype.ime

class DeleteRepeatRhythm(

    private val initialDelayMs: Long = INITIAL_DELAY_MS,

    private val firstIntervalMs: Long = FIRST_INTERVAL_MS,

    private val floorIntervalMs: Long = FLOOR_INTERVAL_MS,

    private val stepMs: Long = STEP_MS,
) {

    init {
        require(
            initialDelayMs > 0 && floorIntervalMs > 0 &&
                firstIntervalMs >= floorIntervalMs && stepMs > 0,
        ) {
            "连删节奏参数非法：initial=$initialDelayMs first=$firstIntervalMs floor=$floorIntervalMs step=$stepMs"
        }
    }

    fun delayBefore(repeatIndex: Int): Long = when {
        repeatIndex <= 0 -> initialDelayMs
        else -> maxOf(floorIntervalMs, firstIntervalMs - (repeatIndex - 1) * stepMs)
    }

    companion object {

        const val INITIAL_DELAY_MS = 400L

        const val FIRST_INTERVAL_MS = 200L

        const val FLOOR_INTERVAL_MS = 50L

        const val STEP_MS = 25L
    }
}
