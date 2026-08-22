package com.cactus.bitacora.ui.navigation

import com.cactus.bitacora.AppScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryNavigationTest {
    @Test
    fun destinationsHaveRequiredOrderAndStartAtHome() {
        assertEquals(
            listOf("Inicio", "Crear", "Consultar", "Sincronizar", "Más"),
            primaryDestinations.map { it.label }
        )
        assertEquals(AppScreen.Health, primaryDestinations.first().screen)
        assertTrue(primaryDestinations.all { it.symbol.isNotBlank() })
    }

    @Test
    fun emptyInputIsNotAllParticipants() {
        assertEquals(
            MovementQuerySelection.Empty,
            resolveMovementQuery("   ", mapOf("P0015" to 15))
        )
    }

    @Test
    fun wildcardKeepsSupervisorScope() {
        val result = resolveMovementQuery(
            " * ",
            mapOf("P0015" to 15, "P0016" to 16)
        ) as MovementQuerySelection.All
        assertEquals(setOf(15, 16), result.authorizedParticipantIds)
    }

    @Test
    fun wildcardForUnrestrictedUserDoesNotInventAFilter() {
        val result = resolveMovementQuery("*", null) as MovementQuerySelection.All
        assertNull(result.authorizedParticipantIds)
    }

    @Test
    fun participantCodeIsNormalizedAndMustBelongToScope() {
        assertEquals(
            MovementQuerySelection.Participant(15),
            resolveMovementQuery(" p0015 ", mapOf("P0015" to 15))
        )
        assertEquals(
            MovementQuerySelection.NotFound,
            resolveMovementQuery("P0099", mapOf("P0015" to 15))
        )
    }

    @Test
    fun unrestrictedParticipantUsesResolvedCatalogId() {
        assertEquals(
            MovementQuerySelection.Participant(15),
            resolveMovementQuery("P0015", null, resolvedParticipantId = 15)
        )
    }
}
