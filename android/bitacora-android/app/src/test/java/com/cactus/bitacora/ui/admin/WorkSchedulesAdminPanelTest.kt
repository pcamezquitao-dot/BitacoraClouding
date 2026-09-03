package com.cactus.bitacora.ui.admin

import com.cactus.bitacora.model.WorkScheduleDetailIn
import com.cactus.bitacora.model.WorkScheduleIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class WorkSchedulesAdminPanelTest {
    private fun detail(day: Int, start: Int? = null, end: Int? = null, number: Int = 1, working: Boolean = true) =
        WorkScheduleDetailIn(null, day, number, working, start, end)

    private fun details() = (1..5).map { detail(it, 480, 960) } +
        listOf(detail(6, working = false), detail(7, working = false))

    @Test fun labelAndWeeklyTotalFollowContract() {
        assertEquals("Jornadas de trabajo", WORK_SCHEDULES_LABEL)
        assertEquals(2400, scheduleWeeklyMinutes(details()))
    }

    @Test fun activeScheduleRequiresItsWeeklyObjective() {
        val valid = WorkScheduleIn("JOR-40", "Cuarenta horas", 2400, vigencia_desde = "2026-08-01", activo = true, detalles = details())
        assertNull(scheduleFormError(valid))
        assertNotNull(scheduleFormError(valid.copy(minutos_objetivo_semana = 2520)))
    }

    @Test fun noControlRequiresZeroWithoutDetails() {
        val valid = WorkScheduleIn("SIN_HORARIO", "Sin horario", 0, vigencia_desde = "2026-08-01", activo = true, aplica_control_horario = false)
        assertNull(scheduleFormError(valid))
        assertNotNull(scheduleFormError(valid.copy(minutos_objetivo_semana = 15)))
    }

    @Test fun timeSelectorsOnlyOfferQuarterMinutes() {
        assertEquals(listOf(0, 15, 30, 45), SCHEDULE_QUARTER_MINUTES)
    }

    @Test fun weeklyObjectiveConvertsBetweenHoursMinutesAndStoredMinutes() {
        assertEquals(2550, weeklyObjectiveMinutes(42, 30))
        assertEquals(42, weeklyObjectiveHours(2550))
        assertEquals(30, weeklyObjectiveMinutePart(2550))
    }

    @Test fun listActionsKeepTheParticipantsViewAccessibilityAndExposeScheduleState() {
        assertEquals("Ver jornada", WORK_SCHEDULE_VIEW_CONTENT_DESCRIPTION)
        assertEquals("Jornada activa. Inactivar jornada", workScheduleStatusContentDescription(true))
        assertEquals("Jornada inactiva. Activar jornada", workScheduleStatusContentDescription(false))
    }
}
