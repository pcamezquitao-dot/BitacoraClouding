package com.cactus.bitacora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.feature.bpm.ParticipantBpmTasksScreen
import com.cactus.bitacora.model.PortalItemOut
import com.cactus.bitacora.model.PortalOut
import kotlinx.coroutines.launch
import retrofit2.HttpException

internal enum class PortalView { IDENTIFY, MENU, TASKS }

internal fun portalRows(options: List<PortalItemOut>): List<List<PortalItemOut>> = options.chunked(2)

private fun portalError(error: Throwable): String =
    if (error is HttpException) Regex("\\\"detail\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        .find(error.response()?.errorBody()?.string().orEmpty())?.groupValues?.get(1)
        ?: "No fue posible consultar el portal"
    else error.message ?: "No fue posible consultar el portal"

@Composable
internal fun PortalGeneralScreen(repository: BitacoraRepository, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf(PortalView.IDENTIFY) }
    var code by remember { mutableStateOf("") }
    var portal by remember { mutableStateOf<PortalOut?>(null) }
    var selected by remember { mutableStateOf<PortalItemOut?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun identify() {
        val requested = code.trim().uppercase()
        portal = null
        selected = null
        error = null
        if (requested.isBlank()) { error = "Ingrese el código del participante"; return }
        loading = true
        scope.launch {
            runCatching { repository.identifyPortal(requested) }
                .onSuccess { portal = it; code = it.participante.codigo; view = PortalView.MENU }
                .onFailure { error = portalError(it) }
            loading = false
        }
    }

    val active = portal
    if (view == PortalView.TASKS && active != null) {
        ParticipantBpmTasksScreen(active.participante.codigo) { view = PortalView.MENU }
        return
    }
    if (view == PortalView.IDENTIFY || active == null) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Portal General", style = MaterialTheme.typography.headlineSmall)
            Text("Identifique al participante para construir su menú.")
            OutlinedTextField(code, { code = it }, modifier = Modifier.fillMaxWidth(),
                label = { Text("Código del participante") }, singleLine = true)
            Button(enabled = !loading, onClick = ::identify, modifier = Modifier.fillMaxWidth()) {
                Text(if (loading) "Consultando…" else "Continuar")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onExit, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Portal General", style = MaterialTheme.typography.headlineSmall)
        Text("${active.participante.codigo} · ${active.participante.nombre_completo}")
        Text("Tipo: ${active.participante.tipo_descripcion}")
        active.mensaje_menu?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        portalRows(active.opciones).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { item ->
                    Card(
                        onClick = {
                            scope.launch {
                                runCatching { repository.openPortalOption(active.participante.codigo, item.id_accion) }
                                    .onSuccess { selected = it; error = null }
                                    .onFailure { error = portalError(it) }
                            }
                        },
                        modifier = Modifier.weight(1f).aspectRatio(1f)
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(item.nombre, fontSize = 14.sp, lineHeight = 18.sp)
                        }
                    }
                }
                if (row.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
        Button(onClick = { view = PortalView.TASKS }, modifier = Modifier.fillMaxWidth().aspectRatio(2f)) {
            Text("Tareas BPM")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onClick = { portal = null; code = ""; selected = null; error = null; view = PortalView.IDENTIFY }, modifier = Modifier.fillMaxWidth()) {
            Text("Cambiar participante")
        }
        OutlinedButton(onExit, modifier = Modifier.fillMaxWidth()) { Text("Salir del portal") }
    }

    selected?.let { item ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(item.nombre) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Proceso: ${item.nombre_proceso ?: "No configurado"}")
                Text("Flujo BPM: ${item.nombre_bpm ?: "No configurado"}")
                Text("Estado: ${item.estado_destino}")
                Text("Consultar esta opción no crea un caso. La ejecución solo comienza mediante una acción explícita autorizada del Motor BPM.")
            } },
            confirmButton = { Button(onClick = { selected = null }) { Text("Cerrar") } }
        )
    }
}
