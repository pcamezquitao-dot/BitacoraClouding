package com.cactus.bitacora.feature.supervisorevents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.data.local.SupervisorEventLocalEntity
import com.cactus.bitacora.model.SupervisedParticipantOut
import com.cactus.bitacora.model.SupervisorSessionOut
import com.cactus.bitacora.sync.OfflineSyncScheduler
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
internal fun SupervisorEventForm(
    repository: SupervisorEventRepository,
    session: SupervisorSessionOut,
    authorizedParticipants: List<SupervisedParticipantOut>,
    enqueueSync: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<SupervisedParticipantOut?>(null) }
    var type by remember { mutableStateOf(SupervisorEventType.PERMISSION) }
    val today = remember { LocalDate.now(COLOMBIA_ZONE).toString() }
    var startDate by remember { mutableStateOf(today) }
    var endDate by remember { mutableStateOf(today) }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var observations by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var history by remember { mutableStateOf<List<SupervisorEventLocalEntity>>(emptyList()) }

    fun loadHistory(participant: SupervisedParticipantOut?) {
        if (participant == null) {
            history = emptyList()
            return
        }
        scope.launch {
            history = repository.eventsForParticipant(session.codigo, participant.id_participante)
        }
    }

    LaunchedEffect(selected?.id_participante) { loadHistory(selected) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Novedades", style = MaterialTheme.typography.titleLarge)
        Text("Seleccione un participante activo bajo su alcance")
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(
                authorizedParticipants,
                key = { "${it.id_participante}-${it.id_area}" }
            ) { participant ->
                val label = "${participant.codigo} · ${participant.nombre.orEmpty()} " +
                    "${participant.apellido.orEmpty()} · ${participant.area}"
                if (selected == participant) {
                    Button(onClick = { selected = participant }, modifier = Modifier.fillMaxWidth()) {
                        Text(label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { selected = participant; message = null },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(label) }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SupervisorEventType.entries.forEach { option ->
                if (type == option) {
                    Button(onClick = { type = option }, modifier = Modifier.weight(1f)) {
                        Text(option.label)
                    }
                } else {
                    OutlinedButton(
                        onClick = { type = option; message = null },
                        modifier = Modifier.weight(1f)
                    ) { Text(option.label) }
                }
            }
        }
        OutlinedTextField(
            value = startDate,
            onValueChange = { startDate = it },
            label = { Text(if (type == SupervisorEventType.DISABILITY) "Fecha inicial (AAAA-MM-DD)" else "Fecha (AAAA-MM-DD)") },
            modifier = Modifier.fillMaxWidth()
        )
        if (type == SupervisorEventType.DISABILITY) {
            OutlinedTextField(
                value = endDate,
                onValueChange = { endDate = it },
                label = { Text("Fecha final (AAAA-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("Hora inicial HH:mm") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text("Hora final HH:mm") },
                    modifier = Modifier.weight(1f)
                )
            }
            runCatching {
                validateSupervisorEvent(
                    SupervisorEventDraft(
                        supervisorCode = session.codigo,
                        participantId = selected?.id_participante ?: 0,
                        areaId = selected?.id_area ?: 0,
                        type = type,
                        startDate = startDate,
                        startTime = startTime,
                        endTime = endTime
                    )
                ).durationMinutes
            }.getOrNull()?.let { Text("Duración calculada: $it minutos") }
        }
        OutlinedTextField(
            value = observations,
            onValueChange = { observations = it.take(400) },
            label = { Text("Observaciones") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val participant = selected
                val authorized = authorizedParticipants
                    .map { it.id_participante to it.id_area }
                    .toSet()
                if (participant == null || !isParticipantAuthorized(
                        participant.id_participante,
                        participant.id_area,
                        authorized
                    )) {
                    message = "Seleccione un participante autorizado y activo"
                } else {
                    scope.launch {
                        message = try {
                            val draft = SupervisorEventDraft(
                                supervisorCode = session.codigo,
                                participantId = participant.id_participante,
                                areaId = participant.id_area,
                                type = type,
                                startDate = startDate,
                                endDate = endDate,
                                startTime = startTime,
                                endTime = endTime,
                                observations = observations
                            )
                            repository.saveOffline(draft)
                            loadHistory(participant)
                            enqueueSync()
                            "Novedad guardada offline y pendiente de sincronización"
                        } catch (error: SupervisorEventValidationException) {
                            error.message
                        } catch (error: Exception) {
                            error.message ?: "No fue posible guardar la novedad"
                        }
                    }
                }
            }
        ) { Text("Guardar novedad") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        Text("Novedades registradas", style = MaterialTheme.typography.titleMedium)
        history.forEach { event ->
            val eventType = eventTypeFromBackendId(event.tipoNovedad)?.label ?: "Tipo ${event.tipoNovedad}"
            val interval = if (event.horaInicio != null && event.horaFinal != null) {
                " · ${event.horaInicio}–${event.horaFinal}"
            } else ""
            val dates = if (event.fechaFinal != event.fechaInicio) {
                "${event.fechaInicio} a ${event.fechaFinal}"
            } else event.fechaInicio
            Text("$eventType · $dates$interval · ${event.syncStatus}")
            event.observaciones?.let { Text(it) }
            event.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
internal fun rememberSupervisorEventRepository(): SupervisorEventRepository {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    return remember { SupervisorEventRepository(context) }
}

@Composable
internal fun rememberSupervisorEventSyncAction(): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    return remember { { OfflineSyncScheduler.enqueueNow(context) } }
}
