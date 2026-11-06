// T10 · 中文组合态状态机（FR-A4 按键进入拼音组合 / FR-A8 组合态候选 / 决策 8 R-2 空格语义）。
// 纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试（T5 施工口径，同 KeyboardLayout/FocusNavigator）。
// 候选产出以函数注入（真实查询引擎来自 :pinyin 模块，SofatypeImeService 装配）；
// 上屏 / 组合串显示等 InputConnection 操作属服务层，本类只管组合串与候选两个状态。
package com.sofatype.ime

import com.sofatype.pinyin.PinyinEngine

class ChineseComposer(private val queryCandidates: (String) -> List<String>) {

    /** 拼音组合串（全为小写字母；空 = 无组合态）。 */
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
     * 删除组合串末位（组合态内退格）。
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

    /** 依组合串刷新候选（组合态外恒为空）。 */
    private fun refresh() {
        candidates = if (composition.isEmpty()) emptyList() else queryCandidates(composition)
    }
}
