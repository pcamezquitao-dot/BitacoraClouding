package com.cactus.bitacora.feature.bpm

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.data.local.BpmTaskLocalEntity
import com.cactus.bitacora.model.SupervisorAreaOut
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

private val TASK_STATES = listOf("PENDIENTE", "EN_EJECUCION", "COMPLETADA", "CANCELADA")

@Composable
internal fun SupervisorRhBpmScreen(
    actorCode: String,
    areas: List<SupervisorAreaOut>,
    onBack: () -> Unit
) {
    val context = LocalContext.current.applicationContext
    val repository = remember { BpmRepository(context) }
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf<List<BpmTaskLocalEntity>>(emptyList()) }
    var filter by remember { mutableStateOf("PENDIENTE") }
    var message by remember { mutableStateOf("Actualice para consultar las tareas asignadas") }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() { tasks = repository.tasks(actorCode) }
    fun run(action: suspend () -> String) {
        busy = true
        scope.launch {
            message = try { action() } catch (error: Exception) {
                error.message ?: "No fue posible completar la accion"
            }
            load(); busy = false
        }
    }
    LaunchedEffect(actorCode) { load() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Tareas BPM - Supervisor RH", style = MaterialTheme.typography.headlineSmall)
        Text("Participante: $actorCode")
        Text("Solo el servidor confirma la decision y sus registros asociados.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = { run { repository.refresh(actorCode); "Bandeja actualizada" } }) {
                Text("Actualizar")
            }
            Button(enabled = !busy, onClick = { run {
                val value = repository.syncPending()
                runCatching { repository.refresh(actorCode) }
                "${value.confirmed} confirmadas; ${value.conflicts} conflictos; ${value.rejected} rechazadas"
            } }) { Text("Sincronizar") }
            OutlinedButton(onClick = onBack) { Text("Volver") }
        }
        Text(message, color = if (message.contains("conflict", true) || message.contains("rechaz", true))
            MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Text("Filtrar por estado")
        TASK_STATES.forEach { state ->
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { filter = state }) {
                Text((if (filter == state) "✓ " else "") + state)
            }
        }
        val visible = tasks.filter { it.state == filter }
        Text("${visible.size} tarea(s) $filter")
        visible.forEach { task ->
            SupervisorRhTaskCard(task, actorCode, areas, repository, busy, ::run)
        }
    }
}

