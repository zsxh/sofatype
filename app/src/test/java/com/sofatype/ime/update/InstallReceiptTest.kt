// T46 附加（T21「失败可重试」口径收口）· 安装回执状态映射单元测试：
// 锁定 EXTRA_STATUS → 处置决策：0=SUCCESS、2=PENDING_USER_ACTION（转交系统确认框），
// 其余一切取值（含 1=FAILURE、厂商变形码如真机 INSTALL_FAILED_USER_RESTRICTED 的状态、
// 负值兜底）归入 FAILURE —— 失败必须切 UI 失败态（红点+原因文案+重试）。
// Round 2 真机缺口实证：失败回执仅写 log，界面滞留「已提交系统安装」（r2-t46z.xml）。
package com.sofatype.ime.update

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallReceiptTest {

    @Test
    fun successStatusMapsToSuccess() {
        assertEquals(InstallReceiptDecision.SUCCESS, InstallReceipt.decisionFor(0))
    }

    @Test
    fun pendingUserActionStatusMapsToPending() {
        assertEquals(InstallReceiptDecision.PENDING_USER_ACTION, InstallReceipt.decisionFor(2))
    }

    @Test
    fun failureStatusMapsToFailure() {
        assertEquals(InstallReceiptDecision.FAILURE, InstallReceipt.decisionFor(1))
    }

    @Test
    fun vendorVariantAndNegativeStatusesFallBackToFailure() {
        // 真机实测拒绝回执状态（INSTALL_FAILED_USER_RESTRICTED，qa Round 2 logcat 13:16:30）
        assertEquals(InstallReceiptDecision.FAILURE, InstallReceipt.decisionFor(7))
        // API 27+ 子状态码（BLOCKED/ABORTED/CONFLICT 等）在低版本不出现，出现也一律归失败
        assertEquals(InstallReceiptDecision.FAILURE, InstallReceipt.decisionFor(3))
        assertEquals(InstallReceiptDecision.FAILURE, InstallReceipt.decisionFor(4))
        assertEquals(InstallReceiptDecision.FAILURE, InstallReceipt.decisionFor(-1))
    }

    @Test
    fun statusConstantsMatchPackageInstallerApiValues() {
        // 与 android.app.PackageInstaller 常量对齐（模型用字面量保持无 Android 依赖）
        assertEquals(0, InstallReceipt.STATUS_SUCCESS)
        assertEquals(1, InstallReceipt.STATUS_FAILURE)
        assertEquals(2, InstallReceipt.STATUS_PENDING_USER_ACTION)
    }
}
