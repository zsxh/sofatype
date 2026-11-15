// T72 · 远程输入连接代号 · 设备类型（决策 61）：类型判定在手机端完成、随 hello 上报
// deviceType 字段（微信→苹果→安卓→浏览器命中即停；TV 不解析 UA）。
// TV 端只按本枚举消费 code 字符串并映射展示标签（[label]设备 + 两位序号）；
// 缺失 / 未知取值按浏览器兜底（浏览器是手机端判定链的命中即停终点，旧客户端
// 未携带 deviceType 时同口径，PRD 第 7 章「旧客户端未携带按匿名处理」）。
// 纯 Kotlin，不依赖 Android 类，可单元测试锁定映射。
package com.sofatype.ime.remote

/** 连接代号的设备类型（决策 61：hello.deviceType 的合法取值与展示标签）。 */
enum class DeviceType(val code: String, val label: String) {
    /** 微信内置浏览器（判定链首位）。 */
    WECHAT("wechat", "微信"),

    /** 苹果设备（iPhone / iPad / iPod / Mac）。 */
    APPLE("apple", "苹果"),

    /** 安卓设备（非微信浏览器）。 */
    ANDROID("android", "安卓"),

    /** 浏览器（命中即停链终点 / 旧客户端兜底）。 */
    BROWSER("browser", "浏览器"),
    ;

    companion object {
        /** hello.deviceType → 枚举；null / 非法取值一律回浏览器（兜底口径，见类注释）。 */
        fun fromCode(raw: String?): DeviceType =
            entries.firstOrNull { it.code == raw } ?: BROWSER
    }
}
