package com.cactus.bitacora.data

import com.cactus.bitacora.model.ParticipantAdminIn
import com.cactus.bitacora.model.ParticipantAdminOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParticipantSyncContractTest {
    private val remote = ParticipantAdminOut(
        2, 1, "00000002", "P0002", "Nombre2", "Apellido2",
        "1985-03-16", "F", null, null, null, null, true
    )

    @Test fun reducedLocalSnapshotIsHydratedBeforePut() {
        val local = ParticipantAdminIn(
            1, "00000002", "P0002", "Nombre editado", null, null, null,
            null, null, null, null
        )
        val payload = local.normalizedNulls().completeForServer(remote)
        assertEquals("P0002", payload.identificacion_participante)
        assertEquals("Nombre editado", payload.nombre)
        assertEquals("Apellido2", payload.apellido)
        assertEquals("1985-03-16", payload.fecha_nacimiento)
        assertEquals("F", payload.sexo)
        assertNull(payload.fecha_entrada)
        assertNull(payload.observaciones)
    }

    @Test fun knownRequiredValuesAreNotOverwritten() {
        val local = ParticipantAdminIn(
            1, "00000002", "P0002", "Nombre2", "Apellido editado",
            "1990-01-02", "M", null, null, "nota", null
        )
        val payload = local.normalizedNulls().completeForServer(remote)
        assertEquals("Apellido editado", payload.apellido)
        assertEquals("1990-01-02", payload.fecha_nacimiento)
        assertEquals("M", payload.sexo)
        assertEquals("nota", payload.observaciones)
    }
}
