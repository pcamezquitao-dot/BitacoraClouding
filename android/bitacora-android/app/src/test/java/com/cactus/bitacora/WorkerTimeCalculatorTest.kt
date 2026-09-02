package com.cactus.bitacora

import com.cactus.bitacora.data.calculateWorkerTime
import com.cactus.bitacora.model.WorkerEventOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkerTimeCalculatorTest {
    @Test fun pairsEventsAndAttributesAnOvernightShiftToEntryDay() {
        val result = calculateWorkerTime(
            participantId = 10,
            year = 2026,
            month = 8,
            source = listOf(
                WorkerEventOut("in", 29765040, 4, "in"),
                WorkerEventOut("out", 29765160, 5, "out")
            )
        )
        assertEquals(120, result.dias[3].minutos_trabajados)
        assertEquals(0, result.dias[4].minutos_trabajados)
        assertTrue(result.dias[3].eventos.all { it.utilizado })
    }
}
