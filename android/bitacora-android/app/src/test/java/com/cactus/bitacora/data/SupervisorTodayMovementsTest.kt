package com.cactus.bitacora.data

import com.cactus.bitacora.model.SupervisorTodayMovementOut
import org.junit.Assert.assertEquals
import org.junit.Test

class SupervisorTodayMovementsTest {
    @Test
    fun combinesPendingAndRemoteWithoutDuplicateAndFiltersSupervisorAndDay() {
        val pending = movement("same", 2, 100, "ENTRADA", "P0037", "PENDIENTE_CREAR")
        val remoteDuplicate = pending.copy(id_bitacora = 115, sync_status = "SINCRONIZADO")
        val exit = movement("exit", 2, 110, "SALIDA", "P0038", "ERROR")
        val otherSupervisor = movement("other", 3, 115, "ENTRADA", "P0039", "SINCRONIZADO")
        val yesterday = movement("yesterday", 2, 99, "ENTRADA", "P0040", "SINCRONIZADO")

        val result = mergeSupervisorTodayMovements(
            supervisorId = 2,
            startMinute = 100,
            endMinute = 120,
            local = listOf(pending, exit),
            remote = listOf(remoteDuplicate, otherSupervisor, yesterday)
        )

        assertEquals(listOf("exit", "same"), result.map { it.client_uuid })
        assertEquals(listOf("SALIDA", "ENTRADA"), result.map { it.tipo })
        assertEquals("PENDIENTE_CREAR", result.last().sync_status)
    }

    private fun movement(
        uuid: String,
        supervisor: Int,
        timestamp: Int,
        type: String,
        participantCode: String,
        status: String
    ) = SupervisorTodayMovementOut(
        id_bitacora = timestamp,
        id_participante = timestamp,
        id_supervisor = supervisor,
        id_area = 2,
        tipo = type,
        timestamp_min = timestamp,
        client_uuid = uuid,
        codigo_participante = participantCode,
        nombre_completo = "Participante",
        area = "Finca1",
        sync_status = status
    )
}
