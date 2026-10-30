package com.example.mts

import com.example.mts.ui.common.addDays
import com.example.mts.ui.common.formatQuantity
import com.example.mts.ui.common.isoToMillis
import com.example.mts.ui.common.millisToIso
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    @Test
    fun quantitiesDropNeedlessDecimals() {
        assertEquals("2", formatQuantity(2.0))
        assertEquals("0.75", formatQuantity(0.75))
        assertEquals("1.33", formatQuantity(4.0 / 3.0))
    }

    @Test
    fun servingScaleRoundTrips() {
        assertEquals("3", formatQuantity(2.0 * (3.0 / 2.0)))
    }

    @Test
    fun datesCrossMonthBoundaries() {
        assertEquals("2026-11-01", addDays("2026-10-31", 1))
        assertEquals("2026-12-28", addDays("2027-01-04", -7))
    }

    @Test
    fun isoDatesRoundTrip() {
        assertEquals("2026-11-05", millisToIso(isoToMillis("2026-11-05")!!))
    }
}
