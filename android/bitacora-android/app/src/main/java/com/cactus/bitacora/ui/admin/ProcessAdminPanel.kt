package com.cactus.bitacora.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cactus.bitacora.R
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.ActivityCatalogOut
import com.cactus.bitacora.model.ProcessAdminIn
import com.cactus.bitacora.model.ProcessAdminOut
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import retrofit2.HttpException

internal const val PROCESS_ADMIN_NOTE =
    "Organice los procesos por niveles y asocie, cuando corresponda, una actividad del Catálogo de Actividades"

private fun processKey(value: String): String = Normalizer.normalize(
    value.trim(), Normalizer.Form.NFD
).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

internal fun processErrorDetail(rawBody: String?, fallback: String): String =
    Regex("\\\"detail\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"")
        .find(rawBody.orEmpty())?.groupValues?.get(1)
        ?.replace("\\\\\"", "\"")?.replace("\\\\n", "\n")
        ?.trim()?.ifEmpty { fallback } ?: fallback

private fun processFailureMessage(failure: Exception, fallback: String): String =
    if (failure is HttpException) processErrorDetail(failure.response()?.errorBody()?.string(), fallback)
    else failure.message ?: fallback

internal fun processFormError(
    processes: List<ProcessAdminOut>, currentId: Int?, code: String, name: String,
    reference: String, time: String, cost: String, type: String, parentId: Int?
): String? {
    val id = code.toIntOrNull() ?: return "Ingrese un código entero válido"
    if (id < 0) return "Ingrese un código entero válido"
    val normalizedName = name.trim()
    if (normalizedName.isEmpty()) return "Ingrese el nombre del proceso"
    if (normalizedName.length > 50) return "El nombre admite máximo 50 caracteres"
    if (reference.trim().length > 100) return "La Referencia admite máximo 100 caracteres"
    if (time.isNotBlank() && (time.toIntOrNull() == null || time.toInt() < 0)) return "El tiempo debe ser un entero no negativo"
    if (cost.isNotBlank() && (cost.toDoubleOrNull() == null || cost.toDouble() < 0 || cost.toDouble() > 99_999_999.99)) return "El costo debe ser un número no negativo de máximo 99.999.999,99"
    if (type.toIntOrNull() != 1) return "El tipo de proceso permitido es 1"
    if (currentId == null && processes.any { it.id_proceso == id }) return "Ya existe un proceso con este código"
    val others = processes.filterNot { it.id_proceso == currentId }
    if (others.any { processKey(it.nombre) == processKey(normalizedName) }) return "Ya existe un proceso con este nombre"
    val normalizedReference = reference.trim()
    if (normalizedReference.isNotEmpty() && others.any { processKey(it.nombre_corto.orEmpty()) == processKey(normalizedReference) }) return "Ya existe un proceso con esta referencia"
    if (currentId != null && parentId == currentId) return "El proceso no puede depender de sí mismo ni de sus descendientes"
    return null
}

internal fun descendantProcessIds(processes: List<ProcessAdminOut>, id: Int): Set<Int> {
    val children = processes.groupBy(ProcessAdminOut::id_proceso_padre)
    val result = mutableSetOf<Int>()
    val pending = ArrayDeque(children[id].orEmpty().map(ProcessAdminOut::id_proceso))
    while (pending.isNotEmpty()) {
        val child = pending.removeFirst()
        if (!result.add(child)) continue
        children[child].orEmpty().forEach { pending.addLast(it.id_proceso) }
    }
    result.remove(id)
    return result
}

internal fun hasProcessAncestryCycle(processes: List<ProcessAdminOut>, id: Int): Boolean {
    val parents = processes.associate { it.id_proceso to it.id_proceso_padre }
    val visited = mutableSetOf<Int>()
    var current: Int? = id
    while (current != null) {
        if (!visited.add(current)) return true
        current = parents[current]
    }
    return false
}

internal data class ProcessTreeRow(
    val process: ProcessAdminOut, val depth: Int, val hasChildren: Boolean, val expanded: Boolean,
    val inconsistent: Boolean = false
)

internal fun buildProcessTreeRows(
    processes: List<ProcessAdminOut>, expanded: Set<Int>, includeCollapsedChildren: Boolean = false
): List<ProcessTreeRow> {
    val ids = processes.mapTo(mutableSetOf(), ProcessAdminOut::id_proceso)
    val children = processes.groupBy(ProcessAdminOut::id_proceso_padre).mapValues { (_, values) ->
        values.sortedWith(compareBy<ProcessAdminOut> { processKey(it.nombre) }.thenBy { it.id_proceso })
    }
    val rows = mutableListOf<ProcessTreeRow>()
    val visited = mutableSetOf<Int>()
    fun visit(item: ProcessAdminOut, depth: Int) {
        if (!visited.add(item.id_proceso)) return
        val childItems = children[item.id_proceso].orEmpty()
        val isExpanded = childItems.isNotEmpty() && item.id_proceso in expanded
        rows += ProcessTreeRow(item, depth, childItems.isNotEmpty(), isExpanded)
        if (includeCollapsedChildren || isExpanded) childItems.forEach { visit(it, depth + 1) }
    }
    children[null].orEmpty().forEach { visit(it, 0) }
    processes.filter { it.id_proceso !in visited }.sortedBy { it.id_proceso }.forEach {
        rows += ProcessTreeRow(it, 0, children[it.id_proceso].orEmpty().isNotEmpty(), false, it.id_proceso_padre != null && it.id_proceso_padre !in ids || hasProcessAncestryCycle(processes, it.id_proceso))
        visited += it.id_proceso
    }
    return rows
}

@Composable
private fun ProcessSummary(rows: List<ProcessTreeRow>) {
    val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
    Text("Resumen de procesos (solo lectura)", style = MaterialTheme.typography.titleMedium)
    Column(Modifier.fillMaxWidth().background(Color(0xFF202124)).padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("Árbol de procesos", color = Color.White, style = style, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
            Text("│", color = Color.White, style = style)
            Text("Referencia", color = Color.White, style = style, modifier = Modifier.width(100.dp).padding(horizontal = 6.dp))
        }
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                val marker = if (row.depth == 0) "●" else "└─"
                Text("   ".repeat(row.depth) + "$marker ${row.process.nombre}", color = Color.White, style = style, modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 2.dp))
                Text("│", color = Color.White, style = style)
                Text(row.process.nombre_corto ?: "—", color = Color.White, style = style, modifier = Modifier.width(100.dp).padding(horizontal = 6.dp))
            }
        }
    }
}

