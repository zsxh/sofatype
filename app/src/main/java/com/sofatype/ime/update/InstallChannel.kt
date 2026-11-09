// T47 · 决策 36：安装提交渠道纯模型（无 Android 依赖，边界单测锁定）。
// 会话直装口径在 Android 6 基线废止：API 23 基线下载确认后转交系统安装器（UI 渠道，
// install Intent + file://，issue T47 改动清单口径）；PackageInstaller 会话分支保留为
// API 24+ 分支（决策 36），行为不回归（T47 验收④）。
package com.sofatype.ime.update

/** 安装提交渠道（决策 36）。 */
enum class InstallChannel {

    /** PackageInstaller 会话提交（API 24+ 既有分支，决策 36 保留；有会话回执）。 */
    SESSION,

    /** 系统安装器 install Intent 转交（API 23 基线 UI 渠道；无会话回执，回前台按 versionCode 判定）。 */
    UI_INTENT,
}

object InstallChannels {

    /** 会话分支下界：决策 36「保留为 API 24+ 分支」——API 24 起走会话，23 及以下走 UI 渠道。 */
    const val SESSION_MIN_SDK = 24

    /** 按设备 API 级别取安装提交渠道。 */
    fun channelFor(sdkInt: Int): InstallChannel =
        if (sdkInt >= SESSION_MIN_SDK) InstallChannel.SESSION else InstallChannel.UI_INTENT
}
