package com.cactus.bitacora.ui.admin

import com.cactus.bitacora.model.ProcessAdminOut
import org.junit.Assert.assertEquals
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
}