@Composable
internal fun ProcessAdminPanel(repository: BitacoraRepository, actor: String) {
    val scope = rememberCoroutineScope()
    var processes by remember { mutableStateOf<List<ProcessAdminOut>>(emptyList()) }
    var activities by remember { mutableStateOf<List<ActivityCatalogOut>>(emptyList()) }
    var expanded by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ProcessAdminOut?>(null) }
    var creating by remember { mutableStateOf(false) }
    var presetParent by remember { mutableStateOf<Int?>(null) }
    var viewing by remember { mutableStateOf<ProcessAdminOut?>(null) }
    var deleting by remember { mutableStateOf<ProcessAdminOut?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    fun load() {
        if (actor.trim().isEmpty()) { error = "Identifique al administrador para consultar los procesos"; return }
        loading = true; error = null
        scope.launch {
            try {
                val loaded = repository.adminProcesses(actor)
                processes = loaded
                activities = runCatching { repository.adminActivities(actor) }.getOrDefault(activities)
            } catch (failure: Exception) {
                error = processFailureMessage(failure, "No fue posible cargar los procesos")
            } finally { loading = false }
        }
    }

    LaunchedEffect(actor) {
        if (actor.isBlank()) error = "Identifique al administrador para consultar los procesos"
        else { delay(300); load() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Administración Proceso", style = MaterialTheme.typography.titleMedium)
        Text(PROCESS_ADMIN_NOTE)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !loading, onClick = ::load) { Text(if (loading) "Cargando…" else "Actualizar") }
            Button(onClick = { creating = true; editing = null; presetParent = null }) { Text("Crear proceso raíz (+)") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error); Button(onClick = ::load) { Text("Reintentar") } }
        if (!loading && error == null && processes.isEmpty()) Text("No hay procesos registrados")
        val summaryRows = buildProcessTreeRows(processes, processes.mapTo(mutableSetOf(), ProcessAdminOut::id_proceso), true)
        if (processes.isNotEmpty()) ProcessSummary(summaryRows)
        val rows = buildProcessTreeRows(processes, expanded)
        if (rows.any(ProcessTreeRow::inconsistent)) Text("La jerarquía contiene registros inconsistentes; revise antes de modificar", color = MaterialTheme.colorScheme.error)
        Text("Árbol interactivo", style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth().padding(start = (row.depth * 10).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { creating = true; editing = null; presetParent = row.process.id_proceso }, modifier = Modifier.size(36.dp)) { Text("+") }
                Text(
                    when { row.hasChildren && row.expanded -> "▼ ${row.process.nombre}"; row.hasChildren -> "▶ ${row.process.nombre}"; else -> "└─ ${row.process.nombre}" },
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f).clickable(enabled = row.hasChildren) { expanded = if (row.expanded) expanded - row.process.id_proceso else expanded + row.process.id_proceso }
                )
                IconButton(onClick = { viewing = row.process }, modifier = Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_participant_view), "Consultar", tint = Color(0xFF1976D2), modifier = Modifier.size(18.dp)) }
                IconButton(onClick = { editing = row.process; creating = false; presetParent = row.process.id_proceso_padre }, modifier = Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_participant_edit), "Editar", tint = Color(0xFFF9A825), modifier = Modifier.size(18.dp)) }
                IconButton(onClick = { deleting = row.process; deleteError = null }, modifier = Modifier.size(36.dp)) { Icon(painterResource(R.drawable.ic_participant_retire), "Eliminar", tint = Color(0xFFD32F2F), modifier = Modifier.size(18.dp)) }
            }
        }
    }

    if (creating || editing != null) ProcessEditorDialog(
        processes, activities, editing, presetParent,
        onDismiss = { creating = false; editing = null },
        onSave = { payload ->
            if (editing == null) repository.createAdminProcess(actor, payload) else repository.updateAdminProcess(actor, payload)
            message = if (editing == null) "Proceso creado correctamente" else "Proceso actualizado correctamente"
            presetParent?.let { expanded = expanded + it }
            creating = false; editing = null; load()
        }
    )

    viewing?.let { item -> AlertDialog(
        onDismissRequest = { viewing = null }, title = { Text(item.nombre) },
        text = { Text("Código: ${item.id_proceso}\nDescripción: ${item.descripcion ?: "—"}\nPadre: ${item.nombre_padre ?: "Sin padre / Proceso raíz"}\nTiempo estimado: ${item.tiempo_estimado ?: "—"}\nCosto estimado: ${item.costo_estimado ?: "—"}\nTipo de proceso: ${item.tipo_proceso}\nPrecondición: ${item.precondicion ?: "—"}\nActividad: ${item.nombre_actividad ?: "Sin actividad"}\nReferencia: ${item.nombre_corto ?: "—"}") },
        confirmButton = { Button(onClick = { viewing = null }) { Text("Cerrar") } }
    ) }

    deleting?.let { item -> AlertDialog(
        onDismissRequest = { deleting = null }, title = { Text("Eliminar proceso") },
        text = { Column { Text("¿Eliminar ${item.id_proceso} · ${item.nombre}? No se eliminarán descendientes ni registros relacionados."); deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
        dismissButton = { OutlinedButton(onClick = { deleting = null; deleteError = null }) { Text("Cancelar") } },
        confirmButton = { Button(onClick = { scope.launch { try { message = repository.deleteAdminProcess(actor, item.id_proceso).mensaje; deleting = null; deleteError = null; load() } catch (failure: Exception) { deleteError = processFailureMessage(failure, "No fue posible eliminar el proceso") } } }) { Text("Eliminar") } }
    ) }
}

@Composable
private fun ProcessEditorDialog(
    processes: List<ProcessAdminOut>, activities: List<ActivityCatalogOut>, current: ProcessAdminOut?, initialParent: Int?,
    onDismiss: () -> Unit, onSave: suspend (ProcessAdminIn) -> Unit
) {
    val scope = rememberCoroutineScope()
    var code by remember(current, initialParent) { mutableStateOf(current?.id_proceso?.toString().orEmpty()) }
    var name by remember(current, initialParent) { mutableStateOf(current?.nombre.orEmpty()) }
    var description by remember(current, initialParent) { mutableStateOf(current?.descripcion.orEmpty()) }
    var reference by remember(current, initialParent) { mutableStateOf(current?.nombre_corto.orEmpty()) }
    var time by remember(current, initialParent) { mutableStateOf(current?.tiempo_estimado?.toString() ?: "0") }
    var cost by remember(current, initialParent) { mutableStateOf(current?.costo_estimado?.toString() ?: "0.00") }
    var precondition by remember(current, initialParent) { mutableStateOf(current?.precondicion.orEmpty()) }
    var parent by remember(current, initialParent) { mutableStateOf(initialParent) }
    var activity by remember(current, initialParent) { mutableStateOf(current?.id_actividad) }
    var parentMenu by remember { mutableStateOf(false) }
    var activityMenu by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val forbiddenParents = current?.let { descendantProcessIds(processes, it.id_proceso) + it.id_proceso }.orEmpty()
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() }, title = { Text(if (current == null) "Crear proceso" else "Modificar proceso") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(code, { code = it }, label = { Text("Código") }, enabled = current == null, singleLine = true)
            OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
            OutlinedTextField(description, { description = it }, label = { Text("Descripción (opcional)") })
            OutlinedTextField(reference, { reference = it }, label = { Text("Referencia (opcional)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(time, { time = it }, label = { Text("Tiempo") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(cost, { cost = it }, label = { Text("Costo") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField("1", {}, label = { Text("Tipo") }, modifier = Modifier.weight(1f), singleLine = true, readOnly = true)
            }
            OutlinedTextField(precondition, { precondition = it }, label = { Text("Precondición (opcional)") })
            Column { OutlinedButton(onClick = { parentMenu = true }) { Text(parent?.let { id -> processes.firstOrNull { it.id_proceso == id }?.let { "Padre: ${it.nombre} (${it.id_proceso})" } } ?: "Sin padre / Proceso raíz") }; DropdownMenu(parentMenu, { parentMenu = false }) { DropdownMenuItem({ Text("Sin padre / Proceso raíz") }, { parent = null; parentMenu = false }); processes.filterNot { it.id_proceso in forbiddenParents }.forEach { option -> DropdownMenuItem({ Text("${option.nombre} (${option.id_proceso})") }, { parent = option.id_proceso; parentMenu = false }) } } }
            Column { OutlinedButton(onClick = { activityMenu = true }) { Text(activity?.let { id -> activities.firstOrNull { it.id_actividad == id }?.let { "Actividad: ${it.nombre}" } } ?: "Sin actividad") }; DropdownMenu(activityMenu, { activityMenu = false }) { DropdownMenuItem({ Text("Sin actividad") }, { activity = null; activityMenu = false }); activities.forEach { option -> DropdownMenuItem({ Text("${option.nombre} (${option.id_actividad})") }, { activity = option.id_actividad; activityMenu = false }) } } }
            formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (saving) Text("Guardado pendiente de confirmación…")
        } },
        dismissButton = { OutlinedButton(enabled = !saving, onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = { Button(enabled = !saving, onClick = {
            formError = processFormError(processes, current?.id_proceso, code, name, reference, time, cost, "1", parent)
            if (formError != null) return@Button
            saving = true
            val payload = ProcessAdminIn(code.toInt(), name.trim(), description.trim().ifEmpty { null }, parent, time.toIntOrNull(), cost.toDoubleOrNull(), 1, precondition.trim().ifEmpty { null }, activity, reference.trim().ifEmpty { null }, current?.fingerprint)
            scope.launch {
                try { onSave(payload) }
                catch (failure: Exception) { formError = processFailureMessage(failure, "El guardado no fue confirmado") }
                finally { saving = false }
            }
        }) { Text("Guardar") } }
    )
}
