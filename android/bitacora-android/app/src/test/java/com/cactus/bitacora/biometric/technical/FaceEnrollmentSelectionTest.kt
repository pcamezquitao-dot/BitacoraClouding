package com.cactus.bitacora.biometric.technical

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FaceEnrollmentSelectionTest {
    @Test
    fun explainsEachDisabledEnrollmentState() {
        assertEquals(
            "Escriba o escanee el código del participante",
            faceEnrollmentSelectionError("", false)
        )
        assertEquals(
            "Busque y seleccione primero el participante",
            faceEnrollmentSelectionError("P0002", false)
        )
    }

    @Test
    fun enablesEnrollmentOnlyWithValidSelectedParticipant() {
        assertNull(faceEnrollmentSelectionError("P0002", true))
    }
}
