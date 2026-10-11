package com.cactus.bitacora.feature.bpm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.launch

private val PARTICIPANT_TASK_STATES = listOf("PENDIENTE", "EN_EJECUCION", "COMPLETADA", "CANCELADA")

@Composable
internal fun ParticipantBpmTasksScreen(actorCode: String, onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repository = remember { BpmRepository(context) }
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf<List<BpmTaskLocalEntity>>(emptyList()) }
    var filter by remember { mutableStateOf("PENDIENTE") }
    var message by remember { mutableStateOf("Consultando tareas locales") }
    var busy by remember { mutableStateOf(false) }

    suspend fun load() { tasks = repository.tasks(actorCode) }
    fun run(action: suspend () -> String) {
        busy = true
        scope.launch {
            message = try { action() } catch (error: Exception) {
                error.message ?: "No fue posible completar la accion"
            }
            load()
            busy = false
        }
    }
    LaunchedEffect(actorCode) {
        load()
        message = if (tasks.none { it.state in setOf("PENDIENTE", "EN_EJECUCION") }) {
            "No tiene tareas pendientes"
        } else {
            "Tareas disponibles sin conexion; actualice para confirmar con el servidor"
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Tareas BPM", style = MaterialTheme.typography.headlineSmall)
        Text("Participante: ${actorCode.trim().uppercase()}")
        Text("Solo puede consultar y ejecutar tareas asignadas a su identidad.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = { run {
                repository.refresh(actorCode)
                if (repository.tasks(actorCode).none { it.state in setOf("PENDIENTE", "EN_EJECUCION") })
                    "No tiene tareas pendientes" else "Bandeja actualizada"
            } }) { Text("Actualizar") }
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
        PARTICIPANT_TASK_STATES.forEach { state ->
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = { filter = state }) {
                Text((if (filter == state) "✓ " else "") + state)
            }
        }
        val visible = tasks.filter { it.state == filter }
        Text("${visible.size} tarea(s) $filter")
        if (filter == "PENDIENTE" && visible.isEmpty()) Text("No tiene tareas pendientes")
        visible.forEach { task ->
            BpmTaskCard(task, busy, onStart = { run {
                repository.queueStart(actorCode, task)
                "Inicio guardado localmente; pendiente de confirmacion"
            } }, onComplete = { result, observations, supports -> run {
                repository.queueComplete(actorCode, task, result, observations, supports)
                "Finalizacion guardada localmente; pendiente de confirmacion"
            } }, onReassign = { _, _ ->
                message = "La reasignacion requiere autorizacion administrativa"
            })
        }
    }
}
