package com.sofatype.ime.update

enum class UiInstallDecision {

    UPDATED,

    NOT_UPDATED,
}

object UiInstallReceipt {

    fun decisionFor(versionCodeBefore: Int, versionCodeAfter: Int): UiInstallDecision =
        if (versionCodeBefore > 0 && versionCodeAfter > versionCodeBefore) {
            UiInstallDecision.UPDATED
        } else {
            UiInstallDecision.NOT_UPDATED
        }
}

object UiInstallMarker {

    private const val PREFIX = "before="

    fun encode(versionCodeBefore: Int): String = "$PREFIX$versionCodeBefore"

    fun decode(raw: String?): Int? {
        if (raw == null || !raw.startsWith(PREFIX)) return null
        val value = raw.substringAfter(PREFIX).toIntOrNull() ?: return null
        return value.takeIf { it > 0 }
    }
}
