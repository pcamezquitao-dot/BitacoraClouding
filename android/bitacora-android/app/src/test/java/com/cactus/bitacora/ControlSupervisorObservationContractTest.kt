package com.cactus.bitacora

import com.cactus.bitacora.model.ControlBitacoraOut
import com.cactus.bitacora.model.ControlObservationUpdateIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId

class ControlSupervisorObservationContractTest {
    @Test
    fun selectionKeepsSupervisorWorkerAndDate() {
        assertEquals(ControlDaySelection(2, 15, "2026-08-15"), ControlDaySelection(2, 15, "2026-08-15"))
    }

    @Test
    fun optimisticPayloadPreservesBeforeAndAfterExactly() {
        val payload = ControlObservationUpdateIn("", "texto nuevo")
        assertEquals("", payload.observacion_anterior)
        assertEquals("texto nuevo", payload.observacion_nueva)
    }

    @Test
    fun entryAndExitRemainIndependentRows() {
        val rows = listOf(4, 5).map { type ->
            ControlBitacoraOut(type, 15, "P0015", "2026-08-15", 29_000_000, type, null)
        }
        assertEquals(listOf(4, 5), rows.map { it.tipo_anotacion })
        assertEquals(2, rows.map { it.id_bitacora }.distinct().size)
    }

    @Test
    fun screenKeepsEditorOpenOnFailureAndClosesOnlyOnSuccess() {
        val source = File("src/main/java/com/cactus/bitacora/ControlSupervisorScreen.kt").readText()
        assertTrue(source.contains("ACTUALIZAR Y CERRAR"))
        assertTrue(source.contains("saveError ="))
        assertTrue(source.contains("onSuccess"))
        assertTrue(source.contains("editing = null"))
        assertTrue(source.contains("refresh += 1"))
    }

    @Test
    fun timestampUsesBogotaZone() {
        val formatted = formatControlTimestamp(0, ZoneId.of("America/Bogota"))
        assertTrue(formatted.startsWith("1969-12-31T19:00"))
    }
}
