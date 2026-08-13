package com.cactus.bitacora.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.R
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.ParticipantAdminIn
import com.cactus.bitacora.model.ParticipantAdminOut
import kotlinx.coroutines.launch

internal fun participantFormError(value: ParticipantAdminIn): String? {
    if (value.tipo_documento <= 0) return "Tipo documento es obligatorio"
    if (value.documento.isBlank()) return "Documento es obligatorio"
    if (value.identificacion_participante.isBlank()) return "Código es obligatorio"
    if (value.nombre.isBlank()) return "Nombres es obligatorio"
    fun invalidDate(raw: String?): Boolean = raw?.takeIf(String::isNotBlank)?.let {
        runCatching { java.time.LocalDate.parse(it) }.isFailure
    } ?: false
    if (invalidDate(value.fecha_nacimiento) || invalidDate(value.fecha_entrada) ||
        invalidDate(value.fecha_salida)) return "La fecha debe tener el formato AAAA-MM-DD"
    if (!value.sexo.isNullOrBlank() && value.sexo.uppercase() !in setOf("M", "F")) {
        return "Sexo debe ser M o F"
    }
    return null
}

internal fun validParticipantForm(value: ParticipantAdminIn): Boolean =
    participantFormError(value) == null

internal data class ParticipantFieldLayout(val singleLine: Boolean, val minLines: Int, val maxLines: Int)

internal fun participantFieldLayout(index: Int) = if (index == 9) {
    ParticipantFieldLayout(singleLine = false, minLines = 4, maxLines = 8)
} else {
    ParticipantFieldLayout(singleLine = true, minLines = 1, maxLines = 1)
}

@Composable
fun ParticipantsAdminPanel(repository: BitacoraRepository, actor: String, refreshToken: Int = 0) {
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
    LaunchedEffect(refreshToken) { load() }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${participant.identificacion_participante} · ${participant.nombre} ${participant.apellido}",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { editing = participant }, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(R.drawable.ic_participant_view), "Ver participante", tint = Color(0xFF1976D2), modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { editing = participant }, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(R.drawable.ic_participant_edit), "Editar participante", tint = Color(0xFFF9A825), modifier = Modifier.size(20.dp))
                }
                if (participant.activo) IconButton(onClick = { retiring = participant }, modifier = Modifier.size(36.dp)) {
                    Icon(painterResource(R.drawable.ic_participant_retire), "Retirar participante", tint = Color(0xFFD32F2F), modifier = Modifier.size(20.dp))
                }
            }
            Text("${participant.documento}${if (participant.activo) "" else " · RETIRADO"}")
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
                    val current = editing
                    if (current != null) {
                        val result = repository.updateAdminParticipant(actor, current.id_participante, payload)
                        items = items.map { if (it.id_participante == current.id_participante) result.participant else it }
                        message = if (result.synced) "Participante actualizado correctamente" else
                            "Cambio guardado localmente. Pendiente de sincronización${result.syncError?.let { ": $it" }.orEmpty()}"
                        creating = false; editing = null; loading = false
                    } else {
                        repository.createAdminParticipant(actor, payload)
                        creating = false; editing = null; load()
                    }
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
    var sex by remember { mutableStateOf(initial?.sexo.orEmpty()) }
    var entry by remember { mutableStateOf(initial?.fecha_entrada.orEmpty()) }
    var exit by remember { mutableStateOf(initial?.fecha_salida.orEmpty()) }
    var observations by remember { mutableStateOf(initial?.observaciones.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email.orEmpty()) }
    val payload = ParticipantAdminIn(type.toIntOrNull() ?: 0, document, code, name,
        surname.ifBlank { null }, birth.ifBlank { null }, sex.ifBlank { null },
        entry.ifBlank { null }, exit.ifBlank { null }, observations.ifBlank { null },
        email.ifBlank { null })
    val validationError = participantFormError(payload)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial == null) "Agregar participante" else "Editar participante") }, text = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(bottom = 16.dp)
        ) { listOf("Tipo documento" to type, "Documento" to document, "Código" to code, "Nombres" to name, "Apellidos" to surname, "Nacimiento (AAAA-MM-DD)" to birth, "Sexo (M/F)" to sex, "Entrada (AAAA-MM-DD)" to entry, "Salida (AAAA-MM-DD)" to exit, "Observaciones" to observations, "Correo" to email).forEachIndexed { index, field ->
            val invalidDate = index in setOf(5, 7, 8) && field.second.isNotBlank() &&
                runCatching { java.time.LocalDate.parse(field.second) }.isFailure
            val layout = participantFieldLayout(index)
            OutlinedTextField(
                value = field.second,
                onValueChange = { v -> when(index){0->type=v;1->document=v;2->code=v;3->name=v;4->surname=v;5->birth=v;6->sex=v;7->entry=v;8->exit=v;9->observations=v;else->email=v} },
                label = { Text(field.first) },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (index == 9) Modifier.heightIn(min = 110.dp) else Modifier),
                singleLine = layout.singleLine,
                minLines = layout.minLines,
                maxLines = layout.maxLines,
                isError = invalidDate,
                supportingText = if (invalidDate) {{ Text("La fecha debe tener el formato AAAA-MM-DD") }} else null
            )
        }; validationError?.let { Text(it, color = MaterialTheme.colorScheme.error) } }
    }, dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancelar") } }, confirmButton = { Button(enabled = validationError == null, onClick = { onSave(payload) }) { Text("Guardar") } })
}
