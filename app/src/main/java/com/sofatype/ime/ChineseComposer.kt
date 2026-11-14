// T10 · 中文组合态状态机（FR-A4 按键进入拼音组合 / FR-A8 组合态候选 / 决策 8 R-2 空格语义）。
// T66 · 决策 51-②：分词切分符——组合态内插入（如 xi'an）、显示于组合串；候选查询与原样上屏
// 均剥离切分符（引擎只接受纯小写字母串；选词上屏经区域替换天然不落切分符）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（T5 施工口径，同 KeyboardLayout/FocusNavigator）。
// 候选产出以函数注入（真实查询引擎来自 :pinyin 模块，SofatypeImeService 装配）；
// 上屏 / 组合串显示等 InputConnection 操作属服务层，本类只管组合串与候选两个状态。
package com.sofatype.ime

import com.sofatype.pinyin.PinyinEngine

class ChineseComposer(private val queryCandidates: (String) -> List<String>) {

    /** 分词切分符（T66 · 决策 51-②：半角撇号，如 xi'an）。 */
    val segmentMark: Char = '\''

    /** 拼音组合串（小写字母与切分符；空 = 无组合态）。 */
    var composition: String = ""
        private set

    /** 当前候选词（与组合串对应；无组合态或无命中时为空）。 */
    var candidates: List<String> = emptyList()
        private set

    /** 组合态是否有候选（决策 8 R-2：空格键「有候选选首候选、无候选输入空格」的判定项）。 */
    val hasCandidates: Boolean get() = candidates.isNotEmpty()

    /**
     * 字母入组（FR-A4 中文模式）：统一转小写（拼音全拼小写口径，大小写状态只作用于键面不作用于组合串）。
     * 组合串达到引擎查询上限后不再入组（超长输入引擎恒返回空候选，继续入组无意义）。
     */
    fun appendLetter(letter: Char) {
        if (composition.length >= PinyinEngine.MAX_INPUT_LEN) return
        composition += letter.lowercaseChar()
        refresh()
    }

    /**
     * 插入分词切分符（T66 · 决策 51-②）：仅组合态内生效（无组合态返回 false，服务层按键忽略）。
     * 切分符只改变组合串显示与引擎查询输入的剥离形态，候选随组合串刷新。
     */
    fun appendSegment(): Boolean {
        if (composition.isEmpty()) return false
        if (composition.length >= PinyinEngine.MAX_INPUT_LEN) return false
        composition += segmentMark
        refresh()
        return true
    }

    /**
     * 删除组合串末位（组合态内退格；末位为切分符时先删切分符）。
     * 返回是否发生了删除；false = 组合态外（服务层改走宿主文本删除）。
     */
    fun deleteLastLetter(): Boolean {
        if (composition.isEmpty()) return false
        composition = composition.dropLast(1)
        refresh()
        return true
    }

    /** 首候选（R-2 空格选首候选；无候选返回 null）。 */
    fun firstCandidate(): String? = candidates.firstOrNull()

    /** 结束组合（选中上屏 / 中英切换 / 进符号键盘 / 完成键 / 键盘重建）：清空组合串与候选。 */
    fun reset() {
        composition = ""
        candidates = emptyList()
    }

    /**
     * 候选查询用组合串（剥离切分符）：引擎查询只接受纯小写字母串（FR-A8 口径），
     * 切分符仅作组合态显示与上屏剥离语义（决策 51-②）。
     */
    fun queryInput(): String = stripSegmentMarks(composition)

    /**
     * 原样上屏用组合串（剥离切分符，决策 51-②「组合串无候选原样上屏且不带切分符」）；
     * 选词上屏经组合区域替换天然不带切分符，不走本出口。
     */
    fun committedText(): String = stripSegmentMarks(composition)

    /** 依组合串刷新候选（组合态外恒为空；查询入参剥离切分符）。 */
    private fun refresh() {
        candidates = if (composition.isEmpty()) emptyList() else queryCandidates(queryInput())
    }

    /** 切分符剥离：组合串 → 纯字母查询/上屏串。 */
    private fun stripSegmentMarks(source: String): String = source.replace(segmentMark.toString(), "")
}
