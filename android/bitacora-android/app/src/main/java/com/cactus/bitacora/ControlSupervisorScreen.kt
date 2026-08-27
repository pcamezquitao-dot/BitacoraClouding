package com.cactus.bitacora

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.ControlBitacoraOut
import com.cactus.bitacora.model.ControlSupervisedWorkerOut
import com.cactus.bitacora.model.SupervisorSessionOut
import com.cactus.bitacora.model.WorkerDayOut
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

internal data class ControlDaySelection(
    val supervisorId: Int,
    val supervisedId: Int,
    val date: String
)

@Composable
internal fun ControlSupervisorScreen(
    repository: BitacoraRepository,
    session: SupervisorSessionOut,
    onBack: () -> Unit
) {
    val zone = remember { ZoneId.of("America/Bogota") }
    val today = remember { LocalDate.now(zone) }
    var period by remember { mutableStateOf(YearMonth.from(today)) }
    var report by remember { mutableStateOf<com.cactus.bitacora.model.ControlSupervisorReportOut?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<ControlDaySelection?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var dayRows by remember { mutableStateOf<List<ControlBitacoraOut>>(emptyList()) }
    var dayError by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ControlBitacoraOut?>(null) }
    var editedText by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(session.codigo, period, refresh) {
        runCatching { repository.supervisorControl(session, period.year, period.monthValue) }
            .onSuccess { report = it; error = null }
            .onFailure { report = null; error = it.message ?: "No fue posible consultar CONTROL" }
    }
    LaunchedEffect(selected, refresh) {
        val current = selected
        if (current == null) {
            dayRows = emptyList()
            dayError = null
        } else runCatching {
            repository.supervisorControlDayBitacoras(session, current.supervisedId, current.date)
        }.onSuccess { dayRows = it; dayError = null }
            .onFailure {
                dayRows = emptyList()
                dayError = it.message ?: "No fue posible consultar las bitacoras"
            }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("CONTROL", style = MaterialTheme.typography.headlineSmall)
        Text("${session.codigo} · ${session.nombre_completo}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton({ period = period.minusMonths(1) }) { Text("Anterior") }
            Text("${period.month.getDisplayName(TextStyle.FULL, Locale("es"))} ${period.year}")
            OutlinedButton({ period = period.plusMonths(1) }) { Text("Siguiente") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        report?.let { data ->
            Text("Acumulado general", style = MaterialTheme.typography.titleLarge)
            Text("Supervisados: ${data.acumulado.supervisados}")
            Text("Tiempo trabajado: ${formatControlMinutes(data.acumulado.total_minutos)}")
            Text("Jornadas incompletas: ${data.acumulado.jornadas_incompletas}")
            if (data.supervisados.isEmpty()) Text("No hay supervisados activos para este supervisor")
            data.supervisados.forEach { worker ->
                ControlWorkerDailyChart(
                    worker = worker,
                    supervisorId = session.id_supervisor,
                    selected = selected,
                    dayRows = dayRows,
                    dayError = dayError,
                    onSelect = { selected = it },
                    onOpenEditor = { row ->
                        editing = row
                        editedText = row.observaciones.orEmpty()
                        saveError = null
                    }
                )
            }
        }
        OutlinedButton(onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver al menu") }
    }

    editing?.let { row ->
        AlertDialog(
            onDismissRequest = { if (!saving) editing = null },
            title = { Text("Editar observacion · Bitacora ${row.id_bitacora}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tipo ${row.tipo_anotacion} · ${formatControlTimestamp(row.timestamp_min, zone)}")
                    OutlinedTextField(
                        value = editedText,
                        onValueChange = { editedText = it },
                        label = { Text("Observaciones") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !saving
                    )
                    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(enabled = !saving, onClick = { saving = true; saveError = null }) {
                    Text("ACTUALIZAR Y CERRAR")
                }
            },
            dismissButton = {
                OutlinedButton(enabled = !saving, onClick = { editing = null }) { Text("Cancelar") }
            }
        )
        LaunchedEffect(saving) {
            if (saving) runCatching {
                repository.updateSupervisorControlObservation(session, row, editedText.ifEmpty { null })
            }.onSuccess {
                saving = false
                editing = null
                refresh += 1
            }.onFailure {
                saving = false
                saveError = if (it is retrofit2.HttpException && it.code() == 409) {
                    "La observacion cambio en el servidor. Actualice la consulta antes de reintentar."
                } else it.message ?: "No fue posible actualizar la observacion"
            }
        }
    }
}

@Composable
private fun ControlWorkerDailyChart(
    worker: ControlSupervisedWorkerOut,
    supervisorId: Int,
    selected: ControlDaySelection?,
    dayRows: List<ControlBitacoraOut>,
    dayError: String?,
    onSelect: (ControlDaySelection) -> Unit,
    onOpenEditor: (ControlBitacoraOut) -> Unit,
) {
    Text("${worker.codigo} · ${worker.nombre_completo}", style = MaterialTheme.typography.titleMedium)
    Text("Areas: ${worker.areas.joinToString()}")
    Text("Tiempo trabajado: ${formatControlMinutes(worker.total_minutos)} · Incompletas: ${worker.jornadas_incompletas}")
    val max = worker.dias.maxOfOrNull { it.minutos_trabajados }?.coerceAtLeast(8 * 60) ?: 8 * 60
    Row(Modifier.fillMaxWidth().height(180.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.width(34.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text("${max / 60}h"); Text("8h"); Text("4h"); Text("0h")
        }
        Row(
            Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.Bottom
        ) {
            worker.dias.forEach { day ->
                ControlDayBar(
                    day, max,
                    selected?.supervisedId == worker.id_participante && selected.date == day.fecha
                ) { onSelect(ControlDaySelection(supervisorId, worker.id_participante, day.fecha)) }
            }
        }
    }
    if (selected?.supervisedId == worker.id_participante) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Registros del ${selected.date}", style = MaterialTheme.typography.titleMedium)
            dayError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (dayRows.isEmpty() && dayError == null) Text("No hay registros tipo 4 o 5 para esta fecha")
            dayRows.forEach { row ->
                OutlinedButton(
                    onClick = { onOpenEditor(row) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text("Bitacora ${row.id_bitacora} · Tipo ${row.tipo_anotacion}")
                        Text(formatControlTimestamp(row.timestamp_min, ZoneId.of("America/Bogota")))
                        Text("Observacion: ${row.observaciones.orEmpty().ifEmpty { "(vacia)" }}")
                    }
                }
            }
        }
    }
    Text("Azul claro: inferior a la jornada · Azul oscuro: jornada completa")
    Text("Naranja: superior a la jornada · Rojo: domingo o festivo")
}

@Composable
private fun ControlDayBar(day: WorkerDayOut, max: Int, selected: Boolean, onClick: () -> Unit) {
    val segments = workerBarSegments(day)
    Column(
        Modifier.width(38.dp).fillMaxHeight().clickable(onClick = onClick).padding(horizontal = 3.dp)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        if (segments.lightMinutes > 0) Box(Modifier.width(24.dp).height((130f * segments.lightMinutes / max).dp).background(WorkerLightBlue))
        if (segments.ordinaryMinutes > 0) Box(Modifier.width(24.dp).height((130f * segments.ordinaryMinutes / max).dp).background(WorkerDarkBlue))
        if (segments.additionalMinutes > 0) Box(Modifier.width(24.dp).height((130f * segments.additionalMinutes / max).dp).background(WorkerOvertimeOrange))
        if (segments.specialMinutes > 0) Box(Modifier.width(24.dp).height((130f * segments.specialMinutes / max).dp).background(WorkerSundayHolidayRed))
        if (day.registro_incompleto) Text("!", color = MaterialTheme.colorScheme.error)
        Text(LocalDate.parse(day.fecha).dayOfMonth.toString())
    }
}

private fun formatControlMinutes(minutes: Int): String = "${minutes / 60} h ${minutes % 60} min"

internal fun formatControlTimestamp(minutes: Long, zone: ZoneId): String =
    Instant.ofEpochSecond(minutes * 60).atZone(zone).toLocalDateTime().toString()
