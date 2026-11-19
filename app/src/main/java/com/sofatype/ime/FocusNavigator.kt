package com.sofatype.ime

enum class FocusDirection { LEFT, RIGHT, UP, DOWN }

class FocusNavigator(
    rows: List<KeyRow> = KeyboardLayout.rows(LanguageMode.ENGLISH),
    overlayCells: Map<String, List<Pair<Int, Int>>> = mapOf(
        KeyboardLayout.doneKey.id to KeyboardLayout.doneKeyCells,
    ),
    private val initialCell: Pair<Int, Int> = 1 to 1,
) {

    private val grid: Map<Int, Map<Int, String>>

    private val positions: Map<String, List<Pair<Int, Int>>>

    init {

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

                    Cell.EmptyCell -> x += KEY_PX + KEY_GAP_PX
                }
            }
        }

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

    fun initialKey(): String = requireNotNull(keyAt(initialCell.first, initialCell.second)) {
        "初始格位 (${initialCell.first}, ${initialCell.second}) 无键"
    }

    fun keyAt(row: Int, col: Int): String? = grid[row]?.get(col)

    fun positionsOf(keyId: String): List<Pair<Int, Int>> = positions[keyId].orEmpty()

    fun next(currentKeyId: String, direction: FocusDirection): String? {

        val (row, col) = positionsOf(currentKeyId).minByOrNull { it.first }
            ?: return null
        val dRow = if (direction == FocusDirection.UP) -1 else if (direction == FocusDirection.DOWN) 1 else 0
        val dCol = if (direction == FocusDirection.LEFT) -1 else if (direction == FocusDirection.RIGHT) 1 else 0
        var r = row + dRow
        var c = col + dCol
        while (r in 1..GRID_ROWS && c in 1..GRID_COLS) {
            when (val target = keyAt(r, c)) {

                null -> {
                    r += dRow
                    c += dCol
                }

                currentKeyId -> return null
                else -> return target
            }
        }
        return null
    }

    companion object {

        private const val KEY_PX = 96
        private const val KEY_GAP_PX = 10
        private const val SPACER_PX = 48

        private const val GRID_ROWS = 4
        const val GRID_COLS = 12
    }
}
