package com.cactus.bitacora

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.data.CreateBitacoraResult
import com.cactus.bitacora.model.SupervisedParticipantOut
import com.cactus.bitacora.model.SupervisorSessionOut
import com.cactus.bitacora.ui.query.BitacoraQueryScreen
import kotlinx.coroutines.launch

private enum class SupervisorView { MENU, PARTICIPANTS, QUERY, ENTRADA, SALIDA, TODAY, CONTROL }

@Composable
internal fun SupervisorModeScreen(repository: BitacoraRepository, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf(repository.savedSupervisorCode().orEmpty()) }
    var session by remember { mutableStateOf<SupervisorSessionOut?>(null) }
    var view by remember { mutableStateOf(SupervisorView.MENU) }
    var participants by remember { mutableStateOf<List<SupervisedParticipantOut>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<Pair<SupervisedParticipantOut, String>?>(null) }

    fun identify() {
        loading = true
        scope.launch {
            try {
                session = repository.identifySupervisor(code)
                participants = repository.supervisedParticipants(requireNotNull(session).codigo)
                message = null
            } catch (error: Exception) {
                message = error.message ?: "No fue posible identificar al supervisor"
            } finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { if (code.isNotBlank()) identify() }

    val activeSession = session
    if (activeSession == null) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Identificación del supervisor", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(code, { code = it }, label = { Text("Código del participante") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = ::identify, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "Validando…" else "Ingresar como supervisor")
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        }
        return
    }

    BackHandler(enabled = view != SupervisorView.MENU) {
        view = SupervisorView.MENU
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("SUPERVISOR", style = MaterialTheme.typography.headlineSmall)
        Text("${activeSession.codigo} · ${activeSession.nombre_completo}")
        Text(activeSession.estado, color = MaterialTheme.colorScheme.primary)
        Text("Áreas: ${activeSession.areas.joinToString { it.area }}")
        if (view == SupervisorView.MENU) {
            Button({ view = SupervisorView.ENTRADA }, modifier = Modifier.fillMaxWidth()) { Text("Registrar entrada") }
            Button({ view = SupervisorView.SALIDA }, modifier = Modifier.fillMaxWidth()) { Text("Registrar salida") }
            Button({ view = SupervisorView.PARTICIPANTS }, modifier = Modifier.fillMaxWidth()) { Text("Consultar participantes bajo su mando") }
            Button({ view = SupervisorView.QUERY }, modifier = Modifier.fillMaxWidth()) { Text("Consultar marcaciones") }
            Button({ view = SupervisorView.TODAY }, modifier = Modifier.fillMaxWidth()) { Text("Consultar movimientos del día") }
            Button({ view = SupervisorView.CONTROL }, modifier = Modifier.fillMaxWidth()) { Text("CONTROL") }
        } else {
            OutlinedButton({ view = SupervisorView.MENU }, modifier = Modifier.fillMaxWidth()) { Text("Volver al menú") }
            if (view == SupervisorView.TODAY) {
                LaunchedEffect(activeSession.codigo, view) {
                    message = try {
                        repository.supervisorTodayMovements(activeSession).joinToString("\n") {
                            val time = java.time.Instant.ofEpochSecond(it.timestamp_min * 60L)
                                .atZone(java.time.ZoneId.of("America/Bogota"))
                                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                            "${it.tipo} · ${it.codigo_participante} · ${it.nombre_completo} · " +
                                "$time · ${it.area} · ${it.sync_status}"
                        }.ifBlank { "No hay movimientos del día" }
                    } catch (_: Exception) { "Movimientos remotos no disponibles temporalmente" }
                }
                Text(message.orEmpty())
            } else if (view == SupervisorView.CONTROL) {
                ControlSupervisorScreen(repository, activeSession) { view = SupervisorView.MENU }
            } else if (view == SupervisorView.QUERY) {
                BitacoraQueryScreen(
                    repository = repository,
                    allowDelete = false,
                    authorizedParticipants = participants
                )
            } else {
                OutlinedTextField(search, { search = it }, label = { Text("Código, nombre o apellido") }, modifier = Modifier.fillMaxWidth())
                val filtered = participants.filter {
                    search.isBlank() || listOf(it.codigo, it.nombre, it.apellido)
                        .any { value -> value?.contains(search, ignoreCase = true) == true }
                }
                Text("${filtered.size} participantes autorizados")
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filtered, key = { "${it.id_participante}-${it.id_area}" }) { participant ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${participant.codigo} · ${participant.nombre.orEmpty()} ${participant.apellido.orEmpty()}")
                                Text(participant.area)
                            }
                            if (view == SupervisorView.ENTRADA || view == SupervisorView.SALIDA) {
                                Button(onClick = { confirmation = participant to view.name }) { Text("Seleccionar") }
                            }
                        }
                    }
                }
            }
        }
        message?.takeIf { view != SupervisorView.TODAY }?.let { Text(it) }
        OutlinedButton(onClick = {
            repository.clearSupervisorSession(); session = null; code = ""; onExit()
        }, modifier = Modifier.fillMaxWidth()) { Text("Cerrar sesión de supervisor") }
    }

    confirmation?.let { (participant, type) ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text("Confirmar $type") },
            text = { Text("${participant.codigo} · ${participant.nombre.orEmpty()} ${participant.apellido.orEmpty()}\nÁrea: ${participant.area}") },
            dismissButton = { OutlinedButton({ confirmation = null }) { Text("Cancelar") } },
            confirmButton = { Button(onClick = {
                confirmation = null
                scope.launch {
                    message = when (val result = repository.createSupervisorMovement(activeSession, participant, type)) {
                        is CreateBitacoraResult.Sincronizada -> "$type registrada correctamente"
                        is CreateBitacoraResult.Pendiente -> result.message
                    }
                }
            }) { Text("Guardar") } }
        )
    }
}
