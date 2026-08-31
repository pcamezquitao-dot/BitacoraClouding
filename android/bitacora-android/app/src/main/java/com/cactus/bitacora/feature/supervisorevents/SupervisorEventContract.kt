package com.cactus.bitacora.feature.supervisorevents

import com.cactus.bitacora.data.local.SupervisorEventLocalEntity
import com.cactus.bitacora.data.local.SyncStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID

internal val COLOMBIA_ZONE: ZoneId = ZoneId.of("America/Bogota")

internal enum class SupervisorEventType(val backendId: Int, val label: String) {
    PERMISSION(1, "Permiso"),
    DISABILITY(2, "Incapacidad"),
    OVERTIME(7, "Hora extra")
}

internal data class SupervisorEventDraft(
    val supervisorCode: String,
    val participantId: Int,
    val areaId: Int,
    val type: SupervisorEventType,
    val startDate: String,
    val endDate: String = startDate,
    val startTime: String = "",
    val endTime: String = "",
    val observations: String = ""
)

internal data class ValidatedSupervisorEvent(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startTime: LocalTime?,
    val endTime: LocalTime?,
    val durationMinutes: Int?
)

internal class SupervisorEventValidationException(message: String) : IllegalArgumentException(message)

internal fun validateSupervisorEvent(draft: SupervisorEventDraft): ValidatedSupervisorEvent {
    if (draft.supervisorCode.isBlank()) {
        throw SupervisorEventValidationException("Falta identificar al supervisor")
    }
    if (draft.participantId <= 0 || draft.areaId <= 0) {
        throw SupervisorEventValidationException("Seleccione un participante autorizado y activo")
    }
    val startDate = parseDate(draft.startDate, "La fecha inicial no es válida")
    val endDate = parseDate(
        if (draft.type == SupervisorEventType.DISABILITY) draft.endDate else draft.startDate,
        "La fecha final no es válida"
    )
    if (endDate < startDate) {
        throw SupervisorEventValidationException("La fecha final no puede ser anterior a la inicial")
    }
    if (draft.observations.length > 400) {
        throw SupervisorEventValidationException("Las observaciones no pueden superar 400 caracteres")
    }
    if (draft.type == SupervisorEventType.DISABILITY) {
        return ValidatedSupervisorEvent(startDate, endDate, null, null, null)
    }
    val startTime = parseTime(draft.startTime, "La hora inicial no es válida")
    val endTime = parseTime(draft.endTime, "La hora final no es válida")
    if (!endTime.isAfter(startTime)) {
        throw SupervisorEventValidationException("La hora final debe ser posterior a la inicial")
    }
    val minutes = java.time.Duration.between(startTime, endTime).toMinutes().toInt()
    return ValidatedSupervisorEvent(startDate, startDate, startTime, endTime, minutes)
}

internal fun SupervisorEventDraft.toLocalEntity(
    clientUuid: String = UUID.randomUUID().toString(),
    nowMillis: Long = System.currentTimeMillis()
): SupervisorEventLocalEntity {
    val valid = validateSupervisorEvent(this)
    return SupervisorEventLocalEntity(
        clientUuid = clientUuid,
        supervisorCode = supervisorCode.trim().uppercase(),
        tipoNovedad = type.backendId,
        idParticipante = participantId,
        idArea = areaId,
        fechaInicio = valid.startDate.toString(),
        fechaFinal = valid.endDate.toString(),
        horaInicio = valid.startTime?.toString(),
        horaFinal = valid.endTime?.toString(),
        observaciones = observations.trim().ifBlank { null },
        syncStatus = SyncStatus.PENDIENTE_CREAR,
        createdAtMillis = nowMillis,
        updatedAtMillis = nowMillis
    )
}

internal fun eventTypeFromBackendId(id: Int): SupervisorEventType? =
    SupervisorEventType.entries.firstOrNull { it.backendId == id }

internal fun isParticipantAuthorized(
    participantId: Int,
    areaId: Int,
    authorizedParticipantAreas: Set<Pair<Int, Int>>
): Boolean = participantId > 0 && areaId > 0 &&
    participantId to areaId in authorizedParticipantAreas

private fun parseDate(value: String, message: String): LocalDate = try {
    LocalDate.parse(value.trim())
} catch (_: DateTimeParseException) {
    throw SupervisorEventValidationException(message)
}

private fun parseTime(value: String, message: String): LocalTime = try {
    LocalTime.parse(value.trim())
} catch (_: DateTimeParseException) {
    throw SupervisorEventValidationException(message)
}
