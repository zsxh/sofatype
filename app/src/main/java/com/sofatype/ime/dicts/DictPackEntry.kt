package com.sofatype.ime.dicts

data class DictPackEntry(
    val name: String,
    val version: String,
    val sha256Hex: String,
    val bytes: Long,
    val url: String,
)
