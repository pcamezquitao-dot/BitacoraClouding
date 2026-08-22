package com.cactus.bitacora.feature.testdata

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.data.BitacoraRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun TestBitacoraAdminScreen(
    repository: BitacoraRepository,
    onBack: () -> Unit
) {
    val today = remember { LocalDate.now(ZoneId.of(TEST_BITACORA_ZONE)) }
    var participantCode by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(today.minusDays(13).toString()) }
    var endDate by remember { mutableStateOf(today.toString()) }
    var meanHours by remember { mutableStateOf("8") }
    var deviationHours by remember { mutableStateOf("1") }
    var sundayPercentage by remember { mutableStateOf("25") }
    var centralEntry by remember { mutableStateOf("07:00") }
    var entryVariation by remember { mutableStateOf("30") }
    var minimumHours by remember { mutableStateOf("5") }
    var maximumHours by remember { mutableStateOf("11") }
    var showAdvanced by remember { mutableStateOf(false) }
    var seedText by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<TestBitacoraPreview?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun invalidatePreview() {
        preview = null
        message = null
        error = null
    }

    fun parameters(): TestBitacoraParameters {
        val start = LocalDate.parse(startDate.trim())
        val end = LocalDate.parse(endDate.trim())
        val code = participantCode.trim().uppercase()
        return TestBitacoraParameters(
            participantCode = code,
            startDate = start,
            endDate = end,
            meanMinutes = ((meanHours.toDoubleOrNull()
                ?: kotlin.error("Promedio diario inválido")) * 60).toInt(),
            sampleDeviationMinutes = ((deviationHours.toDoubleOrNull()
                ?: kotlin.error("Desviación estándar inválida")) * 60).toInt(),
            sundayPercentage = sundayPercentage.toIntOrNull()
                ?: kotlin.error("Porcentaje de domingos inválido"),
            centralEntryTime = LocalTime.parse(centralEntry.trim()),
            maximumEntryVariationMinutes = entryVariation.toIntOrNull()
                ?: kotlin.error("Variación de entrada inválida"),
            minimumDurationMinutes = ((minimumHours.toDoubleOrNull()
                ?: kotlin.error("Duración mínima inválida")) * 60).toInt(),
            maximumDurationMinutes = ((maximumHours.toDoubleOrNull()
                ?: kotlin.error("Duración máxima inválida")) * 60).toInt(),
            seed = seedText.trim().toLongOrNull()
                ?: automaticTestBitacoraSeed(code, start, end)
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("INSERTAR BITÁCORA DE PRUEBA", style = MaterialTheme.typography.titleLarge)
        Text(
            "DESARROLLO: los registros quedarán identificados como PRUEBA y nunca reemplazan datos existentes.",
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = participantCode,
            onValueChange = { participantCode = it; invalidatePreview() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Código del participante") },
            singleLine = true
        )
        FieldRow(
            leftValue = startDate,
            leftLabel = "Fecha inicial (AAAA-MM-DD)",
            onLeftChange = { startDate = it; invalidatePreview() },
            rightValue = endDate,
            rightLabel = "Fecha final (AAAA-MM-DD)",
            onRightChange = { endDate = it; invalidatePreview() }
        )
        FieldRow(
            leftValue = meanHours,
            leftLabel = "Promedio diario (horas)",
            onLeftChange = { meanHours = it; invalidatePreview() },
            rightValue = deviationHours,
            rightLabel = "Desviación muestral (horas)",
            onRightChange = { deviationHours = it; invalidatePreview() }
        )
        FieldRow(
            leftValue = sundayPercentage,
            leftLabel = "Domingos (%)",
            onLeftChange = { sundayPercentage = it; invalidatePreview() },
            rightValue = centralEntry,
            rightLabel = "Hora central",
            onRightChange = { centralEntry = it; invalidatePreview() }
        )
        FieldRow(
            leftValue = entryVariation,
            leftLabel = "Variación (minutos)",
            onLeftChange = { entryVariation = it; invalidatePreview() },
            rightValue = minimumHours,
            rightLabel = "Duración mínima (horas)",
            onRightChange = { minimumHours = it; invalidatePreview() }
        )
        OutlinedTextField(
            value = maximumHours,
            onValueChange = { maximumHours = it; invalidatePreview() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Duración máxima (horas)") },
            singleLine = true
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = showAdvanced, onCheckedChange = { showAdvanced = it })
            Text("Opciones avanzadas")
        }
        if (showAdvanced) {
            OutlinedTextField(
                value = seedText,
                onValueChange = { seedText = it; invalidatePreview() },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Semilla (vacía = automática)") },
                singleLine = true
            )
            Text("Zona horaria: $TEST_BITACORA_ZONE")
        }
        Button(
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            onClick = {
                busy = true
                error = null
                message = null
                scope.launch {
                    try {
                        val result = repository.previewTestBitacoraBatch(
                            parameters = parameters(),
                            isAdministrator = true
                        )
                        preview = result
                        seedText = result.batch.parameters.seed.toString()
                    } catch (failure: Exception) {
                        preview = null
                        error = failure.message ?: "No fue posible generar la vista previa"
                    } finally {
                        busy = false
                    }
                }
            }
        ) { Text(if (busy) "VALIDANDO…" else "GENERAR VISTA PREVIA") }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        preview?.let { current ->
            TestBatchPreview(current)
            Text(
                "Se insertarán ${current.batch.physicalRecordCount} registros sintéticos: " +
                    "${current.batch.entryCount} entradas y ${current.batch.exitCount} salidas para " +
                    "${current.batch.parameters.participantCode}.",
                fontWeight = FontWeight.Bold
            )
            Button(
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                onClick = {
                    busy = true
                    error = null
                    message = null
                    scope.launch {
                        try {
                            val result = repository.insertTestBitacoraBatch(
                                preview = current,
                                isAdministrator = true
                            )
                            message = "Se insertaron ${result.insertedRecords} registros de prueba. " +
                                "La sincronización oficial quedó programada."
                            preview = null
                        } catch (failure: Exception) {
                            error = failure.message ?: "No fue posible insertar el lote"
                        } finally {
                            busy = false
                        }
                    }
                }
            ) { Text("INSERTAR REGISTROS DE PRUEBA") }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            onClick = onBack
        ) { Text("Regresar") }
    }
}

@Composable
private fun FieldRow(
    leftValue: String,
    leftLabel: String,
    onLeftChange: (String) -> Unit,
    rightValue: String,
    rightLabel: String,
    onRightChange: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = leftValue,
            onValueChange = onLeftChange,
            modifier = Modifier.weight(1f),
            label = { Text(leftLabel) },
            singleLine = true
        )
        OutlinedTextField(
            value = rightValue,
            onValueChange = onRightChange,
            modifier = Modifier.weight(1f),
            label = { Text(rightLabel) },
            singleLine = true
        )
    }
}

