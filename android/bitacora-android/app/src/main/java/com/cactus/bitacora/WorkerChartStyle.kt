package com.cactus.bitacora

import androidx.compose.ui.graphics.Color
import com.cactus.bitacora.model.WorkerDayOut

internal val WorkerLightBlue = Color(0xFF64B5F6)
internal val WorkerDarkBlue = Color(0xFF0D47A1)
internal val WorkerOvertimeOrange = Color(0xFFF57C00)
internal val WorkerSundayHolidayRed = Color(0xFFC62828)

internal data class WorkerBarSegments(
    val lightMinutes: Int = 0,
    val ordinaryMinutes: Int = 0,
    val additionalMinutes: Int = 0,
    val specialMinutes: Int = 0
)

internal fun workerBarSegments(day: WorkerDayOut): WorkerBarSegments {
    val total = day.minutos_trabajados.coerceAtLeast(0)
    if (day.domingo || day.festivo) return WorkerBarSegments(specialMinutes = total)
    val limit = if (day.sabado) 4 * 60 else 8 * 60
    return when {
        total == 0 -> WorkerBarSegments()
        total < limit -> WorkerBarSegments(lightMinutes = total)
        else -> WorkerBarSegments(ordinaryMinutes = limit, additionalMinutes = total - limit)
    }
}
