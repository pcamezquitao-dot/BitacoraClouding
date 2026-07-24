package com.cactus.bitacora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentNavigationTest {
    @Test
    fun administratorCanOpenEnrollment() {
        assertTrue(canAccessEnrollment(AppEnvironment.ADMINISTRADOR))
        assertTrue(isScreenAllowed(AppEnvironment.ADMINISTRADOR, AppScreen.FaceEnrollment))
        assertTrue(
            environmentMenuScreens(AppEnvironment.ADMINISTRADOR)
                .contains(AppScreen.FaceEnrollment)
        )
    }

    @Test
    fun citizenCannotSeeOrOpenEnrollment() {
        assertFalse(canAccessEnrollment(AppEnvironment.CIUDADANO))
        assertFalse(isScreenAllowed(AppEnvironment.CIUDADANO, AppScreen.FaceEnrollment))
        assertFalse(
            environmentMenuScreens(AppEnvironment.CIUDADANO)
                .contains(AppScreen.FaceEnrollment)
        )
        assertEquals(
            "Esta función está disponible únicamente para el administrador",
            ADMIN_ONLY_MESSAGE
        )
    }

    @Test
    fun bothEnvironmentsKeepCitizenFunctions() {
        val common = listOf(
            AppScreen.CreateDailyLog,
            AppScreen.QueryDailyLog,
            AppScreen.Sync
        )
        AppEnvironment.entries.forEach { environment ->
            assertTrue(environmentMenuScreens(environment).containsAll(common))
        }
    }

    @Test
    fun environmentIsSessionStateWithStableLabels() {
        assertEquals("Administrador", AppEnvironment.ADMINISTRADOR.label)
        assertEquals("Ciudadano", AppEnvironment.CIUDADANO.label)
        assertFalse(canAccessEnrollment(null))
    }
}
