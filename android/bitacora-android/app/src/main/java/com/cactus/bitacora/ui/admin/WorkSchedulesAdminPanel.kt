package com.cactus.bitacora.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.cactus.bitacora.R
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.WorkScheduleDetailIn
import com.cactus.bitacora.model.WorkScheduleIn
import com.cactus.bitacora.model.WorkScheduleOut
import kotlinx.coroutines.launch

internal const val WORK_SCHEDULES_LABEL = "Jornadas de trabajo"
internal const val WORK_SCHEDULE_VIEW_CONTENT_DESCRIPTION = "Ver jornada"
private val scheduleDays = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
internal val SCHEDULE_QUARTER_MINUTES = listOf(0, 15, 30, 45)
private val restTypes = listOf("Sin descanso", "No remunerado", "Remunerado")

internal fun weeklyObjectiveHours(totalMinutes: Int): Int = totalMinutes / 60

internal fun weeklyObjectiveMinutePart(totalMinutes: Int): Int = totalMinutes % 60

internal fun weeklyObjectiveMinutes(hours: Int, minutes: Int): Int = (hours * 60) + minutes

internal fun scheduleProgrammedMinutes(value: WorkScheduleDetailIn): Int {
    if (!value.es_laborable) return 0
    val duration = (value.hora_salida_min ?: 0) +
        (if (value.salida_dia_siguiente) 1440 else 0) - (value.hora_entrada_min ?: 0)
    return duration - if (value.descanso_remunerado) 0 else value.descanso_min
}

internal fun scheduleWeeklyMinutes(values: List<WorkScheduleDetailIn>) = values.sumOf(::scheduleProgrammedMinutes)

internal fun scheduleFormError(value: WorkScheduleIn): String? = when {
    value.codigo_jornada.trim().isEmpty() -> "El código es obligatorio"
    value.nombre_jornada.trim().isEmpty() -> "El nombre es obligatorio"
    value.tolerancia_entrada_min !in 0..240 || value.tolerancia_salida_min !in 0..240 -> "Tolerancias inválidas"
    !value.aplica_control_horario && (value.minutos_objetivo_semana != 0 || value.detalles.isNotEmpty()) -> "Sin control horario requiere objetivo cero y sin tramos"
    value.aplica_control_horario && value.minutos_objetivo_semana <= 0 -> "El objetivo debe ser positivo"
    value.activo && value.aplica_control_horario && scheduleWeeklyMinutes(value.detalles) != value.minutos_objetivo_semana -> "Una jornada activa debe coincidir con el objetivo semanal"
    else -> null
}

private fun minutes(value: Int) = "${value / 60} h ${"%02d".format(value % 60)} min"
private fun timeText(value: Int?) = value?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "--:--"
internal fun workScheduleStatusContentDescription(active: Boolean) =
    if (active) "Jornada activa. Inactivar jornada" else "Jornada inactiva. Activar jornada"

@Composable
private fun WorkScheduleStatusIcon(active: Boolean) {
    val color = if (active) Color(0xFF2E7D32) else Color(0xFF616161)
    Canvas(
        modifier = Modifier
            .size(20.dp)
            .semantics { contentDescription = workScheduleStatusContentDescription(active) },
    ) {
        val stroke = size.minDimension * 0.12f
        val headRadius = size.minDimension * 0.16f
        val headX = if (active) size.width * 0.60f else size.width / 2f
        val headY = size.height * 0.22f
        drawCircle(color, headRadius, androidx.compose.ui.geometry.Offset(headX, headY))
        if (active) {
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.42f), androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.60f), stroke, StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.43f, size.height * 0.51f), androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.47f), stroke, StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.60f), androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.88f), stroke, StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.60f), androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.80f), stroke, StrokeCap.Round)
        } else {
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.40f), androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.70f), stroke, StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.52f), androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.52f), stroke, StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.70f), androidx.compose.ui.geometry.Offset(size.width * 0.27f, size.height * 0.92f), stroke, StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width / 2f, size.height * 0.70f), androidx.compose.ui.geometry.Offset(size.width * 0.73f, size.height * 0.92f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun ChoiceDropdown(
    label: String,
    selected: String,
    options: List<String>,
    enabled: Boolean = true,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            TextButton({ expanded = true }, enabled = enabled) { Text(selected) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = {
                        expanded = false
                        onSelect(option)
                    })
                }
            }
        }
    }
}

