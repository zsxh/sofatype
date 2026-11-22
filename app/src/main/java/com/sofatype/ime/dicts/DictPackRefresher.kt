package com.sofatype.ime.dicts

object DictPackRefresher {

    @Volatile
    private var listener: (() -> Unit)? = null

    fun register(listener: (() -> Unit)?) {
        this.listener = listener
    }

    fun notifyPacksChanged() {
        listener?.invoke()
    }
}
