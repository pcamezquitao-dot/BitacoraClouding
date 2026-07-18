package com.cactus.bitacora.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ParticipantSearchTest {
    @Test
    fun `normaliza P0002 sin perder prefijo ni ceros`() {
        listOf("P0002", "p0002", " P0002 ").forEach {
            assertEquals("P0002", normalizeParticipantQuery(it))
        }
    }

    @Test
    fun `codigo inexistente conserva su contenido alfanumerico`() {
        assertEquals("NO-EXISTE", normalizeParticipantQuery(" no-existe "))
    }

}
