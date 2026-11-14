// T6 · 遥控器焦点导航模型（FR-A11）：纯 Kotlin，不依赖 Android 类，可脱离模拟器独立单元测试。
// 口径：全表键位恰构成 12 列 × 4 行完整网格（各行键中心集合完全一致），
// 行内键格位由 x 原点推导（步进与 T28 列节奏单测同口径：键 96 + 键距 10 = 106、spacer 48 + 键距 10 = 58，
// 1080p px 基准，T68 · 决策 58 画板 designs/20261114）；跨两行完成键按绝对定位
//（字母键盘左缘 1278px / 符号键盘 1282px，画板 absolute 定位）显式声明占据行 3/4 的图标列格位。
// 导航规则（FR-A11「符合视觉行列关系，不跳变」）：
//   左/右 = 同行向该方向的最近实键（空格位无键格越空直达，T58：不越任何实键，仍是视觉相邻键）；
//   上/下 = 同列向该方向的最近实键（现行布局各列全实键，语义与逐格一致）；
//   扫到自身占据格位（跨行键）即停不动；目标格不存在（表缘）时不动；
//   完成键以顶行格位为导航锚（跨行高键，向下仍是自身视觉区域 → 不动）。
// 方向键事件由 SofatypeImeService.onKeyDown 处理（转发键事件不经过 IME view 树，见该类 onKeyDown 注释），
// 表缘 next 返回 null 时调用方消费事件并保持焦点，不进入系统 focus search，保证路径确定不跳变。
package com.sofatype.ime

/** 十字键方向（FR-A11）。 */
enum class FocusDirection { LEFT, RIGHT, UP, DOWN }

/**
 * 遥控器焦点导航器：输入当前焦点键 id 与方向，输出下一焦点键 id（null = 不动）。
 * 格位坐标 (row, col) 均 1-based；跨行键（完成键等）占据多个格位。
 *
 * @param rows 行键位（行内键格位由画板步进口径推导）
 * @param rows 行键位（行内键格位由画板步进口径推导；缺省 = 英文态字母键盘——T66 · 决策 51 起
 *   行 1 功能键区头键随语言模式分化，两态格网同构、仅该键位键 id 不同，服务层按当前语言显式传参）
 * @param overlayCells 跨行浮层键格位（键 id → 占据格位列表，如完成键/空格键跨行 3/4）；
 *   缺省为字母键盘完成键（列 = 行 1 下屏键列，T28 对齐口径）
 * @param initialCell 初始焦点格位（缺省键区左上角）
 */
