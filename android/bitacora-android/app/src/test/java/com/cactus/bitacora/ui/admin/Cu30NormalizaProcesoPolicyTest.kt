package com.cactus.bitacora.ui.admin

import com.cactus.bitacora.model.Cu30CandidateOut
import com.cactus.bitacora.model.Cu30Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Cu30NormalizaProcesoPolicyTest {

    @Test
    fun edicion_de_tipo_y_transiciones_conserva_la_definicion_explicita() {
        val original = step().copy(
            id_actividad = 7,
            nombre_actividad = "Validar soporte",
            siguiente = listOf("P002")
        )

        val decision = changeCu30StepType(original, "decision")

        assertEquals("decision", decision.tipo)
        assertNull(decision.id_actividad)
        assertNull(decision.nombre_actividad)
        assertEquals(listOf("P002", "P003"), parseCu30Transitions(" P002, P003, P002 "))
        assertEquals(listOf("radicado", "fecha"), parseCu30Values("radicado, fecha, radicado"))
    }
    private fun step() = Cu30Step(
        paso_id = "P001", requerimiento = "Validar soporte", responsable = "Analista",
        candidates = listOf(Cu30CandidateOut(7, "Validar documento", "Comprobar soporte"))
    )

    @Test fun `catalog choice preserves exact identity and clears proposal`() {
        val result = selectCu30Activity(
            step().copy(propuesta_codigo = "PROP-P001", propuesta_nombre = "Nueva", propuesta_descripcion = "Descripción"),
            7, "Validar documento"
        )
        assertEquals(7L, result.id_actividad)
        assertEquals("Validar documento", result.nombre_actividad)
        assertNull(result.propuesta_codigo)
    }

    @Test fun `new proposal has stable provisional code and clears catalog choice`() {
        val first = proposeCu30Activity(selectCu30Activity(step(), 7, "Validar documento"))
        val second = proposeCu30Activity(first)
        assertEquals("PROP-P001", first.propuesta_codigo)
        assertEquals(first.propuesta_codigo, second.propuesta_codigo)
        assertNull(first.id_actividad)
        assertNull(first.nombre_actividad)
    }

    @Test fun `approval is blocked by pending items and after approval`() {
        assertFalse(cu30CanApprove(listOf("Falta responsable"), "PENDIENTE"))
        assertFalse(cu30CanApprove(emptyList(), "APROBADA"))
        assertTrue(cu30CanApprove(emptyList(), "PENDIENTE"))
    }
}
