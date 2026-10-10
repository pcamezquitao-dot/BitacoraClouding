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
import com.cactus.bitacora.data.local.BpmDefinitionLocalEntity
import com.cactus.bitacora.data.local.BpmCaseLocalEntity
import com.cactus.bitacora.data.local.BpmSyncState
import com.cactus.bitacora.data.local.BpmTaskLocalEntity
import kotlinx.coroutines.launch

@Composable
internal fun BpmMotorScreen(onExit: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repository = remember { BpmRepository(context) }
    val scope = rememberCoroutineScope()
    var actor by remember { mutableStateOf("") }
    var affected by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var definitions by remember { mutableStateOf<List<BpmDefinitionLocalEntity>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<BpmTaskLocalEntity>>(emptyList()) }
    var cases by remember { mutableStateOf<List<BpmCaseLocalEntity>>(emptyList()) }
    var selectedDefinition by remember { mutableStateOf<BpmDefinitionLocalEntity?>(null) }
    var message by remember { mutableStateOf("Identifique al participante y sincronice") }
    var busy by remember { mutableStateOf(false) }

    suspend fun loadLocal() {
        definitions = repository.definitions()
        selectedDefinition = selectedDefinition ?: definitions.firstOrNull()
        tasks = if (actor.isBlank()) emptyList() else repository.tasks(actor.trim().uppercase())
        cases = if (actor.isBlank()) emptyList() else repository.cases(actor.trim().uppercase())
    }
    LaunchedEffect(Unit) { loadLocal() }

    fun run(action: suspend () -> String) {
        busy = true
        scope.launch {
            message = try { action() } catch (error: Exception) {
                error.message ?: "No fue posible completar la acción"
            }
            loadLocal(); busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Motor BPM", style = MaterialTheme.typography.headlineSmall)
        Text("El trabajo sin conexión queda pendiente; solo el servidor puede confirmarlo.")
        OutlinedTextField(actor, { actor = it.uppercase() }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Código del participante") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && actor.isNotBlank(), onClick = {
                run {
                    repository.refresh(actor); "Definiciones y tareas confirmadas por el servidor"
                }
            }) { Text("Actualizar") }
            Button(enabled = !busy, onClick = {
                run {
                    val value = repository.syncPending()
                    runCatching { if (actor.isNotBlank()) repository.refresh(actor) }
                    "Sincronización: ${value.confirmed} confirmadas, ${value.conflicts} conflictos, ${value.rejected} rechazadas"
                }
            }) { Text("Sincronizar") }
            OutlinedButton(onClick = onExit) { Text("Salir") }
        }
        Text(message, color = if (message.contains("conflict", true) || message.contains("rechaz", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)

        Text("Abrir caso", style = MaterialTheme.typography.titleLarge)
        definitions.forEach { definition ->
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { selectedDefinition = definition }
            ) {
                Text((if (selectedDefinition?.processId == definition.processId) "✓ " else "") +
                    "${definition.name} v${definition.version}")
            }
        }
        OutlinedTextField(affected, { affected = it.uppercase() }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Participante afectado") }, singleLine = true)
        OutlinedTextField(subject, { subject = it }, modifier = Modifier.fillMaxWidth(),
            label = { Text("Asunto") })
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy && actor.isNotBlank() && affected.isNotBlank() && subject.isNotBlank() && selectedDefinition != null,
            onClick = {
                run {
                    val local = repository.queueOpenCase(actor, requireNotNull(selectedDefinition), subject, affected)
                    subject = ""
                    "Caso $local guardado localmente, pendiente de sincronización"
                }
            }
        ) { Text("Guardar apertura") }

        Text("Mis tareas", style = MaterialTheme.typography.titleLarge)
        if (tasks.isEmpty()) Text("No hay tareas locales. Actualice al recuperar conexión.")
        tasks.forEach { task ->
            BpmTaskCard(task, busy, onStart = {
                run {
                    repository.queueStart(actor, task)
                    "Inicio guardado localmente; pendiente de confirmación"
                }
            }, onComplete = { result, observations, supports ->
                run {
                    repository.queueComplete(actor, task, result, observations, supports)
                    "Finalización guardada localmente; sucesor provisional hasta sincronizar"
                }
            }, onReassign = { responsibleId, reason ->
                run {
                    repository.queueReassign(actor, task, responsibleId, reason)
                    "Reasignación guardada localmente; pendiente de confirmación"
                }
            })
        }

        Text("Administración de casos", style = MaterialTheme.typography.titleLarge)
        Text("Las acciones requieren autorización administrativa y motivo; el servidor vuelve a validarlas.")
        cases.forEach { case ->
            BpmCaseAdminCard(case, busy) { operation, reason ->
                run {
                    repository.queueCaseAdmin(actor, case, operation, reason)
                    "$operation guardada localmente; pendiente de confirmación"
                }
            }
        }
    }
}

