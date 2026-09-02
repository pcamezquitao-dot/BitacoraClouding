package com.cactus.bitacora.data

import com.cactus.bitacora.model.WorkerDayOut
import com.cactus.bitacora.model.WorkerEventOut
import com.cactus.bitacora.model.WorkerTimeOut
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private val colombiaZone = ZoneId.of("America/Bogota")

internal fun calculateWorkerTime(
    participantId: Int,
    year: Int,
    month: Int,
    source: List<WorkerEventOut>,
    calendarDays: List<WorkerDayOut> = emptyList()
): WorkerTimeOut {
    val events = source.distinctBy { it.client_uuid?.let { uuid -> "uuid:$uuid" } ?: it.id_anotacion }
        .sortedWith(compareBy({ it.timestamp_min }, { it.id_anotacion }))
    val worked = mutableMapOf<LocalDate, Int>()
    val detail = mutableMapOf<LocalDate, MutableList<WorkerEventOut>>()
    var entry: WorkerEventOut? = null
    events.forEach { event ->
        if (event.tipo_anotacion == 4 && entry == null) entry = event
        else if (event.tipo_anotacion == 4) {
            detail.getOrPut(event.localDate(), ::mutableListOf) += event.copy(
                inconsistencia = "Entrada consecutiva sin salida intermedia"
            )
        }
        if (event.tipo_anotacion == 5 && entry != null) {
            val start = Instant.ofEpochSecond(entry!!.timestamp_min * 60L).atZone(colombiaZone)
            val end = Instant.ofEpochSecond(event.timestamp_min * 60L).atZone(colombiaZone)
            if (end.isAfter(start)) {
                worked[start.toLocalDate()] = worked.getOrDefault(start.toLocalDate(), 0) +
                    ChronoUnit.MINUTES.between(start, end).toInt()
                detail.getOrPut(start.toLocalDate(), ::mutableListOf).addAll(
                    listOf(entry!!.copy(utilizado = true), event.copy(utilizado = true))
                )
            } else {
                detail.getOrPut(event.localDate(), ::mutableListOf) += event.copy(
                    inconsistencia = "Duración no positiva"
                )
            }
            entry = null
        } else if (event.tipo_anotacion == 5) {
            detail.getOrPut(event.localDate(), ::mutableListOf) += event.copy(
                inconsistencia = "Salida sin entrada"
            )
        }
    }
    entry?.let { pending ->
        detail.getOrPut(pending.localDate(), ::mutableListOf) += pending.copy(
            inconsistencia = "Entrada sin salida"
        )
    }
    val calendar = calendarDays.associateBy { LocalDate.parse(it.fecha) }
    val first = LocalDate.of(year, month, 1)
    val days = (0 until first.lengthOfMonth()).map { offset ->
        val date = first.plusDays(offset.toLong())
        val known = calendar[date]
        val saturday = known?.sabado ?: (date.dayOfWeek.value == 6)
        val sunday = known?.domingo ?: (date.dayOfWeek.value == 7)
        val holiday = known?.festivo ?: false
        val dayEvents = detail[date].orEmpty().distinctBy { it.id_anotacion }
        WorkerDayOut(
            date.toString(), known?.dia_semana ?: date.dayOfWeek.value, !sunday && !holiday,
            saturday, sunday, holiday, known?.nombre_festivo, worked[date] ?: 0,
            dayEvents.any { it.inconsistencia != null }, dayEvents
        )
    }
    return WorkerTimeOut(participantId, year, month, "America/Bogota", days)
}

private fun WorkerEventOut.localDate(): LocalDate =
    Instant.ofEpochSecond(timestamp_min * 60L).atZone(colombiaZone).toLocalDate()
