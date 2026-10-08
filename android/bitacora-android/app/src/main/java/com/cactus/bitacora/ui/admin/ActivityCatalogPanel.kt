package com.cactus.bitacora.ui.admin

import androidx.compose.foundation.clickable
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
import com.cactus.bitacora.model.ActivityCatalogOut
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

internal const val ACTIVITY_CATALOG_NOTE =
    "Estas actividades son únicas y se pueden repetir en varios procesos"

internal fun activityNameError(value: String): String? = when {
    value.trim().isEmpty() -> "El nombre es obligatorio"
    value.trim().length > 100 -> "El nombre admite máximo 100 caracteres"
    else -> null
}

internal fun activityActorReady(value: String): Boolean = value.trim().isNotEmpty()

@Composable
internal fun ActivityCatalogPanel(repository: BitacoraRepository, actor: String) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var activities by remember { mutableStateOf<List<ActivityCatalogOut>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var form by remember { mutableStateOf<ActivityCatalogOut?>(null) }
    var creating by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ActivityCatalogOut?>(null) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    fun load() {
        loading = true
        error = null
        scope.launch {
            try {
                activities = repository.adminActivities(actor, query)
            } catch (failure: Exception) {
                error = failure.message ?: "No fue posible cargar el catálogo"
            } finally {
                loading = false
            }
        }
    }

    fun openEditor(activity: ActivityCatalogOut?) {
        form = activity
        creating = activity == null
        name = activity?.nombre.orEmpty()
        description = activity?.descripcion.orEmpty()
        formError = null
    }

    LaunchedEffect(actor) {
        if (!activityActorReady(actor)) {
            error = "Identifique al administrador para consultar el catálogo"
        } else {
            error = null
            delay(300)
            load()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Catálogo de Actividades", style = MaterialTheme.typography.titleMedium)
        Text(ACTIVITY_CATALOG_NOTE)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Buscar por nombre") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !loading, onClick = ::load) {
                Text(if (loading) "Cargando…" else "Buscar")
            }
            OutlinedButton(onClick = { query = ""; load() }) { Text("Limpiar") }
            Button(onClick = { openEditor(null) }) { Text("+") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Button(onClick = ::load) { Text("Reintentar") }
        }
        if (!loading && error == null && activities.isEmpty()) {
            Text("No hay actividades registradas")
        }
        activities.forEach { activity ->
            Column(
                modifier = Modifier.fillMaxWidth().clickable {
                    scope.launch {
                        detail = runCatching { repository.adminActivity(actor, activity.id_actividad) }
                            .getOrElse { activity }
                    }
                }
            ) {
                Text(activity.nombre, style = MaterialTheme.typography.titleSmall)
                activity.descripcion?.let { Text(it, maxLines = 2) }
                OutlinedButton(onClick = { openEditor(activity) }) { Text("Editar") }
            }
        }
    }

    if (creating || form != null) {
        AlertDialog(
            onDismissRequest = { if (!saving) { creating = false; form = null } },
            title = { Text(if (form == null) "Crear actividad" else "Modificar actividad") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it },
                        label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = description, onValueChange = { description = it },
                        label = { Text("Descripción (opcional)") }, modifier = Modifier.fillMaxWidth()
                    )
                    formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (saving) Text("Guardado pendiente de confirmación…")
                }
            },
            confirmButton = {
                Button(enabled = !saving, onClick = {
                    formError = activityNameError(name)
                    if (formError != null) return@Button
                    saving = true
                    scope.launch {
                        try {
                            val current = form
                            if (current == null) {
                                repository.createAdminActivity(actor, name, description)
                                message = "Actividad creada y confirmada"
                            } else {
                                repository.updateAdminActivity(
                                    actor, current.id_actividad, name, description
                                )
                                message = "Actividad actualizada y confirmada"
                            }
                            creating = false
                            form = null
                            load()
                        } catch (failure: Exception) {
                            formError = failure.message ?: "El guardado no fue confirmado"
                        } finally {
                            saving = false
                        }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                OutlinedButton(enabled = !saving, onClick = {
                    creating = false; form = null
                }) { Text("Cancelar") }
            }
        )
    }

    detail?.let { selected ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(selected.nombre) },
            text = { Text(selected.descripcion ?: "Sin descripción") },
            confirmButton = { Button(onClick = { detail = null }) { Text("Cerrar") } }
        )
    }
}