@Composable
private fun BpmCaseAdminCard(
    case: BpmCaseLocalEntity,
    busy: Boolean,
    onAction: (String, String) -> Unit
) {
    var reason by remember(case.clientUuid) { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(case.subject, style = MaterialTheme.typography.titleMedium)
            Text("Caso ${case.remoteId ?: case.clientUuid} · ${case.state}")
            Text("Sincronización: ${syncLabel(case.syncState)}")
            OutlinedTextField(reason, { reason = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Motivo administrativo") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (case.state == "ABIERTO") {
                    OutlinedButton(enabled = !busy && reason.isNotBlank(),
                        onClick = { onAction("SUSPEND_CASE", reason) }) { Text("Suspender") }
                }
                if (case.state == "SUSPENDIDO") {
                    OutlinedButton(enabled = !busy && reason.isNotBlank(),
                        onClick = { onAction("RESUME_CASE", reason) }) { Text("Reanudar") }
                }
                if (case.state in setOf("ABIERTO", "SUSPENDIDO")) {
                    OutlinedButton(enabled = !busy && reason.isNotBlank(),
                        onClick = { onAction("CANCEL_CASE", reason) }) { Text("Cancelar") }
                }
            }
        }
    }
}

@Composable
private fun BpmTaskCard(
    task: BpmTaskLocalEntity,
    busy: Boolean,
    onStart: () -> Unit,
    onComplete: (String, String, List<BpmEvidenceCapture>) -> Unit,
    onReassign: (Int, String) -> Unit
) {
    val context = LocalContext.current
    var result by remember(task.clientUuid) { mutableStateOf("") }
    var observations by remember(task.clientUuid) { mutableStateOf("") }
    var supports by remember(task.clientUuid) { mutableStateOf<List<BpmEvidenceCapture>>(emptyList()) }
    var newResponsible by remember(task.clientUuid) { mutableStateOf("") }
    var reassignReason by remember(task.clientUuid) { mutableStateOf("") }
    val supportPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        supports = uris.take(20).mapNotNull { uri ->
            runCatching {
                val resolver = context.contentResolver
                val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                } ?: (uri.lastPathSegment ?: "soporte")
                val bytes = requireNotNull(resolver.openInputStream(uri)).use { it.readBytes() }
                BpmEvidenceCapture(name, resolver.getType(uri) ?: "application/octet-stream", bytes)
            }.getOrNull()
        }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.stageName, style = MaterialTheme.typography.titleMedium)
            Text("Estado funcional: ${task.state}")
            Text("Sincronización: ${syncLabel(task.syncState)}")
            if (task.provisional) Text("Provisional en este dispositivo", color = MaterialTheme.colorScheme.tertiary)
            task.dueAt?.let { Text("Vence: $it") }
            task.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (task.state == "PENDIENTE") {
                Button(enabled = !busy, onClick = onStart) { Text("Iniciar") }
            }
            if (task.state == "EN_EJECUCION") {
                OutlinedTextField(result, { result = it.uppercase() }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Resultado configurado") })
                OutlinedTextField(observations, { observations = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Observaciones") })
                OutlinedButton(onClick = { supportPicker.launch("*/*") }) {
                    Text(if (supports.isEmpty()) "Seleccionar soportes" else "Soportes (${supports.size})")
                }
                supports.forEach { Text("• ${it.filename} (${it.bytes.size} bytes)") }
                Button(enabled = !busy && result.isNotBlank(), onClick = {
                    onComplete(result, observations, supports)
                }) { Text("Completar") }
            }
            if (task.remoteId != null && task.state in setOf("PENDIENTE", "EN_EJECUCION")) {
                OutlinedTextField(newResponsible, { newResponsible = it.filter(Char::isDigit) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("ID nuevo responsable") })
                OutlinedTextField(reassignReason, { reassignReason = it },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Motivo de reasignación") })
                OutlinedButton(
                    enabled = !busy && newResponsible.toIntOrNull() != null && reassignReason.isNotBlank(),
                    onClick = { onReassign(requireNotNull(newResponsible.toIntOrNull()), reassignReason) }
                ) { Text("Reasignar") }
            }
        }
    }
}

internal fun syncLabel(value: String): String = when (value) {
    BpmSyncState.CONFIRMED -> "Confirmado por servidor"
    BpmSyncState.CONFLICT -> "En conflicto; conserve y resuelva"
    BpmSyncState.REJECTED -> "Rechazado por servidor"
    BpmSyncState.STALE -> "Información local desactualizada"
    else -> "Guardado localmente; pendiente de sincronización"
}
