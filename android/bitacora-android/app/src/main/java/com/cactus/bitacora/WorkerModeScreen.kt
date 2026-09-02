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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.biometric.FaceIdentificationTarget
import com.cactus.bitacora.biometric.technical.FaceFlowMode
import com.cactus.bitacora.biometric.technical.FaceTechnicalScreen
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.WorkerDayOut
import com.cactus.bitacora.model.WorkerSessionOut
import com.cactus.bitacora.model.WorkerTimeOut
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private enum class WorkerView { IDENTIFY, MENU, TIME, FACE }

@Composable
internal fun WorkerModeScreen(repository: BitacoraRepository, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf(WorkerView.IDENTIFY) }
    var code by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<WorkerSessionOut?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun identify(reference: String) {
        scope.launch {
            runCatching { repository.identifyWorker(reference) }
                .onSuccess { session = it; code = it.codigo; error = null; view = WorkerView.MENU }
                .onFailure { error = it.message ?: "No fue posible identificar al trabajador" }
        }
    }
    if (view == WorkerView.FACE) {
        FaceTechnicalScreen(
            target = FaceIdentificationTarget.EMPLEADO,
            mode = FaceFlowMode.IDENTIFICATION,
            onConfirmed = { identify(it.participantCode) },
            onEnrollmentComplete = {}, onTestRecognition = {}, onUseQr = { view = WorkerView.IDENTIFY },
            onCancel = { view = WorkerView.IDENTIFY }
        )
        return
    }
    val active = session
    if (active == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Identificación del trabajador", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(code, { code = it }, label = { Text("Código del trabajador") }, modifier = Modifier.fillMaxWidth())
            Button({ identify(code.trim()) }, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
            Button({ view = WorkerView.FACE }, modifier = Modifier.fillMaxWidth()) { Text("Reconocer rostro") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onExit, modifier = Modifier.fillMaxWidth()) { Text("Volver al menú principal") }
        }
        return
    }
    if (view == WorkerView.TIME) {
        WorkerTimeScreen(repository, active) { view = WorkerView.MENU }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Trabajador", style = MaterialTheme.typography.headlineSmall)
        Text("${active.codigo} · ${active.nombre_completo}", style = MaterialTheme.typography.titleMedium)
        Button({ view = WorkerView.TIME }, modifier = Modifier.fillMaxWidth()) { Text("Tiempo trabajado") }
        OutlinedButton({ session = null; code = ""; view = WorkerView.IDENTIFY }, modifier = Modifier.fillMaxWidth()) { Text("Cambiar trabajador") }
        OutlinedButton(onExit, modifier = Modifier.fillMaxWidth()) { Text("Salir del módulo") }
    }
}

