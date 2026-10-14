package com.cactus.bitacora.ui.admin

import com.cactus.bitacora.model.ProcessAdminOut
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessAdminPolicyTest {
    private fun process(id: Int, name: String, parent: Int? = null) = ProcessAdminOut(
        id, name, null, parent, 0, 0.0, 1, null, null, null, null, null, "f".repeat(64)
    )

    @Test fun `tree is preorder and alphabetic between siblings`() {
        val rows = buildProcessTreeRows(
            listOf(process(3, "Zulu", 1), process(2, "Alfa", 1), process(1, "Raíz")),
            setOf(1)
        )
        assertEquals(listOf(1, 2, 3), rows.map { it.process.id_proceso })
        assertEquals(listOf(0, 1, 1), rows.map { it.depth })
    }

    @Test fun `expansion flag exposes children only when selected`() {
        val values = listOf(process(1, "Raíz"), process(2, "Hijo", 1))
        assertEquals(listOf(1), buildProcessTreeRows(values, emptySet()).map { it.process.id_proceso })
        assertEquals(listOf(1, 2), buildProcessTreeRows(values, setOf(1)).map { it.process.id_proceso })
    }

    @Test fun `descendants are excluded from parent candidates`() {
        val values = listOf(process(1, "Raíz"), process(2, "Hijo", 1), process(3, "Nieto", 2))
        assertEquals(setOf(2, 3), descendantProcessIds(values, 1))
    }

    @Test fun `duplicate and invalid form values are rejected`() {
        val values = listOf(process(1, "Proceso Uno"))
        assertTrue(processFormError(values, null, "2", "proceso uno", "", "0", "0", "1", null)!!.contains("nombre"))
        assertTrue(processFormError(values, null, "2", "Nuevo", "", "-1", "0", "1", null)!!.contains("tiempo"))
        assertTrue(processFormError(values, null, "2", "Nuevo", "", "0", "0", "2", null)!!.contains("tipo"))
    }

    @Test fun `first root with null current and parent is valid`() {
        assertEquals(null, processFormError(emptyList(), null, "99029101", "Primera raíz", "", "0", "0", "1", null))
    }

    @Test fun `server detail is shown instead of generic http status`() {
        assertEquals("No se puede eliminar: el proceso tiene subprocesos", processErrorDetail("{\"detail\":\"No se puede eliminar: el proceso tiene subprocesos\"}", "HTTP 409"))
    }

    @Test fun `preexisting cycle is visible and marked inconsistent`() {
        val cycle = listOf(process(10, "Ciclo A", 11), process(11, "Ciclo B", 10))
        assertTrue(hasProcessAncestryCycle(cycle, 10))
        assertTrue(buildProcessTreeRows(cycle, emptySet()).all { it.inconsistent })
    }

    @Test fun `search matches full and partial codes without depending on expansion`() {
        val values = listOf(process(10, "Raíz"), process(205, "Hijo oculto", 10))
        assertEquals(listOf(205), searchProcesses(values, "205").map { it.process.id_proceso })
        assertEquals(listOf(205), searchProcesses(values, "  20 ").map { it.process.id_proceso })
        assertFalse(buildProcessTreeRows(values, emptySet()).any { it.process.id_proceso == 205 })
    }

    @Test fun `search matches names ignoring case accents and edge spaces`() {
        val values = listOf(process(1, "Gestión Clínica"), process(2, "Operaciones"))
        assertEquals(listOf(1), searchProcesses(values, "  GESTION clin ").map { it.process.id_proceso })
        assertEquals(listOf(2), searchProcesses(values, "racio").map { it.process.id_proceso })
    }

    @Test fun `search gives duplicate names their complete parent paths`() {
        val values = listOf(
            process(1, "Raíz A"), process(2, "Raíz B"),
            process(3, "Padre A", 1), process(4, "Padre B", 2),
            process(5, "Revisión", 3), process(6, "Revisión", 4)
        )
        val results = searchProcesses(values, "revision")
        assertEquals(listOf(5, 6), results.map { it.process.id_proceso })
        assertEquals(setOf("Raíz A > Padre A", "Raíz B > Padre B"), results.map { it.parentPath }.toSet())
    }

    @Test fun `search has no arbitrary result limit and blank query restores tree mode`() {
        val values = (1..250).map { process(it, "Proceso $it") }
        assertEquals(250, searchProcesses(values, "proceso").size)
        assertTrue(searchProcesses(values, "   ").isEmpty())
        assertTrue(searchProcesses(values, "inexistente").isEmpty())
    }

    @Test fun `search UI filters locally opens existing detail and distinguishes load errors`() {
        val source = File("src/main/java/com/cactus/bitacora/ui/admin/ProcessAdminPanel.kt").readText()
        assertTrue(source.contains("onValueChange = { searchQuery = it }"))
        assertTrue(source.contains("onClick = { searchQuery = \"\" }"))
        assertTrue(source.contains("onClick = { viewing = result.process }"))
        assertTrue(source.contains("!loading && error == null && searchResults.isEmpty()"))
        assertFalse(source.substringAfter("onValueChange = { searchQuery = it }").substringBefore("label =").contains("adminProcesses"))
    }
}
