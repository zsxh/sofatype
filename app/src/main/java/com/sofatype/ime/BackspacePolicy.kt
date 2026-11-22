package com.sofatype.ime

object BackspacePolicy {

    enum class Target { SELECTION, BEFORE, AFTER, NONE }

    fun resolve(selectionLength: Int, beforeLength: Int, afterLength: Int): Target = when {
        selectionLength > 0 -> Target.SELECTION
        beforeLength > 0 -> Target.BEFORE
        afterLength > 0 -> Target.AFTER
        else -> Target.NONE
    }
}