@Composable
private fun WorkerTimeScreen(repository: BitacoraRepository, session: WorkerSessionOut, onBack: () -> Unit) {
    val zone = remember { ZoneId.of("America/Bogota") }
    val today = remember { LocalDate.now(zone) }
    var period by remember { mutableStateOf(YearMonth.from(today)) }
    var data by remember { mutableStateOf<WorkerTimeOut?>(null) }
    var selected by remember { mutableStateOf<WorkerDayOut?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(period) {
        runCatching { repository.workerTime(session, period.year, period.monthValue) }
            .onSuccess { data = it; selected = it.dias.firstOrNull { day -> day.fecha == today.toString() }; error = null }
            .onFailure { error = it.message; data = null }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tiempo trabajado", style = MaterialTheme.typography.headlineSmall)
        Text("${session.codigo} · ${session.nombre_completo}")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            OutlinedButton({ period = period.minusMonths(1) }) { Text("Anterior") }
            Text("${period.month.getDisplayName(TextStyle.FULL, Locale("es"))} ${period.year}")
            OutlinedButton({ period = period.plusMonths(1) }) { Text("Siguiente") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ period = period.minusYears(1) }, modifier = Modifier.weight(1f)) { Text("Año anterior") }
            OutlinedButton({ period = YearMonth.from(today) }, modifier = Modifier.weight(1f)) { Text("Periodo actual") }
            OutlinedButton({ period = period.plusYears(1) }, modifier = Modifier.weight(1f)) { Text("Año siguiente") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val days = data?.dias.orEmpty()
        if (days.isEmpty() && error == null) Text("No hay registros para este período")
        val max = days.maxOfOrNull { it.minutos_trabajados }?.coerceAtLeast(8 * 60) ?: 8 * 60
        Row(Modifier.fillMaxWidth().height(220.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Text("${max / 60}h"); Text("8h"); Text("4h"); Text("0h")
            }
            Row(Modifier.weight(1f).fillMaxHeight().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.Bottom) {
                days.forEach { day ->
                    val segments = workerBarSegments(day)
                    Column(Modifier.width(38.dp).fillMaxHeight().clickable { selected = day }.padding(horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        if (segments.lightMinutes > 0) Box(Modifier.width(24.dp).height((160f * segments.lightMinutes / max).dp).background(WorkerLightBlue))
                        if (segments.ordinaryMinutes > 0) Box(Modifier.width(24.dp).height((160f * segments.ordinaryMinutes / max).dp).background(WorkerDarkBlue))
                        if (segments.additionalMinutes > 0) Box(Modifier.width(24.dp).height((160f * segments.additionalMinutes / max).dp).background(WorkerOvertimeOrange))
                        if (segments.specialMinutes > 0) Box(Modifier.width(24.dp).height((160f * segments.specialMinutes / max).dp).background(WorkerSundayHolidayRed))
                        if (day.registro_incompleto) Text("!", color = MaterialTheme.colorScheme.error)
                        Text(LocalDate.parse(day.fecha).dayOfMonth.toString())
                        if (day.fecha == today.toString()) Text("Hoy", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
        Text("Azul claro: inferior a la jornada · Azul oscuro: jornada completa")
        Text("Naranja: superior a la jornada · Rojo: domingo o festivo")
        Text("Lunes a viernes: 8 h · Sábado: 4 h · ! Registro incompleto")
        selected?.let { day ->
            val selectedIndex = days.indexOf(day)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ selected = days.getOrNull(selectedIndex - 1) }, enabled = selectedIndex > 0) { Text("Día anterior") }
                OutlinedButton({ selected = days.getOrNull(selectedIndex + 1) }, enabled = selectedIndex in 0 until days.lastIndex) { Text("Día siguiente") }
            }
            val limit = if (day.domingo || day.festivo) 0 else if (day.sabado) 240 else 480
            val ordinary = if (limit == 0) 0 else day.minutos_trabajados.coerceAtMost(limit)
            val additional = if (limit == 0) 0 else (day.minutos_trabajados - limit).coerceAtLeast(0)
            Text("${day.fecha}: ${day.minutos_trabajados / 60} h ${day.minutos_trabajados % 60} min", style = MaterialTheme.typography.titleMedium)
            Text("Día de semana: ${day.dia_semana} · Límite: ${limit / 60} h")
            Text("Tiempo ordinario: ${ordinary / 60} h ${ordinary % 60} min")
            Text("Tiempo superior al límite: ${additional / 60} h ${additional % 60} min")
            if (day.domingo) Text("Domingo")
            if (day.festivo) Text("Festivo: ${day.nombre_festivo.orEmpty()}")
            if (day.registro_incompleto) Text("Registro incompleto", color = MaterialTheme.colorScheme.error)
            if (day.eventos.isEmpty()) Text("Sin entradas ni salidas") else day.eventos.forEach { event ->
                val time = Instant.ofEpochSecond(event.timestamp_min * 60L).atZone(zone).toLocalTime()
                Text("${if (event.tipo_anotacion == 4) "Entrada" else "Salida"}: $time${event.inconsistencia?.let { " · $it" }.orEmpty()}")
            }
        }
        OutlinedButton(onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver a Trabajador") }
    }
}
