package com.cactus.bitacora.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.R
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.NavigationAdminIn
import com.cactus.bitacora.model.NavigationAdminOut
import com.cactus.bitacora.model.ProcessAdminOut
import java.text.Normalizer
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import retrofit2.HttpException

private fun navigationKey(value: String) = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

internal data class NavigationTreeRow(val action: NavigationAdminOut, val depth: Int, val hasChildren: Boolean, val expanded: Boolean, val inconsistent: Boolean)

internal fun navigationDescendantIds(actions: List<NavigationAdminOut>, id: Int): Set<Int> {
    val children = actions.groupBy(NavigationAdminOut::id_accion_padre)
    val found = mutableSetOf<Int>()
    val pending = ArrayDeque(children[id].orEmpty().map(NavigationAdminOut::id_accion))
    while (pending.isNotEmpty()) {
        val child = pending.removeFirst()
        if (found.add(child)) children[child].orEmpty().forEach { pending.add(it.id_accion) }
    }
    found.remove(id)
    return found
}

internal fun buildNavigationTreeRows(actions: List<NavigationAdminOut>, expanded: Set<Int>): List<NavigationTreeRow> {
    val ids = actions.mapTo(mutableSetOf(), NavigationAdminOut::id_accion)
    val children = actions.groupBy(NavigationAdminOut::id_accion_padre).mapValues { (_, rows) -> rows.sortedWith(compareBy<NavigationAdminOut> { navigationKey(it.nombre) }.thenBy { it.id_accion }) }
    val result = mutableListOf<NavigationTreeRow>()
    val visited = mutableSetOf<Int>()
    val reachableFromRoots = mutableSetOf<Int>()
    fun markReachable(item: NavigationAdminOut) {
        if (!reachableFromRoots.add(item.id_accion)) return
        children[item.id_accion].orEmpty().forEach(::markReachable)
    }
    children[null].orEmpty().forEach(::markReachable)
    fun visit(item: NavigationAdminOut, depth: Int, path: Set<Int>) {
        if (!visited.add(item.id_accion)) return
        val descendants = children[item.id_accion].orEmpty()
        val cycle = item.id_accion in path
        val open = descendants.isNotEmpty() && item.id_accion in expanded
        result += NavigationTreeRow(item, depth, descendants.isNotEmpty(), open, cycle)
        if (open && !cycle) descendants.forEach { visit(it, depth + 1, path + item.id_accion) }
    }
    children[null].orEmpty().forEach { visit(it, 0, emptySet()) }
    actions.filter { it.id_accion !in reachableFromRoots }.sortedBy { it.id_accion }.forEach {
        result += NavigationTreeRow(it, 0, children[it.id_accion].orEmpty().isNotEmpty(), false, it.id_accion_padre !in ids || it.id_accion in navigationDescendantIds(actions, it.id_accion))
        visited += it.id_accion
    }
    return result
}

private fun navigationFailure(error: Exception, fallback: String): String =
    if (error is HttpException) Regex("\\\"detail\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").find(error.response()?.errorBody()?.string().orEmpty())?.groupValues?.get(1) ?: fallback
    else error.message ?: fallback

@Composable
internal fun NavigationAdminPanel(repository: BitacoraRepository, actor: String) {
    val scope = rememberCoroutineScope()
    var actions by remember { mutableStateOf<List<NavigationAdminOut>>(emptyList()) }
    var processes by remember { mutableStateOf<List<ProcessAdminOut>>(emptyList()) }
    var expanded by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<NavigationAdminOut?>(null) }
    var creating by remember { mutableStateOf(false) }
    var presetParent by remember { mutableStateOf<Int?>(null) }
    var viewing by remember { mutableStateOf<NavigationAdminOut?>(null) }
    var deleting by remember { mutableStateOf<NavigationAdminOut?>(null) }

    fun load() {
        if (actor.isBlank()) { error = "Identifique al administrador para consultar la navegación"; return }
        loading = true; error = null
        scope.launch {
            try {
                actions = repository.adminNavigation(actor)
                processes = runCatching { repository.adminProcesses(actor) }.getOrElse { processes }
            } catch (failure: Exception) { error = navigationFailure(failure, "No fue posible cargar la navegación") }
            finally { loading = false }
        }
    }
    LaunchedEffect(actor) {
        if (actor.isNotBlank()) { delay(300); load() }
        else error = "Identifique al administrador para consultar la navegación"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Navegación", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !loading, onClick = ::load) { Text(if (loading) "Cargando…" else "Actualizar") }
            Button(onClick = { creating = true; editing = null; presetParent = null }) { Text("Crear raíz") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error); Button(onClick = ::load) { Text("Reintentar") } }
        if (actor.isNotBlank() && !loading && error == null && actions.isEmpty()) Text("No hay acciones de navegación registradas")
        val rows = buildNavigationTreeRows(actions, expanded)
        if (rows.any(NavigationTreeRow::inconsistent)) Text("La jerarquía contiene registros inconsistentes", color = MaterialTheme.colorScheme.error)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(start = (row.depth * 10).dp).background(if (selected == row.action.id_accion) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) {
                IconButton(onClick = { creating = true; editing = null; presetParent = row.action.id_accion }, modifier = Modifier.size(36.dp)) { Text("+") }
                TextButton(onClick = { selected = row.action.id_accion; if (row.hasChildren) expanded = if (row.expanded) expanded - row.action.id_accion else expanded + row.action.id_accion }, modifier = Modifier.weight(1f)) {
                    Text(when { row.hasChildren && row.expanded -> "▼ ${row.action.nombre}"; row.hasChildren -> "▶ ${row.action.nombre}"; else -> "└─ ${row.action.nombre}" }, modifier = Modifier.fillMaxWidth())
                }
                IconButton(onClick = { viewing = row.action }, modifier = Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_participant_view), "Consultar", tint = Color(0xFF1976D2)) }
                IconButton(onClick = { editing = row.action; creating = false; presetParent = row.action.id_accion_padre }, modifier = Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_participant_edit), "Editar", tint = Color(0xFFF9A825)) }
                IconButton(onClick = { deleting = row.action }, modifier = Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_participant_retire), "Eliminar", tint = Color(0xFFD32F2F)) }
            }
        }
    }

    if (creating || editing != null) NavigationEditor(actions, processes, editing, presetParent, onDismiss = { creating = false; editing = null }, onSave = { payload ->
        val saved = if (editing == null) repository.createAdminNavigation(actor, payload) else repository.updateAdminNavigation(actor, editing!!.id_accion, payload)
        selected = saved.id_accion; saved.id_accion_padre?.let { expanded = expanded + it }
        message = if (editing == null) "Acción creada correctamente" else "Acción actualizada correctamente"
        creating = false; editing = null
        try { actions = repository.adminNavigation(actor) } catch (_: Exception) { message = "Cambio guardado; pendiente actualizar la vista" }
    })

    viewing?.let { item -> AlertDialog(onDismissRequest = { viewing = null }, title = { Text(item.nombre) }, text = { Text("ID: ${item.id_accion}\nTipo: ${item.tipo_accion}\nDescripción: ${item.descripcion ?: "—"}\nPadre: ${item.nombre_padre ?: "Sin padre"}\nProceso: ${item.nombre_proceso ?: "Sin proceso asociado"}") }, confirmButton = { Button(onClick = { viewing = null }) { Text("Cerrar") } }) }
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Eliminar acción") }, text = { Text("¿Eliminar ${item.id_accion} · ${item.nombre}?") }, dismissButton = { OutlinedButton(onClick = { deleting = null }) { Text("Cancelar") } }, confirmButton = { Button(onClick = { scope.launch { try { message = repository.deleteAdminNavigation(actor, item.id_accion).mensaje; deleting = null; load() } catch (e: Exception) { error = navigationFailure(e, "No fue posible eliminar la acción") } } }) { Text("Eliminar") } }) }
}

