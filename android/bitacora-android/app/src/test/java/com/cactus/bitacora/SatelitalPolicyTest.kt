package com.cactus.bitacora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SatelitalPolicyTest {
    @Test
    fun satelital_es_tipo_nueve_sin_alterar_los_ocho_existentes() {
        assertEquals((1..9).toList(), citizenEventTypes.map { it.idTipoNovedad })
        assertEquals("SATELITAL", citizenEventTypes.last().label)
    }

    @Test
    fun tipos_de_seguimiento_iniciales_coinciden_con_el_contrato() {
        assertEquals(10, satelliteTrackingTypes.size)
        assertTrue("SUPERFICIE_DE_AGUA" in satelliteTrackingTypes)
        assertTrue("DEFORESTACION" in satelliteTrackingTypes)
        assertTrue("OTRO" in satelliteTrackingTypes)
    }
}