@Composable
private fun SupervisorRhTaskCard(
    task: BpmTaskLocalEntity,
    actorCode: String,
    areas: List<SupervisorAreaOut>,
    repository: BpmRepository,
    busy: Boolean,
    run: (suspend () -> String) -> Unit
) {
    val context = LocalContext.current
    var detail by remember(task.clientUuid) { mutableStateOf<BpmTaskContext?>(null) }
    var history by remember(task.clientUuid) { mutableStateOf<String?>(null) }
    var result by remember(task.clientUuid) { mutableStateOf("") }
    var observations by remember(task.clientUuid) { mutableStateOf("") }
    var noveltyType by remember(task.clientUuid) { mutableStateOf(1) }
    var areaId by remember(task.clientUuid) { mutableStateOf(areas.firstOrNull()?.id_area) }
    var startDate by remember(task.clientUuid) { mutableStateOf(LocalDate.now().toString()) }
    var endDate by remember(task.clientUuid) { mutableStateOf(LocalDate.now().toString()) }
    var startTime by remember(task.clientUuid) { mutableStateOf("") }
    var endTime by remember(task.clientUuid) { mutableStateOf("") }
    var supports by remember(task.clientUuid) { mutableStateOf<List<BpmEvidenceCapture>>(emptyList()) }
    var supportMessage by remember(task.clientUuid) { mutableStateOf<String?>(null) }
    var confirm by remember(task.clientUuid) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.size > 20) {
            supportMessage = "Se permiten maximo 20 evidencias; seleccione nuevamente."
            return@rememberLauncherForActivityResult
        }
        val loaded = uris.mapNotNull { uri -> runCatching {
            val resolver = context.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: (uri.lastPathSegment ?: "evidencia")
            BpmEvidenceCapture(name, resolver.getType(uri) ?: "application/octet-stream",
                requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() })
        }.getOrNull() }
        supports = loaded
        supportMessage = if (loaded.size == uris.size) null else
            "No fue posible leer una o mas evidencias; revise la seleccion."
    }
    LaunchedEffect(task.clientUuid) { detail = repository.taskContext(task) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(detail?.processName ?: "Proceso BPM", style = MaterialTheme.typography.titleMedium)
            Text("Caso ${detail?.case?.remoteId ?: detail?.case?.clientUuid.orEmpty()}")
            Text("Actividad: ${task.stageName}")
            Text("Afectado: ${detail?.case?.affectedCode.orEmpty()}")
            Text("Responsable: ${task.responsibleCode}")
            Text("Estado: ${task.state} · ${syncLabel(task.syncState)}")
            task.dueAt?.let { Text("Vencimiento: $it") }
            detail?.instructions?.let { Text("Requerimiento: $it") }
            Text("Origen: ${detail?.case?.sourceType.orEmpty()} ${detail?.case?.sourceId.orEmpty()}")
            if (task.remoteId != null) OutlinedButton(onClick = { run {
                history = repository.caseDetailJson(actorCode, requireNotNull(detail?.case?.remoteId))
                "Detalle e historial actualizados"
            } }) { Text("Ver detalle, historial y evidencias") }
            history?.let { Text(it.take(1800), style = MaterialTheme.typography.bodySmall) }
            if (task.state == "PENDIENTE") Button(enabled = !busy, onClick = { run {
                repository.queueStart(actorCode, task); "Inicio guardado localmente; pendiente de sincronizacion"
            } }) { Text("Iniciar") }
            if (task.state == "EN_EJECUCION") {
                Text("Decision permitida")
                detail?.allowedResults.orEmpty().forEach { option ->
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { result = option }) {
                        Text((if (result == option) "✓ " else "") + option)
                    }
                }
                OutlinedTextField(observations, { observations = it }, Modifier.fillMaxWidth(),
                    label = { Text(if (result == "REQUIERE_CORRECCION") "Observaciones obligatorias" else "Observaciones") })
                Text("Novedad C08")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 2, 7).forEach { type -> OutlinedButton(onClick = { noveltyType = type }) {
                        Text((if (noveltyType == type) "✓" else "") + type)
                    } }
                }
                areas.forEach { area -> OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { areaId = area.id_area }) {
                    Text((if (areaId == area.id_area) "✓ " else "") + area.area)
                } }
                OutlinedTextField(startDate, { startDate = it }, Modifier.fillMaxWidth(), label = { Text("Fecha inicial AAAA-MM-DD") })
                OutlinedTextField(endDate, { endDate = it }, Modifier.fillMaxWidth(), label = { Text("Fecha final AAAA-MM-DD") })
                if (noveltyType == 7) {
                    OutlinedTextField(startTime, { startTime = it }, Modifier.fillMaxWidth(),
                        label = { Text("Hora inicial HH:MM") })
                    OutlinedTextField(endTime, { endTime = it }, Modifier.fillMaxWidth(),
                        label = { Text("Hora final HH:MM") })
                }
                OutlinedButton(onClick = { picker.launch("*/*") }) { Text("Evidencias (${supports.size})") }
                supportMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                supports.forEach { Text("• ${it.filename}") }
                val datesValid = runCatching {
                    !LocalDate.parse(endDate).isBefore(LocalDate.parse(startDate))
                }.getOrDefault(false)
                val timesValid = noveltyType != 7 || runCatching {
                    LocalTime.parse(startTime); LocalTime.parse(endTime); true
                }.getOrDefault(false)
                Button(enabled = !busy && result.isNotBlank() && areaId != null && datesValid && timesValid &&
                    (result != "REQUIERE_CORRECCION" || observations.isNotBlank()),
                    onClick = { confirm = true }) { Text("Revisar y confirmar") }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Confirmar atencion") },
        text = { Text("Decision: $result\nObservaciones: ${observations.ifBlank { "Sin observaciones" }}\nEvidencias: ${supports.size}\nNovedad: tipo $noveltyType, $startDate a $endDate") },
        dismissButton = { OutlinedButton(onClick = { confirm = false }) { Text("Cancelar") } },
        confirmButton = { Button(onClick = {
            confirm = false
            run {
                repository.queueComplete(actorCode, task, result, observations, supports,
                    C33NoveltyCapture(noveltyType, requireNotNull(areaId), startDate, endDate,
                        startTime.takeIf { noveltyType == 7 }, endTime.takeIf { noveltyType == 7 }))
                "Atencion guardada localmente; pendiente de confirmacion del servidor"
            }
        }) { Text("Confirmar") } }
    )
}