@Composable
private fun QuarterTimeDropdown(label: String, value: Int?, enabled: Boolean, onChange: (Int) -> Unit) {
    val hour = (value ?: 0) / 60
    val minute = (value ?: 0) % 60
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChoiceDropdown(label, "%02d".format(hour), (0..23).map { "%02d".format(it) }, enabled) {
            onChange(it.toInt() * 60 + minute)
        }
        ChoiceDropdown("Minutos", "%02d".format(minute), SCHEDULE_QUARTER_MINUTES.map { "%02d".format(it) }, enabled) {
            onChange(hour * 60 + it.toInt())
        }
    }
}

private fun restType(detail: WorkScheduleDetailIn): String = when {
    detail.descanso_min == 0 -> restTypes[0]
    detail.descanso_remunerado -> restTypes[2]
    else -> restTypes[1]
}

@Composable
fun WorkSchedulesAdminPanel(repository: BitacoraRepository) {
    val scope = rememberCoroutineScope()
    var schedules by remember { mutableStateOf<List<WorkScheduleOut>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf<Boolean?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<WorkScheduleOut?>(null) }
    var creating by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<WorkScheduleOut?>(null) }
    fun load() {
        loading = true
        scope.launch {
            runCatching { repository.workSchedules(search, activeFilter) }
                .onSuccess { schedules = it; error = null }
                .onFailure { error = it.message ?: "No fue posible consultar jornadas" }
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }
    Text(WORK_SCHEDULES_LABEL, style = MaterialTheme.typography.titleMedium)
    Text("Operación en línea. No modifica asignaciones empleado-área.")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(search, { search = it }, label = { Text("Buscar jornada por código o nombre") }, modifier = Modifier.weight(1f), singleLine = true)
        IconButton(onClick = ::load, enabled = !loading, modifier = Modifier.size(36.dp)) {
            Icon(painterResource(R.drawable.ic_admin_search), "Buscar jornadas", tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(activeFilter == null, { activeFilter = null; load() }, { Text("Todas") })
        FilterChip(activeFilter == true, { activeFilter = true; load() }, { Text("Activas") })
        FilterChip(activeFilter == false, { activeFilter = false; load() }, { Text("Inactivas") })
    }
    Button(onClick = { creating = true }, enabled = !loading) { Text("Crear jornada") }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    schedules.forEach { item ->
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text("${item.codigo_jornada} · ${item.nombre_jornada}", style = MaterialTheme.typography.titleSmall)
            Text("${if (item.activo) "Activa" else "Inactiva"} · ${minutes(item.total_programado_semana)} / ${minutes(item.minutos_objetivo_semana)}")
            if (item.referenciada_activa) Text("Asignada: solo nombre y observaciones editables", color = Color(0xFF9A6700))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = { viewing = item }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_participant_view),
                        WORK_SCHEDULE_VIEW_CONTENT_DESCRIPTION,
                        tint = Color(0xFF1976D2),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = { editor = item }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_participant_edit),
                        "Editar jornada",
                        tint = Color(0xFFF9A825),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = {
                    scope.launch {
                        runCatching { repository.setWorkScheduleStatus(item.id_jornada, !item.activo) }
                            .onSuccess { message = "Estado actualizado"; load() }
                            .onFailure { error = it.message ?: "No fue posible cambiar el estado" }
                    }
                },
                    enabled = !(item.referenciada_activa && item.activo),
                    modifier = Modifier.size(36.dp),
                ) {
                    WorkScheduleStatusIcon(active = item.activo)
                }
            }
        }
    }
    viewing?.let { value ->
        AlertDialog(onDismissRequest = { viewing = null }, confirmButton = { TextButton({ viewing = null }) { Text("Cerrar") } },
            title = { Text(value.nombre_jornada) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${value.codigo_jornada} · ${minutes(value.total_programado_semana)}")
                value.detalles.forEach { d -> Text("${scheduleDays[d.dia_semana_num - 1]} · tramo ${d.numero_tramo}: ${if (d.es_laborable) "${timeText(d.hora_entrada_min)}-${timeText(d.hora_salida_min)} · ${minutes(d.minutos_programados)}" else "No laborable"}") }
            } })
    }
    if (creating || editor != null) WorkScheduleEditor(editor, { creating = false; editor = null }) { payload ->
        scope.launch {
            runCatching { if (editor == null) repository.createWorkSchedule(payload) else repository.updateWorkSchedule(editor!!.id_jornada, payload) }
                .onSuccess { message = "Jornada guardada correctamente"; creating = false; editor = null; load() }
                .onFailure { error = it.message ?: "No fue posible guardar la jornada" }
        }
    }
}

