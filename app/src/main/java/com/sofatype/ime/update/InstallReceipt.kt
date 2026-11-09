// T46 附加（T21 口径收口）· 安装回执状态 → 处置决策纯模型：
// PackageInstaller 回执 EXTRA_STATUS ∈ {0=SUCCESS, 1=FAILURE, 2=PENDING_USER_ACTION}（API 常量，
// 本文件以字面量保持无 Android 依赖）；非 0/2 的一切取值（含厂商变形码与子状态）归入「失败」。
// 失败分支必须切 UI 失败态（红点 + 原因文案 + 重试按钮，T21「失败可重试」口径）——
// Round 2 真机实证缺口：INSTALL_FAILED_USER_RESTRICTED 回执仅写 log，界面滞留「已提交系统安装」。
package com.sofatype.ime.update

/** 安装回执处置决策（纯 Kotlin，单测锁定映射）。 */
enum class InstallReceiptDecision {
    /** STATUS_PENDING_USER_ACTION：转交系统确认框（未知来源授权弹窗）。 */
    PENDING_USER_ACTION,

    /** STATUS_SUCCESS：安装成功回执（记录即可）。 */
    SUCCESS,

    /** 其余一切状态：失败，切 UI 失败态并携带状态码。 */
    FAILURE,
}

object InstallReceipt {

    /** EXTRA_STATUS 状态常量（android.app.PackageInstaller API 21+ 稳定值）。 */
    const val STATUS_SUCCESS: Int = 0
    const val STATUS_FAILURE: Int = 1
    const val STATUS_PENDING_USER_ACTION: Int = 2

    /** 回执状态 → 处置决策：仅 0/2 有专属分支，其余（含负值与厂商变形码）一律失败。 */
    fun decisionFor(status: Int): InstallReceiptDecision = when (status) {
        STATUS_SUCCESS -> InstallReceiptDecision.SUCCESS
        STATUS_PENDING_USER_ACTION -> InstallReceiptDecision.PENDING_USER_ACTION
        else -> InstallReceiptDecision.FAILURE
    }
}
