package com.cactus.bitacora

import com.cactus.bitacora.model.PortalItemOut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalGeneralPolicyTest {
    private fun item(id: Int, name: String) = PortalItemOut(
        id_accion = id, nombre = name, estado_destino = "DISPONIBLE"
    )

    @Test fun gridUsesExactlyTwoItemsPerCompleteRowAndStableServerOrder() {
        val rows = portalRows(listOf(item(1, "Uno"), item(2, "Dos"), item(3, "Tres")))
        assertEquals(listOf(2, 1), rows.map(List<*>::size))
        assertEquals(listOf(1, 2, 3), rows.flatten().map { it.id_accion })
    }

    @Test fun portalSourceKeepsTasksUniqueAndDoesNotOpenCasesOnMenuTap() {
        val source = java.io.File("src/main/java/com/cactus/bitacora/PortalGeneralScreen.kt").readText()
        assertEquals(1, Regex("Text\\(\\\"Tareas BPM\\\"\\)").findAll(source).count())
        assertTrue("openPortalOption" in source)
        assertTrue("queueOpenCase" !in source)
        assertTrue("Cambiar participante" in source)
        assertTrue("fontSize = 14.sp" in source)
        assertTrue("lineHeight = 18.sp" in source)
    }
}

