package com.cactus.bitacora.ui.admin

import com.cactus.bitacora.model.CalendarTreeNodeOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarAdminPolicyTest {
    private fun node(
        id: Long,
        level: String,
        children: List<CalendarTreeNodeOut> = emptyList()
    ) = CalendarTreeNodeOut(
        id_periodo = id,
        nivel = level,
        codigo = id.toString(),
        nombre = level,
        fecha_inicio = "2026-01-01",
        fecha_fin = "2026-01-01",
        es_festivo = false,
        hijos = children
    )

    @Test
    fun onlyDayCanBeEdited() {
        assertFalse(canEditCalendarNode(node(1, "QUINQUENIO")))
        assertFalse(canEditCalendarNode(node(2, "ANIO")))
        assertFalse(canEditCalendarNode(node(3, "MES")))
        assertTrue(canEditCalendarNode(node(4, "DIA")))
    }

    @Test
    fun holidayRequiresNonBlankName() {
        assertFalse(validCalendarHolidayName(true, "   "))
        assertTrue(validCalendarHolidayName(true, "Independencia"))
        assertTrue(validCalendarHolidayName(false, ""))
    }

    @Test
    fun collapsedBranchesDoNotExposeTheirChildren() {
        val day = node(4, "DIA")
        val month = node(3, "MES", listOf(day))
        val year = node(2, "ANIO", listOf(month))
        val fiveYears = node(1, "QUINQUENIO", listOf(year))

        assertEquals(listOf(1L), visibleCalendarNodeIds(listOf(fiveYears), emptySet()))
        assertEquals(
            listOf(1L, 2L, 3L),
            visibleCalendarNodeIds(listOf(fiveYears), setOf(1L, 2L))
        )
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            visibleCalendarNodeIds(listOf(fiveYears), setOf(1L, 2L, 3L))
        )
    }
}
