package com.cactus.bitacora.ui.navigation

import com.cactus.bitacora.AppScreen

internal data class PrimaryDestination(
    val screen: AppScreen,
    val symbol: String,
    val label: String
)

internal val primaryDestinations = listOf(
    PrimaryDestination(AppScreen.Health, "⌂", "Inicio"),
    PrimaryDestination(AppScreen.CreateDailyLog, "+", "Crear"),
    PrimaryDestination(AppScreen.QueryDailyLog, "⌕", "Consultar"),
    PrimaryDestination(AppScreen.Sync, "↻", "Sincronizar"),
    PrimaryDestination(AppScreen.More, "•••", "Más")
)

internal sealed interface MovementQuerySelection {
    data object Empty : MovementQuerySelection
    data class All(val authorizedParticipantIds: Set<Int>?) : MovementQuerySelection
    data class Participant(val participantId: Int) : MovementQuerySelection
    data object NotFound : MovementQuerySelection
}

internal fun resolveMovementQuery(
    rawQuery: String,
    authorizedParticipants: Map<String, Int>?,
    resolvedParticipantId: Int? = null
): MovementQuerySelection {
    val query = rawQuery.trim().uppercase()
    if (query.isEmpty()) return MovementQuerySelection.Empty
    if (query == "*") {
        return MovementQuerySelection.All(authorizedParticipants?.values?.toSet())
    }
    val participantId = authorizedParticipants?.get(query) ?: resolvedParticipantId
    return participantId?.let(MovementQuerySelection::Participant)
        ?: MovementQuerySelection.NotFound
}
