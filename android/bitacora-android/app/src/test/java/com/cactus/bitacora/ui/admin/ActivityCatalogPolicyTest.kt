package com.cactus.bitacora.ui.admin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActivityCatalogPolicyTest {
    @Test fun `exact note is preserved`() {
        assertEquals(
            "Estas actividades son únicas y se pueden repetir en varios procesos",
            ACTIVITY_CATALOG_NOTE
        )
    }

    @Test fun `blank and overlong names are rejected`() {
        assertEquals("El nombre es obligatorio", activityNameError("   "))
        assertEquals(
            "El nombre admite máximo 100 caracteres",
            activityNameError("x".repeat(101))
        )
        assertNull(activityNameError("  Actividad válida  "))
    }

    @Test fun `catalog becomes loadable after actor is entered`() {
        assertEquals(false, activityActorReady("   "))
        assertEquals(true, activityActorReady("p0002"))
    }
}
