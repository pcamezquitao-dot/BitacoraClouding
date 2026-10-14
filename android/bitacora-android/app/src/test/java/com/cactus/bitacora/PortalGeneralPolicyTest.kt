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
        assertTrue("val goldenRatio = 1.618f" in source)
        assertTrue("val tasksRatio = 4.236f" in source)
        assertTrue("val functionHeight = functionWidth / goldenRatio" in source)
        assertTrue("val tasksHeight = maxWidth / tasksRatio" in source)
        assertEquals(2, Regex("val secondaryHeight = maxWidth / 6\\.854f").findAll(source).count())
        assertTrue("Modifier.weight(1f).height(functionHeight)" in source)
        assertTrue("Modifier.fillMaxWidth().height(tasksHeight)" in source)
        assertEquals(2, Regex("Modifier\\.fillMaxWidth\\(\\)\\.height\\(secondaryHeight\\)").findAll(source).count())
        assertTrue("textAlign = TextAlign.Center" in source)
    }
}

