package com.sofatype.ime.dicts

object DictPackEndpoint {

    const val OFFICIAL_CATALOG_URL: String = ""

    @Volatile
    private var debugOverride: String? = null

    fun catalogUrl(): String = debugOverride?.takeIf { it.isNotBlank() } ?: OFFICIAL_CATALOG_URL

    fun setDebugOverride(value: String?) {
        debugOverride = value?.takeIf { it.isNotBlank() }
    }
}
