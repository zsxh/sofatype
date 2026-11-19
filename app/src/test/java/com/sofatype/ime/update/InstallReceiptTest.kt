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

        assertEquals(InstallReceiptDecision.FAILURE, InstallReceipt.decisionFor(7))

        assertEquals(InstallReceiptDecision.FAILURE, InstallReceipt.decisionFor(3))
        assertEquals(InstallReceiptDecision.FAILURE, InstallReceipt.decisionFor(4))
        assertEquals(InstallReceiptDecision.FAILURE, InstallReceipt.decisionFor(-1))
    }

    @Test
    fun statusConstantsMatchPackageInstallerApiValues() {

        assertEquals(0, InstallReceipt.STATUS_SUCCESS)
        assertEquals(1, InstallReceipt.STATUS_FAILURE)
        assertEquals(2, InstallReceipt.STATUS_PENDING_USER_ACTION)
    }
}
