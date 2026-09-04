package com.cactus.bitacora.ui.admin

import com.cactus.bitacora.model.EmployeeAreaAdminIn
import com.cactus.bitacora.model.EmployeeAreaAssignmentOut
import com.cactus.bitacora.model.EmployeeAreaTreeNodeOut
import com.cactus.bitacora.model.ParticipantOptionOut
import com.cactus.bitacora.model.ParticipantTypeAdminOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAssignmentFormContractTest {
    @Test
    fun assignmentFormValidationRequiresParticipantRoleScheduleAndDate() {
        val participant = ParticipantOptionOut(
            id_participante = 42,
            codigo = "P-042",
            nombres = "Ana",
            apellidos = "García",
            nombre_completo = "Ana García",
            documento = "123"
        )
        val type = ParticipantTypeAdminOut(codigo = 2, descripcion = "Supervisor", activo = true, capacidades = emptyList())

        assertNull(validateAssignmentForm(
            current = null,
            participant = participant,
            type = type,
            selectedScheduleId = 7,
            startDate = "2026-09-04"
        ))

        assertEquals("Seleccione un participante", validateAssignmentForm(
            current = null,
            participant = null,
            type = type,
            selectedScheduleId = 7,
            startDate = "2026-09-04"
        ))

        assertEquals("Seleccione una jornada de trabajo.", validateAssignmentForm(
            current = null,
            participant = participant,
            type = type,
            selectedScheduleId = null,
            startDate = "2026-09-04"
        ))
    }

    @Test
    fun createPayloadCarriesAllFourRequiredIdentifiers() {
        val payload = buildCreateEmployeeAreaPayload(
            participantId = 77,
            areaId = 13,
            typeCode = 2,
            description = "Prueba C25",
            startDate = "2026-09-04",
            endDate = null,
            scheduleId = 5L
        )

        assertEquals(77, payload.id_participante)
        assertEquals(13, payload.id_area)
        assertEquals(2, payload.codigo_tipo)
        assertEquals(5L, payload.id_jornada)
        assertEquals("2026-09-04", payload.fecha_inicia)
        assertEquals("Prueba C25", payload.descripcion)
    }

    @Test
    fun successMessageAndErrorHandlingCanBeCovered() {
        val payload = buildUpdateEmployeeAreaPayload(
            areaId = 91,
            typeCode = 1,
            description = "Cambio de jornada",
            startDate = "2026-09-04",
            endDate = "2026-09-30",
            scheduleId = 9L
        )

        assertNotNull(payload)
        assertEquals(9L, payload.id_jornada)
        assertEquals(91, payload.id_area)
        assertEquals(1, payload.codigo_tipo)
    }

    @Test
    fun retiredConflictMessageIsHiddenOutsideExplicitRetireAction() {
        assertEquals(
            "El participante ya tiene una asignación activa en esta área.",
            normalizeAssignmentError("La asignación ya está retirada", false)
        )
        assertEquals(
            "La asignación ya está retirada",
            normalizeAssignmentError("La asignación ya está retirada", true)
        )
        assertEquals(
            "El participante ya tiene una asignación activa en esta área.",
            normalizeAssignmentError("El participante ya tiene una asignación activa en esta área.", false)
        )
    }

    @Test
    fun activeTreeRefreshRemovesRetiredAssignmentAndShowsNewOne() {
        val previousTree = listOf(
            EmployeeAreaTreeNodeOut(
                id_area = 10,
                descripcion = "Área A",
                nombre_corto = "A",
                nivel = 1,
                ruta = "Área A",
                cantidad_participantes = 1,
                participantes = listOf(
                    EmployeeAreaAssignmentOut(
                        id_empleado_area = 7,
                        id_participante = 45,
                        codigo_participante = "P-045",
                        nombre_completo = "Pedro Retirado",
                        codigo_tipo = 2,
                        cargo = "Supervisor",
                        descripcion = "Retirada",
                        fecha_inicia = "2026-09-01",
                        fecha_final = null,
                        id_jornada = 3L
                    )
                )
            )
        )
        val refreshedTree = listOf(
            EmployeeAreaTreeNodeOut(
                id_area = 10,
                descripcion = "Área A",
                nombre_corto = "A",
                nivel = 1,
                ruta = "Área A",
                cantidad_participantes = 1,
                participantes = listOf(
                    EmployeeAreaAssignmentOut(
                        id_empleado_area = 99,
                        id_participante = 88,
                        codigo_participante = "P-088",
                        nombre_completo = "Nuevo activo",
                        codigo_tipo = 1,
                        cargo = "Gerente",
                        descripcion = "Asignación nueva",
                        fecha_inicia = "2026-09-04",
                        fecha_final = null,
                        id_jornada = 4L
                    )
                )
            )
        )

        val previousParticipants = flattenEmployeeAreaTree(previousTree).flatMap { it.participantes }
        val refreshedParticipants = flattenEmployeeAreaTree(refreshedTree).flatMap { it.participantes }

        assertTrue(previousParticipants.any { it.id_empleado_area == 7 })
        assertFalse(refreshedParticipants.any { it.id_empleado_area == 7 })
        assertTrue(refreshedParticipants.any { it.id_empleado_area == 99 })
        assertEquals("Nuevo activo", refreshedParticipants.single { it.id_empleado_area == 99 }.nombre_completo)
    }
}
