package com.cactus.bitacora.ui.admin

import com.cactus.bitacora.model.ParticipantAdminIn
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParticipantAdminPolicyTest {
    private fun payload(birth: String = "1990-01-01", sex: String = "F") = ParticipantAdminIn(
        1, "123", "P-1", "Patricia", "Prueba", birth, sex
    )

    @Test fun valid_form_requires_real_required_fields() {
        assertTrue(validParticipantForm(payload()))
        assertFalse(validParticipantForm(payload(birth = "01/01/1990")))
        assertFalse(validParticipantForm(payload(sex = "X")))
        assertFalse(validParticipantForm(payload().copy(documento = "")))
    }
}
