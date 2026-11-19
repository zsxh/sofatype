package com.sofatype.ime

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteCursorGeometryTest {

    @Test
    fun cursorCentersVerticallyInLineBox() {

        assertEquals(6, RemoteCursorGeometry.centeredTop(top = 0, bottom = 34, cursorHeight = 22))
    }

    @Test
    fun offsetShiftsWithLineBoxTop() {

        assertEquals(46, RemoteCursorGeometry.centeredTop(top = 40, bottom = 74, cursorHeight = 22))
    }

    @Test
    fun oddRemainderRoundsDownwardLikeIntDivision() {

        assertEquals(6, RemoteCursorGeometry.centeredTop(top = 0, bottom = 33, cursorHeight = 21))
    }

    @Test
    fun cursorTallerThanBoxGoesNegativeTop() {

        assertEquals(-4, RemoteCursorGeometry.centeredTop(top = 0, bottom = 14, cursorHeight = 22))
    }

    @Test
    fun cursorExactBoxHeightAnchorsAtTop() {
        assertEquals(0, RemoteCursorGeometry.centeredTop(top = 0, bottom = 22, cursorHeight = 22))
    }
}
