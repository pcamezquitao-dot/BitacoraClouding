package com.cactus.bitacora

import com.cactus.bitacora.model.WorkerDayOut
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkerChartStyleTest {
    private fun day(minutes: Int, saturday: Boolean = false, sunday: Boolean = false, holiday: Boolean = false) =
        WorkerDayOut("2026-08-03", if (saturday) 6 else if (sunday) 7 else 1,
            !sunday && !holiday, saturday, sunday, holiday, null, minutes, false, emptyList())

    private fun total(day: WorkerDayOut): Int = workerBarSegments(day).let {
        it.lightMinutes + it.ordinaryMinutes + it.additionalMinutes + it.specialMinutes
    }

    @Test fun weekdaysFollowEightHourRule() {
        assertEquals(0, total(day(0)))
        assertEquals(360, workerBarSegments(day(360)).lightMinutes)
        assertEquals(480, workerBarSegments(day(480)).ordinaryMinutes)
        assertEquals(120, workerBarSegments(day(600)).additionalMinutes)
    }

    @Test fun saturdayAndSpecialDaysUseTheirOwnRules() {
        assertEquals(240, workerBarSegments(day(240, saturday = true)).ordinaryMinutes)
        assertEquals(180, workerBarSegments(day(420, saturday = true)).additionalMinutes)
        assertEquals(540, workerBarSegments(day(540, holiday = true)).specialMinutes)
    }

    @Test fun segmentsAlwaysAddToExactTotal() {
        listOf(0, 360, 480, 481, 600).forEach { minutes -> assertEquals(minutes, total(day(minutes))) }
    }
}