@Composable
private fun NavigationEditor(actions: List<NavigationAdminOut>, processes: List<ProcessAdminOut>, current: NavigationAdminOut?, initialParent: Int?, onDismiss: () -> Unit, onSave: suspend (NavigationAdminIn) -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember(current) { mutableStateOf(current?.nombre.orEmpty()) }
    var description by remember(current) { mutableStateOf(current?.descripcion.orEmpty()) }
    var parent by remember(current) { mutableStateOf(initialParent) }
    var process by remember(current) { mutableStateOf(current?.id_proceso) }
    var parentMenu by remember { mutableStateOf(false) }; var processMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }; var saving by remember { mutableStateOf(false) }
    val forbidden = current?.let { navigationDescendantIds(actions, it.id_accion) + it.id_accion }.orEmpty()
    AlertDialog(onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (current == null) "Crear acción" else "Editar acción") }, text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(current?.id_accion?.toString().orEmpty(), {}, label = { Text("ID automático") }, enabled = false)
        OutlinedTextField("1", {}, label = { Text("Tipo") }, readOnly = true)
        OutlinedTextField(name, { name = it.take(151); error = null }, label = { Text("Nombre") })
        OutlinedTextField(description, { description = it.take(201); error = null }, label = { Text("Descripción (opcional)") })
        OutlinedButton(onClick = { parentMenu = true }) { Text(parent?.let { id -> actions.firstOrNull { it.id_accion == id }?.let { "Padre: ${it.nombre}" } } ?: "Sin padre") }
        DropdownMenu(parentMenu, { parentMenu = false }) { DropdownMenuItem({ Text("Sin padre") }, { parent = null; parentMenu = false }); actions.filterNot { it.id_accion in forbidden }.forEach { item -> DropdownMenuItem({ Text("${item.nombre} (${item.id_accion})") }, { parent = item.id_accion; parentMenu = false }) } }
        OutlinedButton(onClick = { processMenu = true }) { Text(process?.let { id -> processes.firstOrNull { it.id_proceso == id }?.let { "Proceso: ${it.nombre}" } } ?: "Sin proceso asociado") }
        DropdownMenu(processMenu, { processMenu = false }) { DropdownMenuItem({ Text("Sin proceso asociado") }, { process = null; processMenu = false }); processes.forEach { item -> DropdownMenuItem({ Text("${item.nombre} (${item.id_proceso})") }, { process = item.id_proceso; processMenu = false }) } }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, dismissButton = { OutlinedButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } }, confirmButton = { Button(enabled = !saving, onClick = {
        val normalized = name.trim(); error = when { normalized.isEmpty() -> "Ingrese el nombre de la acción"; normalized.length > 150 -> "El nombre admite máximo 150 caracteres"; description.trim().length > 200 -> "La descripción admite máximo 200 caracteres"; else -> null }
        if (error != null) return@Button
        saving = true
        scope.launch { try { onSave(NavigationAdminIn(nombre = normalized, descripcion = description.trim().ifEmpty { null }, id_accion_padre = parent, id_proceso = process, request_id = UUID.randomUUID().toString(), original_fingerprint = current?.fingerprint)) } catch (e: Exception) { error = navigationFailure(e, "El guardado no fue confirmado") } finally { saving = false } }
    }) { Text("Guardar") } })
}
