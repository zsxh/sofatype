// T21 · 更新检查失败原因枚举（FR-D4 三态中的「失败」态）。
// 纯 Kotlin，供解析 / HTTP / 检查编排 / 下载 / 安装准备各层统一引用；
// 展示文案映射在 SettingsActivity（资源 strings），本文件不含 UI 文案。
package com.sofatype.ime.update

enum class UpdateFailureReason {
    /** 更新源接口地址未配置（决策 17：地址可配置；正式地址后补，置空即此因）。 */
    EMPTY_ENDPOINT,

    /** 检查接口网络失败（连接 / 读写 IO）。 */
    CHECK_NETWORK,

    /** 检查接口返回非 2xx 状态码（状态码经 CheckResult.Failed.httpStatus 携带）。 */
    CHECK_HTTP,

    /** 接口返回内容不是合法的 GitHub Releases JSON（决策 17 字段口径）。 */
    PARSE,

    /** 选定发布条目内没有 .apk 下载资产。 */
    NO_APK_ASSET,

    /** 下载阶段网络失败（连接 / 中断 / 非 2xx，状态码经 httpStatus 携带）。 */
    DOWNLOAD_NETWORK,

    /** 下载阶段本地存储读写失败。 */
    DOWNLOAD_IO,

    /** 下载完成后向系统安装器提交会话失败。 */
    INSTALL_PREPARE,

    /** 安装回执失败（系统安装器拒绝/中断，状态码经 httpStatus 携带；T46 附加，T21「失败可重试」口径）。 */
    INSTALL_COMMIT,

    /**
     * UI 渠道（API 23 基线，决策 36）安装未完成：安装器取消或安装失败，回设置页 versionCode
     * 未升（T47；无会话回执，按版本比对口径归失败可重试）。
     */
    UI_INSTALL_NOT_UPDATED,
}
