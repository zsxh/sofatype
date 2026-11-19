package com.sofatype.ime.remote

enum class DeviceType(val code: String, val label: String) {

    WECHAT("wechat", "微信"),

    APPLE("apple", "苹果"),

    ANDROID("android", "安卓"),

    BROWSER("browser", "浏览器"),
    ;

    companion object {

        fun fromCode(raw: String?): DeviceType =
            entries.firstOrNull { it.code == raw } ?: BROWSER
    }
}
