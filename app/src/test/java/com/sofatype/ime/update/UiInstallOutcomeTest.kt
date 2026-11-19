package com.sofatype.ime.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiInstallReceiptTest {

    @Test
    fun versionCodeIncreasedMeansUpdated() {
        assertEquals(UiInstallDecision.UPDATED, UiInstallReceipt.decisionFor(1, 2))
        assertEquals(UiInstallDecision.UPDATED, UiInstallReceipt.decisionFor(1, 100))
        assertEquals(UiInstallDecision.UPDATED, UiInstallReceipt.decisionFor(63, 64))
    }

    @Test
    fun versionCodeUnchangedMeansNotUpdated() {

        assertEquals(UiInstallDecision.NOT_UPDATED, UiInstallReceipt.decisionFor(1, 1))
    }

    @Test
    fun versionCodeDecreasedMeansNotUpdated() {

        assertEquals(UiInstallDecision.NOT_UPDATED, UiInstallReceipt.decisionFor(2, 1))
    }

    @Test
    fun invalidBeforeCodeMeansNotUpdated() {

        assertEquals(UiInstallDecision.NOT_UPDATED, UiInstallReceipt.decisionFor(0, 5))
        assertEquals(UiInstallDecision.NOT_UPDATED, UiInstallReceipt.decisionFor(-1, 5))
    }
}

class UiInstallMarkerTest {

    @Test
    fun encodeProducesStablePayload() {
        assertEquals("before=1", UiInstallMarker.encode(1))
        assertEquals("before=63", UiInstallMarker.encode(63))
    }

    @Test
    fun decodeRoundTripsEncode() {
        assertEquals(1, UiInstallMarker.decode(UiInstallMarker.encode(1)))
        assertEquals(63, UiInstallMarker.decode(UiInstallMarker.encode(63)))
    }

    @Test
    fun decodeRejectsNullOrMalformedPayload() {
        assertNull(UiInstallMarker.decode(null))
        assertNull(UiInstallMarker.decode(""))
        assertNull(UiInstallMarker.decode("nonsense"))
        assertNull(UiInstallMarker.decode("before="))
        assertNull(UiInstallMarker.decode("before=abc"))

        assertNull(UiInstallMarker.decode("xxbefore=1"))
    }

    @Test
    fun decodeRejectsNonPositiveVersionCode() {
        assertNull(UiInstallMarker.decode("before=0"))
        assertNull(UiInstallMarker.decode("before=-3"))
    }
}