@Composable
private fun WorkScheduleEditor(initial: WorkScheduleOut?, dismiss: () -> Unit, save: (WorkScheduleIn) -> Unit) {
    val locked = initial?.referenciada_activa == true
    var code by remember { mutableStateOf(initial?.codigo_jornada.orEmpty()) }
    var name by remember { mutableStateOf(initial?.nombre_jornada.orEmpty()) }
    var objectiveHours by remember { mutableStateOf(weeklyObjectiveHours(initial?.minutos_objetivo_semana ?: 0).toString()) }
    var objectiveMinutes by remember { mutableStateOf(weeklyObjectiveMinutePart(initial?.minutos_objetivo_semana ?: 0)) }
    var toleranceIn by remember { mutableStateOf(initial?.tolerancia_entrada_min?.toString() ?: "0") }
    var toleranceOut by remember { mutableStateOf(initial?.tolerancia_salida_min?.toString() ?: "0") }
    var from by remember { mutableStateOf(initial?.vigencia_desde ?: "2026-01-01") }
    var until by remember { mutableStateOf(initial?.vigencia_hasta.orEmpty()) }
    var active by remember { mutableStateOf(initial?.activo ?: false) }
    var control by remember { mutableStateOf(initial?.aplica_control_horario ?: true) }
    var notes by remember { mutableStateOf(initial?.observaciones.orEmpty()) }
    var details by remember { mutableStateOf(initial?.detalles?.map { WorkScheduleDetailIn(it.id_detalle, it.dia_semana_num, it.numero_tramo, it.es_laborable, it.hora_entrada_min, it.hora_salida_min, it.salida_dia_siguiente, it.descanso_min, it.descanso_remunerado, it.observaciones) } ?: scheduleDays.indices.map { WorkScheduleDetailIn(dia_semana_num = it + 1, numero_tramo = 1, es_laborable = false) }) }
    var selectedDay by remember { mutableStateOf(1) }
    var selectedSegment by remember { mutableStateOf(1) }
    var expandedDays by remember { mutableStateOf(setOf<Int>()) }
    fun payload() = WorkScheduleIn(code.trim(), name.trim(), weeklyObjectiveMinutes(objectiveHours.toIntOrNull() ?: -1, objectiveMinutes), toleranceIn.toIntOrNull() ?: -1, toleranceOut.toIntOrNull() ?: -1, from, until.trim().ifBlank { null }, active, control, notes.trim().ifBlank { null }, details.sortedWith(compareBy({ it.dia_semana_num }, { it.numero_tramo })))
    fun dayDetails(day: Int) = details.filter { it.dia_semana_num == day }.sortedBy { it.numero_tramo }
    fun setDayWorking(day: Int, working: Boolean) {
        details = details.filterNot { it.dia_semana_num == day } + WorkScheduleDetailIn(
            dia_semana_num = day, numero_tramo = 1, es_laborable = working,
            hora_entrada_min = if (working) 480 else null, hora_salida_min = if (working) 960 else null,
        )
        selectedSegment = 1
    }
    fun updateSelected(change: (WorkScheduleDetailIn) -> WorkScheduleDetailIn) {
        details = details.map { detail ->
            if (detail.dia_semana_num == selectedDay && detail.numero_tramo == selectedSegment) change(detail) else detail
        }
    }
    val payload = payload()
    val total = scheduleWeeklyMinutes(details)
    val validation = scheduleFormError(payload)
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (initial == null) "Crear jornada" else "Editar jornada") },
        confirmButton = { Button({ save(payload) }, enabled = validation == null) { Text("Guardar") } },
        dismissButton = { TextButton(dismiss) { Text("Cancelar") } },
        text = { Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (locked) Text("Esta jornada está asignada: solo puede cambiar nombre y observaciones.", color = Color(0xFF9A6700))
            OutlinedTextField(code, { code = it.uppercase() }, label = { Text("Código") }, enabled = !locked)
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(objectiveHours, { objectiveHours = it.filter(Char::isDigit) }, label = { Text("Horas") }, modifier = Modifier.weight(1f), enabled = !locked)
                ChoiceDropdown("Minutos", "%02d".format(objectiveMinutes), SCHEDULE_QUARTER_MINUTES.map { "%02d".format(it) }, !locked) {
                    objectiveMinutes = it.toInt()
                }
            }
            Row { OutlinedTextField(toleranceIn, { toleranceIn = it.filter(Char::isDigit) }, label = { Text("Tol. entrada") }, modifier = Modifier.weight(1f), enabled = !locked); OutlinedTextField(toleranceOut, { toleranceOut = it.filter(Char::isDigit) }, label = { Text("Tol. salida") }, modifier = Modifier.weight(1f), enabled = !locked) }
            OutlinedTextField(from, { from = it }, label = { Text("Vigencia desde AAAA-MM-DD") }, enabled = !locked)
            OutlinedTextField(until, { until = it }, label = { Text("Vigencia hasta") }, enabled = !locked)
            ChoiceDropdown("Control horario", if (control) "Sí" else "No", listOf("Sí", "No"), !locked) { selected ->
                control = selected == "Sí"
                details = if (control) scheduleDays.indices.map {
                    WorkScheduleDetailIn(dia_semana_num = it + 1, numero_tramo = 1, es_laborable = false)
                } else emptyList()
            }
            ChoiceDropdown("Estado", if (active) "Activa" else "Inactiva", listOf("Activa", "Inactiva"), !locked) {
                active = it == "Activa"
            }
            OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, minLines = 2)
            if (control) {
                Text("Composición de la jornada", style = MaterialTheme.typography.titleSmall)
                scheduleDays.forEachIndexed { index, day ->
                    val dayNumber = index + 1
                    val values = dayDetails(dayNumber)
                    val working = values.any { it.es_laborable }
                    TextButton({
                        selectedDay = dayNumber
                        expandedDays = if (dayNumber in expandedDays) expandedDays - dayNumber else expandedDays + dayNumber
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("${if (dayNumber in expandedDays) "▼" else "▶"} $day${if (working) " · ${values.count { it.es_laborable }} tramo(s)" else " · No laborable"}")
                    }
                    if (dayNumber in expandedDays) values.filter { it.es_laborable }.forEach { detail ->
                        TextButton({ selectedDay = dayNumber; selectedSegment = detail.numero_tramo }, modifier = Modifier.padding(start = 18.dp)) {
                            Text("└── Tramo ${detail.numero_tramo}: ${timeText(detail.hora_entrada_min)}–${timeText(detail.hora_salida_min)}${if (detail.salida_dia_siguiente) " (día siguiente)" else ""} · descanso ${minutes(detail.descanso_min)}")
                        }
                    }
                }
                val selectedValues = dayDetails(selectedDay)
                val selectedDetail = selectedValues.firstOrNull { it.numero_tramo == selectedSegment } ?: selectedValues.firstOrNull()
                ChoiceDropdown("Día de la semana", scheduleDays[selectedDay - 1], scheduleDays, !locked) { selected ->
                    selectedDay = scheduleDays.indexOf(selected) + 1
                    selectedSegment = dayDetails(selectedDay).firstOrNull()?.numero_tramo ?: 1
                }
                ChoiceDropdown("Condición del día", if (selectedValues.any { it.es_laborable }) "Laborable" else "No laborable", listOf("Laborable", "No laborable"), !locked) {
                    setDayWorking(selectedDay, it == "Laborable")
                }
                selectedDetail?.takeIf { it.es_laborable }?.let { detail ->
                    val workingSegments = selectedValues.filter { it.es_laborable }
                    ChoiceDropdown("Tramo", "Tramo ${detail.numero_tramo}", workingSegments.map { "Tramo ${it.numero_tramo}" }, !locked) {
                        selectedSegment = it.removePrefix("Tramo ").toInt()
                    }
                    QuarterTimeDropdown("Entrada", detail.hora_entrada_min, !locked) { updateSelected { current -> current.copy(hora_entrada_min = it) } }
                    QuarterTimeDropdown("Salida", detail.hora_salida_min, !locked) { updateSelected { current -> current.copy(hora_salida_min = it) } }
                    ChoiceDropdown("Termina al día siguiente", if (detail.salida_dia_siguiente) "Sí" else "No", listOf("No", "Sí"), !locked) {
                        updateSelected { current -> current.copy(salida_dia_siguiente = it == "Sí") }
                    }
                    ChoiceDropdown("Tipo de descanso", restType(detail), restTypes, !locked) { selected ->
                        updateSelected { current -> when (selected) {
                            "Sin descanso" -> current.copy(descanso_min = 0, descanso_remunerado = false)
                            "No remunerado" -> current.copy(descanso_min = if (current.descanso_min == 0) 60 else current.descanso_min, descanso_remunerado = false)
                            else -> current.copy(descanso_min = if (current.descanso_min == 0) 60 else current.descanso_min, descanso_remunerado = true)
                        } }
                    }
                    if (detail.descanso_min > 0) ChoiceDropdown("Duración del descanso", minutes(detail.descanso_min), (0..240 step 15).map(::minutes), !locked) { selected ->
                        updateSelected { current -> current.copy(descanso_min = (0..240 step 15).first { minutes(it) == selected }) }
                    }
                    if (!locked) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton({
                            val next = (workingSegments.maxOfOrNull { it.numero_tramo } ?: 0) + 1
                            details = details + WorkScheduleDetailIn(dia_semana_num = selectedDay, numero_tramo = next, es_laborable = true, hora_entrada_min = 480, hora_salida_min = 960)
                            selectedSegment = next
                        }) { Text("Agregar tramo") }
                        TextButton({
                            if (workingSegments.size == 1) setDayWorking(selectedDay, false) else {
                                val remaining = workingSegments.filterNot { it.numero_tramo == detail.numero_tramo }.mapIndexed { position, item -> item.copy(numero_tramo = position + 1) }
                                details = details.filterNot { it.dia_semana_num == selectedDay } + remaining
                                selectedSegment = remaining.first().numero_tramo
                            }
                        }) { Text("Retirar tramo") }
                    }
                }
            }
            if (false && control) scheduleDays.forEachIndexed { index, day ->
                val number = index + 1
                val dayDetails = details.filter { it.dia_semana_num == number }
                val working = dayDetails.any { it.es_laborable }
                Text(day, style = MaterialTheme.typography.titleSmall)
                Row { Text("Laborable", Modifier.weight(1f)); Switch(working, { enabled -> if (!locked) details = details.filterNot { it.dia_semana_num == number } + WorkScheduleDetailIn(dia_semana_num = number, numero_tramo = 1, es_laborable = enabled, hora_entrada_min = if (enabled) 480 else null, hora_salida_min = if (enabled) 960 else null) }, enabled = !locked) }
                dayDetails.filter { it.es_laborable }.forEach { d ->
                    OutlinedTextField("${d.hora_entrada_min ?: ""},${d.hora_salida_min ?: ""},${d.descanso_min}", { raw -> if (!locked) { val values = raw.split(',').mapNotNull { it.trim().toIntOrNull() }; if (values.size == 3) details = details.map { if (it == d) d.copy(hora_entrada_min = values[0], hora_salida_min = values[1], descanso_min = values[2]) else it } } }, label = { Text("Tramo ${d.numero_tramo}: entrada,salida,descanso") }, enabled = !locked)
                    Row {
                        Text("Salida al dÃ­a siguiente", Modifier.weight(1f))
                        Switch(d.salida_dia_siguiente, { enabled ->
                            if (!locked) details = details.map { if (it == d) d.copy(salida_dia_siguiente = enabled) else it }
                        }, enabled = !locked)
                    }
                    Row {
                        Text("Descanso remunerado", Modifier.weight(1f))
                        Switch(d.descanso_remunerado, { enabled ->
                            if (!locked) details = details.map { if (it == d) d.copy(descanso_remunerado = enabled) else it }
                        }, enabled = !locked)
                    }
                    if (!locked) TextButton({
                        details = if (dayDetails.size == 1) {
                            details.filterNot { it == d } + WorkScheduleDetailIn(
                                dia_semana_num = number, numero_tramo = 1, es_laborable = false
                            )
                        } else {
                            val remaining = dayDetails.filterNot { it == d }.mapIndexed { position, value ->
                                value.copy(numero_tramo = position + 1)
                            }
                            details.filterNot { it.dia_semana_num == number } + remaining
                        }
                    }) { Text("Quitar tramo") }
                }
                if (working && !locked) TextButton({
                    val next = (dayDetails.maxOfOrNull { it.numero_tramo } ?: 0) + 1
                    details = details + WorkScheduleDetailIn(
                        dia_semana_num = number, numero_tramo = next, es_laborable = true,
                        hora_entrada_min = 480, hora_salida_min = 960
                    )
                }) { Text("Agregar tramo") }
            }
            Text("Suma semanal: ${minutes(total)} · Objetivo: ${minutes(payload.minutos_objetivo_semana.coerceAtLeast(0))}")
            if (total != payload.minutos_objetivo_semana && control) Text("Advertencia: la suma difiere del objetivo semanal", color = Color(0xFF9A6700))
            validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } })
}
