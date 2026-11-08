// T20 · 版本展示文本（FR-D3 / 决策 19）：「当前版本 v<versionName> (<versionCode>)」的值段格式。
// 纯 Kotlin 无 Android 依赖，供 SettingsActivity 与 JUnit 单测共用。
package com.sofatype.ime

object VersionFormatter {

    // versionName 为 PackageInfo 可空字段，本工程构建配置恒有值；null 按空串渲染，不抛异常
    fun format(versionName: String?, versionCode: Int): String = "v${versionName.orEmpty()} ($versionCode)"
}
