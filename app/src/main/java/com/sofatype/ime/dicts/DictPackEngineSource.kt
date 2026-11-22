package com.sofatype.ime.dicts

import com.sofatype.pinyin.DictData

class DictPackEngineSource(
    private val store: DictPackStore,
    private val onPackSkip: (name: String, reason: String) -> Unit = { _, _ -> },
) {

    fun loadEnabled(): List<DictData> =
        store.installedEntries()
            .filter { store.isEnabled(it.name) }
            .mapNotNull { entry ->
                try {
                    store.loadDictData(entry.name)
                } catch (t: Throwable) {
                    onPackSkip(entry.name, t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
                    null
                }
            }
}