@Composable
private fun TestBatchPreview(preview: TestBitacoraPreview) {
    val batch = preview.batch
    val participantName = listOfNotNull(preview.participant.nombre, preview.participant.apellido)
        .joinToString(" ").trim()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        HorizontalDivider()
        Text("Vista previa", style = MaterialTheme.typography.titleMedium)
        Text("Participante: ${batch.parameters.participantCode} — $participantName")
        Text("Periodo: ${batch.parameters.startDate} a ${batch.parameters.endDate}")
        Text("Días trabajados: ${batch.workdays.size}; domingos: ${batch.sundayCount}")
        Text("Entradas: ${batch.entryCount}; salidas: ${batch.exitCount}")
        Text("Registros físicos: ${batch.physicalRecordCount}")
        Text("Promedio: ${formatMinutes(batch.meanMinutes)}")
        Text(
            "Desviación muestral: " +
                (batch.sampleDeviationMinutes?.let(::formatMinutes)
                    ?: "No verificable con menos de dos días")
        )
        Text("Semilla: ${batch.parameters.seed}")
        HorizontalDivider()
        Text("Fecha | Entrada | Salida | Duración", fontWeight = FontWeight.Bold)
        batch.workdays.forEach { day ->
            Text(
                "${day.date} | ${minuteToTime(day.entryMinute)} | " +
                    "${minuteToTime(day.exitMinute)} | ${day.durationMinutes} min",
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
        HorizontalDivider()
    }
}

private fun formatMinutes(value: Double): String =
    String.format(Locale.US, "%.2f min (%.2f h)", value, value / 60.0)

private fun minuteToTime(minute: Int): String =
    java.time.Instant.ofEpochSecond(minute * 60L)
        .atZone(ZoneId.of(TEST_BITACORA_ZONE))
        .toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm"))
