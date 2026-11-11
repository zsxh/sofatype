// T20 · 版本展示文本（FR-D3 / 决策 19；2026-11-11 户主拍板 PRD 决策 40：版本行只显示
// v<versionName>，versionCode 不再对用户展示，仅保留内部升级判定链用途）。
// 纯 Kotlin 无 Android 依赖，供 SettingsActivity 与 JUnit 单测共用。
package com.sofatype.ime

object VersionFormatter {

    // versionName 为 PackageInfo 可空字段，本工程构建配置恒有值；null 按空串渲染，不抛异常
    fun format(versionName: String?): String = "v${versionName.orEmpty()}"
}
