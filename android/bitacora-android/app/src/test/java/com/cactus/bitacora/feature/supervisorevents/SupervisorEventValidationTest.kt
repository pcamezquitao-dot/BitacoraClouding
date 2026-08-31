package com.cactus.bitacora.feature.supervisorevents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SupervisorEventValidationTest {
    @Test
    fun `only the three C08 types are exposed`() {
        assertEquals(setOf(1, 2, 7), SupervisorEventType.entries.map { it.backendId }.toSet())
    }

    @Test
    fun `permission duration is calculated from same-day hours`() {
        val result = validateSupervisorEvent(draft(SupervisorEventType.PERMISSION))
        assertEquals(90, result.durationMinutes)
        assertEquals(result.startDate, result.endDate)
    }

    @Test
    fun `overtime requires final hour after initial hour`() {
        val error = assertThrows(SupervisorEventValidationException::class.java) {
            validateSupervisorEvent(
                draft(SupervisorEventType.OVERTIME).copy(startTime = "18:00", endTime = "17:59")
            )
        }
        assertTrue(error.message.orEmpty().contains("posterior"))
    }

    @Test
    fun `incapacity accepts date range and does not calculate minutes`() {
        val result = validateSupervisorEvent(
            draft(SupervisorEventType.DISABILITY).copy(
                startDate = "2026-08-30",
                endDate = "2026-09-02",
                startTime = "",
                endTime = ""
            )
        )
        assertEquals("2026-09-02", result.endDate.toString())
        assertNull(result.durationMinutes)
    }

    @Test
    fun `incapacity rejects inverted dates`() {
        assertThrows(SupervisorEventValidationException::class.java) {
            validateSupervisorEvent(
                draft(SupervisorEventType.DISABILITY).copy(
                    startDate = "2026-09-02",
                    endDate = "2026-08-30"
                )
            )
        }
    }

    @Test
    fun `scope requires exact active participant and area pair`() {
        val scope = setOf(15 to 1, 16 to 2)
        assertTrue(isParticipantAuthorized(15, 1, scope))
        assertFalse(isParticipantAuthorized(15, 2, scope))
        assertFalse(isParticipantAuthorized(99, 1, scope))
    }

    private fun draft(type: SupervisorEventType) = SupervisorEventDraft(
        supervisorCode = "P0002",
        participantId = 15,
        areaId = 1,
        type = type,
        startDate = "2026-08-30",
        endDate = "2026-08-30",
        startTime = "08:00",
        endTime = "09:30",
        observations = "Prueba C08"
    )
}
