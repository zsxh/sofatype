// T22 · 删除键长按连删节奏模型（FR-A12「删除键长按连续删除，先慢后快」）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（同 KeyboardLayout / ImeShowGuard 施工口径）。
// 口径：单击完成首删后，先等初始延迟再出第二次删除；其后重复间隔自加速起点逐级（线性步进）
// 递减至下限后恒定——走查可感知「先慢后快」。节奏单一事实源在本模型：服务层只在本模型给出的
// 延迟处排程下一删（见 SofatypeImeService 对应注释），框架键重复事件（节奏由系统重复率决定，
// 不满足本口径）不消费。
package com.sofatype.ime

/**
 * 删除键长按连删节奏（T22 · FR-A12）。
 * 时间轴（按住期间）：单击首删 → [initialDelayMs] → 重复删 #1 → [firstIntervalMs] →
 * 重复删 #2 →（逐级递减 [stepMs]）→ …… → [floorIntervalMs] 恒定。
 */
class DeleteRepeatRhythm(
    /** 首删后到第二次删除的初始延迟（ms）。 */
    private val initialDelayMs: Long = INITIAL_DELAY_MS,
    /** 加速起点间隔（ms）：重复删 #1 到 #2 的间隔。 */
    private val firstIntervalMs: Long = FIRST_INTERVAL_MS,
    /** 加速下限间隔（ms）：分级加速到达后恒定，连删最快节奏。 */
    private val floorIntervalMs: Long = FLOOR_INTERVAL_MS,
    /** 每级加速步长（ms）：相邻两级重复间隔的差值。 */
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

    /**
     * 第 [repeatIndex] 次重复删除（0 起：0 = 按住后第一次重复删除）前应等待的毫秒数。
     * 0 = 初始延迟；1 = 加速起点；其后逐级递减 [stepMs] 至 [floorIntervalMs] 封底；
     * 负值按 0 口径处理（重复计数边界防御）。
     */
    fun delayBefore(repeatIndex: Int): Long = when {
        repeatIndex <= 0 -> initialDelayMs
        else -> maxOf(floorIntervalMs, firstIntervalMs - (repeatIndex - 1) * stepMs)
    }

    companion object {
        /** 首删后初始延迟（任务口径约 400ms）。 */
        const val INITIAL_DELAY_MS = 400L

        /** 加速起点间隔（任务口径约 200ms）。 */
        const val FIRST_INTERVAL_MS = 200L

        /** 加速下限间隔（任务口径约 50ms）。 */
        const val FLOOR_INTERVAL_MS = 50L

        /** 分级加速步长：200→175→150→…→50（7 级到下限）。 */
        const val STEP_MS = 25L
    }
}
