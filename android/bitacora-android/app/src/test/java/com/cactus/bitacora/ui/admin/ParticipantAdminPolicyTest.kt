package com.cactus.bitacora.ui.admin

import com.cactus.bitacora.model.ParticipantAdminIn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticipantAdminPolicyTest {
    private fun payload(birth: String? = "1990-01-01", sex: String? = "F") = ParticipantAdminIn(
        1, "123", "P-1", "Patricia", "Prueba", birth, sex
    )

    @Test fun valid_form_requires_real_required_fields() {
        assertTrue(validParticipantForm(payload()))
        assertFalse(validParticipantForm(payload(birth = "01/01/1990")))
        assertFalse(validParticipantForm(payload(sex = "X")))
        assertFalse(validParticipantForm(payload().copy(documento = "")))
    }

    @Test fun optional_fields_can_be_empty() {
        assertTrue(validParticipantForm(payload(birth = null, sex = null).copy(
            apellido = null, fecha_entrada = null, fecha_salida = null, observaciones = null
        )))
    }

    @Test fun reports_visible_validation_reason() {
        assertTrue(participantFormError(payload(birth = "01/01/1990"))!!.contains("AAAA-MM-DD"))
        assertTrue(participantFormError(payload().copy(nombre = ""))!!.contains("Nombres"))
    }

    @Test fun observations_field_is_multiline_and_other_fields_remain_single_line() {
        assertEquals(ParticipantFieldLayout(false, 4, 8), participantFieldLayout(9))
        assertEquals(ParticipantFieldLayout(true, 1, 1), participantFieldLayout(10))
    }

    @Test fun multiline_observations_are_preserved_in_payload() {
        val observations = "Primera línea\nSegunda línea\nTercera línea\nCuarta línea"
        assertEquals(observations, payload().copy(observaciones = observations).observaciones)
    }
}
