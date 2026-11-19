package com.sofatype.ime.update

enum class InstallReceiptDecision {

    PENDING_USER_ACTION,

    SUCCESS,

    FAILURE,
}

object InstallReceipt {

    const val STATUS_SUCCESS: Int = 0
    const val STATUS_FAILURE: Int = 1
    const val STATUS_PENDING_USER_ACTION: Int = 2

    fun decisionFor(status: Int): InstallReceiptDecision = when (status) {
        STATUS_SUCCESS -> InstallReceiptDecision.SUCCESS
        STATUS_PENDING_USER_ACTION -> InstallReceiptDecision.PENDING_USER_ACTION
        else -> InstallReceiptDecision.FAILURE
    }
}
