package com.cactus.bitacora.data

import com.cactus.bitacora.model.ParticipanteOut
import java.util.Locale

internal fun normalizeParticipantQuery(value: String): String =
    value.trim().uppercase(Locale.ROOT)

data class ParticipantSearchResult(
    val receivedCode: String,
    val normalizedCode: String,
    val source: String,
    val participants: List<ParticipanteOut>
)
