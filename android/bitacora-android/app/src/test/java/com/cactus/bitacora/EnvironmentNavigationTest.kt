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
    fun onlyAdministratorCanOpenCatalogAdministration() {
        assertTrue(
            isScreenAllowed(
                AppEnvironment.ADMINISTRADOR,
                AppScreen.AdminCatalog
            )
        )
        assertTrue(
            environmentMenuScreens(AppEnvironment.ADMINISTRADOR)
                .contains(AppScreen.AdminCatalog)
        )
        assertFalse(
            isScreenAllowed(
                AppEnvironment.CIUDADANO,
                AppScreen.AdminCatalog
            )
        )
        assertFalse(
            environmentMenuScreens(AppEnvironment.CIUDADANO)
                .contains(AppScreen.AdminCatalog)
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

    @Test
    fun citizenEventTypesKeepRequiredIdsAndLabels() {
        assertEquals(
            listOf(
                1 to "PERMISO",
                2 to "INCAPACIDAD",
                3 to "INCIDENTE",
                4 to "INGRESO",
                5 to "SALIDA",
                6 to "REPORTE DE CULTIVO",
                7 to "AUTORIZA HORAS EXTRAS",
                8 to "REPORTE DE CARRETERA",
                9 to "SATELITAL"
            ),
            citizenEventTypes.map { it.idTipoNovedad to it.label }
        )
    }

    @Test
    fun ingresoAndSalidaAllowEmptyTextWhileOtherEventsAllowAudio() {
        val ingreso = citizenEventTypes.single { it.idTipoNovedad == 4 }
        val salida = citizenEventTypes.single { it.idTipoNovedad == 5 }
        assertFalse(ingreso.requiresTextEvidence())
        assertFalse(salida.requiresTextEvidence())
        assertFalse(ingreso.allowsAudioEvidence())
        assertFalse(salida.allowsAudioEvidence())
        citizenEventTypes.filterNot { it.idTipoNovedad in setOf(4, 5) }.forEach {
            assertTrue(it.requiresTextEvidence())
            assertTrue(it.allowsAudioEvidence())
        }
    }
}