class FocusNavigator(
    rows: List<KeyRow> = KeyboardLayout.rows(LanguageMode.ENGLISH),
    overlayCells: Map<String, List<Pair<Int, Int>>> = mapOf(
        KeyboardLayout.doneKey.id to KeyboardLayout.doneKeyCells,
    ),
    private val initialCell: Pair<Int, Int> = 1 to 1,
) {

    /** 格位 → 键 id：row(1..4) × col(1..12)，每格至多一键。 */
    private val grid: Map<Int, Map<Int, String>>

    /** 键 id → 占据格位列表（跨行键有两个）。 */
    private val positions: Map<String, List<Pair<Int, Int>>>

    init {
        // 行内键：按画板步进累计 x 原点（1080p px，与 KeyboardLayoutTest.columnRhythmMatchesArtboard 同口径）
        val originToCol = mutableMapOf<Int, Int>()
        val gridBuilder = mutableMapOf<Int, MutableMap<Int, String>>()
        val positionsBuilder = mutableMapOf<String, MutableList<Pair<Int, Int>>>()
        rows.forEachIndexed { rowIndex, row ->
            val rowNo = rowIndex + 1
            var x = 0
            for (cell in row.cells) {
                when (cell) {
                    is Cell.KeyCell -> {
                        val col = originToCol.getOrPut(x) { originToCol.size + 1 }
                        val occupied = gridBuilder.getOrPut(rowNo) { mutableMapOf() }.put(col, cell.key.id)
                        check(occupied == null) { "格位 ($rowNo, $col) 冲突：${cell.key.id} 与 $occupied" }
                        positionsBuilder.getOrPut(cell.key.id) { mutableListOf() }.add(rowNo to col)
                        x += KEY_PX + KEY_GAP_PX
                    }
                    Cell.Spacer -> x += SPACER_PX + KEY_GAP_PX
                    // 空格位：占一格无键（格网留空，导航越空直达最近实键；T8 半角行 4 / T58 语义）
                    Cell.EmptyCell -> x += KEY_PX + KEY_GAP_PX
                }
            }
        }
        // 跨行浮层键：格位显式声明（字母键盘完成键声明源自 KeyboardLayout.doneKeyCells，
        // 与行内推导列的一致性由 KeyboardLayoutTest/调用方锁定）
        for ((keyId, cells) in overlayCells) {
            check(keyId !in positionsBuilder) { "浮层键 $keyId 不应出现在行内键位中" }
            for ((rowNo, col) in cells) {
                val occupied = gridBuilder.getOrPut(rowNo) { mutableMapOf() }.put(col, keyId)
                check(occupied == null) { "浮层键 $keyId 格位 ($rowNo, $col) 与行内键 $occupied 冲突" }
                positionsBuilder.getOrPut(keyId) { mutableListOf() }.add(rowNo to col)
            }
        }
        grid = gridBuilder
        positions = positionsBuilder
    }

    /** 键盘显示时的初始焦点键（缺省键区左上角第一键）。 */
    fun initialKey(): String = requireNotNull(keyAt(initialCell.first, initialCell.second)) {
        "初始格位 (${initialCell.first}, ${initialCell.second}) 无键"
    }

    /** 指定格位的键 id；无键返回 null。 */
    fun keyAt(row: Int, col: Int): String? = grid[row]?.get(col)

    /** 键占据的格位列表（done 跨两行格位）。未知键返回空列表。 */
    fun positionsOf(keyId: String): List<Pair<Int, Int>> = positions[keyId].orEmpty()

    /**
     * 焦点导航：当前键沿方向移动到该行/列线上的最近实键。
     * 空格位（EmptyCell，无键格）越空直达，不越任何实键（T58：半角符号 [0]↔[*] 左右直达）；
     * 扫到自身占据格位（跨行键另一格）即停返回 null；表缘（该方向无更多格位/实键）返回 null，
     * 调用方消费事件并保持焦点不动。
     */
    fun next(currentKeyId: String, direction: FocusDirection): String? {
        // 完成键以顶行格位为锚（跨两行高键，FR-A10）
        val (row, col) = positionsOf(currentKeyId).minByOrNull { it.first }
            ?: return null
        val dRow = if (direction == FocusDirection.UP) -1 else if (direction == FocusDirection.DOWN) 1 else 0
        val dCol = if (direction == FocusDirection.LEFT) -1 else if (direction == FocusDirection.RIGHT) 1 else 0
        var r = row + dRow
        var c = col + dCol
        while (r in 1..GRID_ROWS && c in 1..GRID_COLS) {
            when (val target = keyAt(r, c)) {
                // 空格位：格网留空，越空继续寻路（T58 半角行 4 col2/3）
                null -> {
                    r += dRow
                    c += dCol
                }
                // 仍是自身（跨行键另一格，如完成键 DOWN）：视觉未离开自身 → 不动
                currentKeyId -> return null
                else -> return target
            }
        }
        return null
    }

    companion object {
        // 画板几何常量（1080p px；与 dimens.xml Token 的 ÷2 换算基准一致，模型侧独立锁定，
        // 列节奏一致性由 KeyboardLayoutTest.columnRhythmMatchesArtboard 交叉验证。
        // T68 · 决策 58 起视觉基准 = designs/20261114：键 96 + 键距 10、spacer 48 + 键距 10）
        private const val KEY_PX = 96
        private const val KEY_GAP_PX = 10
        private const val SPACER_PX = 48

        // 格网规模（画板① 4 行 × 12 列；越空寻路与表缘判定的边界，T58。
        // GRID_COLS 开放：服务层最右列判定用——T67 · 决策 56 最右列全部按键按[右]直达远程输入）
        private const val GRID_ROWS = 4
        const val GRID_COLS = 12
    }
}
