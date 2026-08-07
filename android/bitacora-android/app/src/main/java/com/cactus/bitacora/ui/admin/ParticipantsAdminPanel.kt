package com.cactus.bitacora.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.cactus.bitacora.model.ParticipantAdminIn
import com.cactus.bitacora.model.ParticipantAdminOut
import kotlinx.coroutines.launch

internal fun validParticipantForm(value: ParticipantAdminIn): Boolean =
    value.tipo_documento > 0 && value.documento.isNotBlank() &&
        value.identificacion_participante.isNotBlank() && value.nombre.isNotBlank() &&
        value.apellido.isNotBlank() && Regex("\\d{4}-\\d{2}-\\d{2}").matches(value.fecha_nacimiento) &&
        value.sexo.uppercase() in setOf("M", "F")

@Composable
fun ParticipantsAdminPanel(repository: BitacoraRepository, actor: String) {
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ParticipantAdminOut>>(emptyList()) }
    var total by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ParticipantAdminOut?>(null) }
    var creating by remember { mutableStateOf(false) }
    var retiring by remember { mutableStateOf<ParticipantAdminOut?>(null) }

    fun load() {
        loading = true
        scope.launch {
            try {
                val page = repository.adminParticipants(search)
                items = page.items; total = page.total; message = null
            } catch (error: Exception) { message = error.message ?: "No fue posible cargar participantes" }
            finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { load() }
    Text("Administrar participantes", style = MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Código, nombre o documento") }, modifier = Modifier.weight(1f), singleLine = true)
        Button(onClick = ::load, enabled = !loading) { Text("Buscar") }
    }
    Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("Nuevo participante") }
    Text(if (loading) "Cargando…" else "$total participantes")
    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    items.forEach { participant ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("${participant.identificacion_participante} · ${participant.nombre} ${participant.apellido}")
            Text("${participant.documento}${if (participant.activo) "" else " · RETIRADO"}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { editing = participant }) { Text("Ver") }
                OutlinedButton(onClick = { editing = participant }) { Text("Editar") }
                if (participant.activo) OutlinedButton(onClick = { retiring = participant }) { Text("Retirar") }
            }
        }
    }
    if (creating || editing != null) ParticipantEditorDialog(
        initial = editing,
        onDismiss = { creating = false; editing = null },
        onSave = { payload ->
            if (actor.isBlank()) { message = "Identifique al administrador"; return@ParticipantEditorDialog }
            loading = true
            scope.launch {
                try {
                    editing?.let { repository.updateAdminParticipant(actor, it.id_participante, payload) }
                        ?: repository.createAdminParticipant(actor, payload)
                    creating = false; editing = null; load()
                } catch (error: Exception) { message = error.message ?: "No fue posible guardar"; loading = false }
            }
        }
    )
    retiring?.let { participant ->
        AlertDialog(onDismissRequest = { retiring = null }, title = { Text("Retirar participante") }, text = { Text("Se conservará su historial y se registrará fecha de salida. ¿Continuar?") }, dismissButton = { OutlinedButton(onClick = { retiring = null }) { Text("Cancelar") } }, confirmButton = { Button(onClick = {
            if (actor.isBlank()) { message = "Identifique al administrador"; retiring = null; return@Button }
            scope.launch { try { repository.retireAdminParticipant(actor, participant.id_participante); retiring = null; load() } catch (error: Exception) { message = error.message; retiring = null } }
        }) { Text("Retirar") } })
    }
}

@Composable
private fun ParticipantEditorDialog(initial: ParticipantAdminOut?, onDismiss: () -> Unit, onSave: (ParticipantAdminIn) -> Unit) {
    var type by remember { mutableStateOf(initial?.tipo_documento?.toString() ?: "1") }
    var document by remember { mutableStateOf(initial?.documento.orEmpty()) }
    var code by remember { mutableStateOf(initial?.identificacion_participante.orEmpty()) }
    var name by remember { mutableStateOf(initial?.nombre.orEmpty()) }
    var surname by remember { mutableStateOf(initial?.apellido.orEmpty()) }
    var birth by remember { mutableStateOf(initial?.fecha_nacimiento.orEmpty()) }
    var sex by remember { mutableStateOf(initial?.sexo ?: "F") }
    var entry by remember { mutableStateOf(initial?.fecha_entrada.orEmpty()) }
    var exit by remember { mutableStateOf(initial?.fecha_salida.orEmpty()) }
    var observations by remember { mutableStateOf(initial?.observaciones.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    val payload = ParticipantAdminIn(type.toIntOrNull() ?: 0, document, code, name, surname, birth, sex, entry.ifBlank { null }, exit.ifBlank { null }, observations.ifBlank { null }, email.ifBlank { null })
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Agregar participante" else "Editar participante") }, text = {
        Column { listOf("Tipo documento" to type, "Documento" to document, "Código" to code, "Nombres" to name, "Apellidos" to surname, "Nacimiento (AAAA-MM-DD)" to birth, "Sexo (M/F)" to sex, "Entrada (AAAA-MM-DD)" to entry, "Salida (AAAA-MM-DD)" to exit, "Observaciones" to observations, "Correo" to email).forEachIndexed { index, field ->
            OutlinedTextField(value = field.second, onValueChange = { v -> when(index){0->type=v;1->document=v;2->code=v;3->name=v;4->surname=v;5->birth=v;6->sex=v;7->entry=v;8->exit=v;9->observations=v;else->email=v} }, label = { Text(field.first) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        } }
    }, dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }, confirmButton = { Button(enabled = validParticipantForm(payload), onClick = { onSave(payload) }) { Text("Guardar") } })
}
