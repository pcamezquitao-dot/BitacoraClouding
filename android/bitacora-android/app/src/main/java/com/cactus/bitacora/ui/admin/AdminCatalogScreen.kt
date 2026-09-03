package com.cactus.bitacora.ui.admin

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.AreaTreeNodeOut
import com.cactus.bitacora.model.CalendarHolidayUpdateIn
import com.cactus.bitacora.model.CalendarTreeNodeOut
import com.cactus.bitacora.model.EmployeeAreaAdminIn
import com.cactus.bitacora.model.EmployeeAreaAssignmentOut
import com.cactus.bitacora.model.EmployeeAreaTreeNodeOut
import com.cactus.bitacora.model.EmployeeAreaUpdateIn
import com.cactus.bitacora.model.ParticipantOptionOut
import com.cactus.bitacora.model.ParticipantTypeAdminOut
import com.cactus.bitacora.model.ParticipanteOut
import kotlinx.coroutines.launch

private val availableCapabilities = listOf("EMPLEADO", "SUPERVISOR", "GERENTE")

private enum class AdminMasterSection {
    PARTICIPANTS,
    PARTICIPANT_TYPES,
    EMPLOYEE_AREA,
    ADMINISTRATIVE_AREAS,
    GENERAL_CALENDAR,
    WORK_SCHEDULES
}

internal fun canEditCalendarNode(node: CalendarTreeNodeOut): Boolean =
    node.nivel == "DIA"

internal fun validCalendarHolidayName(isHoliday: Boolean, name: String): Boolean =
    !isHoliday || name.isNotBlank()

internal fun visibleCalendarNodeIds(
    roots: List<CalendarTreeNodeOut>,
    expandedIds: Set<Long>
): List<Long> = buildList {
    fun visit(node: CalendarTreeNodeOut) {
        add(node.id_periodo)
        if (node.id_periodo in expandedIds) node.hijos.forEach(::visit)
    }
    roots.forEach(::visit)
}

@Composable
private fun CalendarTreeRows(
    nodes: List<CalendarTreeNodeOut>,
    expandedIds: Set<Long>,
    depth: Int = 0,
    onToggle: (Long) -> Unit,
    onEdit: (CalendarTreeNodeOut) -> Unit
) {
    nodes.forEach { node ->
        val hasChildren = node.hijos.isNotEmpty()
        val isDay = canEditCalendarNode(node)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 12).dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { if (hasChildren) onToggle(node.id_periodo) }
            ) {
                val marker = when {
                    hasChildren && node.id_periodo in expandedIds -> "▼"
                    hasChildren -> "▶"
                    node.es_festivo -> "★"
                    else -> "•"
                }
                val dayDetail = if (isDay) {
                    buildString {
                        append(" · ${node.fecha_inicio}")
                        node.nombre_dia_semana?.let { append(" · $it") }
                        if (node.es_festivo) {
                            append(" · Festivo")
                            node.nombre_festivo?.let { append(": $it") }
                        }
                    }
                } else ""
                Text("$marker ${node.nombre}$dayDetail")
            }
            if (isDay) {
                Button(onClick = { onEdit(node) }) { Text("Editar") }
            }
        }
        if (hasChildren && node.id_periodo in expandedIds) {
            CalendarTreeRows(node.hijos, expandedIds, depth + 1, onToggle, onEdit)
        }
    }
}

internal fun visibleAreaTreeNodes(
    areas: List<AreaTreeNodeOut>,
    expandedAreaIds: Set<Int>
): List<AreaTreeNodeOut> {
    val areasById = areas.associateBy(AreaTreeNodeOut::id_area)
    return areas.filter { area ->
        var parentId = area.id_padre
        val visited = mutableSetOf<Int>()
        while (parentId != null && parentId != 0) {
            if (!visited.add(parentId)) return@filter false
            val parent = areasById[parentId] ?: return@filter true
            if (parentId !in expandedAreaIds) return@filter false
            parentId = parent.id_padre
        }
        true
    }
}

internal fun orphanAreaTreeNodes(areas: List<AreaTreeNodeOut>): List<AreaTreeNodeOut> {
    val areasById = areas.associateBy(AreaTreeNodeOut::id_area)
    return areas.filter { area ->
        var parentId = area.id_padre
        val visited = mutableSetOf(area.id_area)
        while (parentId != null && parentId != 0) {
            if (!visited.add(parentId)) return@filter true
            val parent = areasById[parentId] ?: return@filter true
            parentId = parent.id_padre
        }
        false
    }
}

internal fun descendantAreaIds(
    areas: List<AreaTreeNodeOut>,
    areaId: Int
): Set<Int> {
    val childrenByParent = areas.groupBy(AreaTreeNodeOut::id_padre)
    val descendants = mutableSetOf<Int>()
    val pending = ArrayDeque(childrenByParent[areaId].orEmpty().map { it.id_area })
    while (pending.isNotEmpty()) {
        val childId = pending.removeFirst()
        if (!descendants.add(childId)) continue
        childrenByParent[childId].orEmpty().forEach { pending.addLast(it.id_area) }
    }
    descendants.remove(areaId)
    return descendants
}

@Composable
private fun AreaTreeRows(
    areas: List<AreaTreeNodeOut>,
    parentIds: Set<Int>,
    expandedAreaIds: Set<Int>,
    onToggle: (AreaTreeNodeOut) -> Unit,
    onDelete: (AreaTreeNodeOut) -> Unit,
    onEdit: (AreaTreeNodeOut) -> Unit,
    onAdd: (AreaTreeNodeOut) -> Unit
) {
    visibleAreaTreeNodes(areas, expandedAreaIds).forEach { area ->
        val hasChildren = area.id_area in parentIds
        val expanded = area.id_area in expandedAreaIds
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (area.nivel * 10).dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 2.dp),
                onClick = { onToggle(area) }
            ) {
                Text(
                    when {
                        hasChildren && expanded -> "▼ ${area.descripcion}"
                        hasChildren -> "▶ ${area.descripcion}"
                        else -> "└─ ${area.descripcion}"
                    }
                )
            }
            Button(
                modifier = Modifier.width(48.dp),
                contentPadding = PaddingValues(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                onClick = { onDelete(area) }
            ) { Text("Del") }
            Button(
                modifier = Modifier.width(52.dp),
                contentPadding = PaddingValues(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                onClick = { onEdit(area) }
            ) { Text("Edit") }
            Button(
                modifier = Modifier.width(44.dp),
                contentPadding = PaddingValues(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                onClick = { onAdd(area) }
            ) { Text("+") }
        }
    }
}

internal fun flattenEmployeeAreaTree(
    roots: List<EmployeeAreaTreeNodeOut>
): List<EmployeeAreaTreeNodeOut> = buildList {
    fun visit(node: EmployeeAreaTreeNodeOut) {
        add(node)
        node.hijos.forEach(::visit)
    }
    roots.forEach(::visit)
}

@Composable
private fun EmployeeAreaTreeRows(
    nodes: List<EmployeeAreaTreeNodeOut>,
    expandedIds: Set<Int>,
    onToggle: (Int) -> Unit,
    onAdd: (EmployeeAreaTreeNodeOut) -> Unit,
    onEdit: (EmployeeAreaAssignmentOut, EmployeeAreaTreeNodeOut) -> Unit,
    onDelete: (EmployeeAreaAssignmentOut, EmployeeAreaTreeNodeOut) -> Unit
) {
    nodes.forEach { area ->
        val expanded = area.id_area in expandedIds
        val hasContent = area.hijos.isNotEmpty() || area.participantes.isNotEmpty()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (area.nivel * 10).dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                onClick = { onToggle(area.id_area) }
            ) {
                Text(
                    "${if (hasContent && expanded) "▼" else if (hasContent) "▶" else "└─"} " +
                        "${area.descripcion} (${area.cantidad_participantes})"
                )
            }
            Button(
                modifier = Modifier.width(44.dp),
                contentPadding = PaddingValues(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                onClick = { onAdd(area) }
            ) { Text("+") }
        }
        if (expanded) {
            if (area.participantes.isEmpty()) {
                Text(
                    "Sin participantes asignados",
                    modifier = Modifier.padding(start = ((area.nivel + 1) * 14).dp)
                )
            }
            area.participantes.forEach { assignment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = ((area.nivel + 1) * 10).dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("👤 ${assignment.codigo_participante} — ${assignment.nombre_completo}")
                        Text(
                            listOfNotNull(assignment.cargo, assignment.descripcion)
                                .joinToString(" — ")
                        )
                    }
                    Button(
                        modifier = Modifier.width(48.dp),
                        contentPadding = PaddingValues(2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        onClick = { onDelete(assignment, area) }
                    ) { Text("Del") }
                    Button(
                        modifier = Modifier.width(52.dp),
                        contentPadding = PaddingValues(2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0)),
                        onClick = { onEdit(assignment, area) }
                    ) { Text("Edit") }
                }
            }
            EmployeeAreaTreeRows(
                area.hijos,
                expandedIds,
                onToggle,
                onAdd,
                onEdit,
                onDelete
            )
        }
    }
}

@Composable
fun AdminCatalogScreen(
    repository: BitacoraRepository,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var actor by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var participantRefreshToken by remember { mutableStateOf(0) }
    var types by remember { mutableStateOf<List<ParticipantTypeAdminOut>>(emptyList()) }
    var areas by remember { mutableStateOf<List<AreaTreeNodeOut>>(emptyList()) }
    var typeDescription by remember { mutableStateOf("") }
    var selectedCapabilities by remember { mutableStateOf(setOf("EMPLEADO")) }
    var editingType by remember { mutableStateOf<ParticipantTypeAdminOut?>(null) }
    var participantQuery by remember { mutableStateOf("") }
    var participants by remember { mutableStateOf<List<ParticipanteOut>>(emptyList()) }
    var selectedParticipant by remember { mutableStateOf<ParticipanteOut?>(null) }
    var selectedArea by remember { mutableStateOf<AreaTreeNodeOut?>(null) }
    var selectedType by remember { mutableStateOf<ParticipantTypeAdminOut?>(null) }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var assignmentDescription by remember { mutableStateOf("") }
    var section by remember { mutableStateOf<AdminMasterSection?>(null) }
    var expandedAreaIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var loadingAreas by remember { mutableStateOf(false) }
    var areaError by remember { mutableStateOf<String?>(null) }
    var areaActionMessage by remember { mutableStateOf<String?>(null) }
    var areaActionError by remember { mutableStateOf<String?>(null) }
    var editingArea by remember { mutableStateOf<AreaTreeNodeOut?>(null) }
    var addingChildTo by remember { mutableStateOf<AreaTreeNodeOut?>(null) }
    var deletingArea by remember { mutableStateOf<AreaTreeNodeOut?>(null) }
    var areaDescription by remember { mutableStateOf("") }
    var areaShortName by remember { mutableStateOf("") }
    var areaParentId by remember { mutableStateOf("") }
    var areaActionLoading by remember { mutableStateOf(false) }
    var parentMenuExpanded by remember { mutableStateOf(false) }
    var employeeAreaTree by remember {
        mutableStateOf<List<EmployeeAreaTreeNodeOut>>(emptyList())
    }
    var expandedEmployeeAreaIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var employeeTreeLoading by remember { mutableStateOf(false) }
    var employeeTreeError by remember { mutableStateOf<String?>(null) }
    var assignmentArea by remember { mutableStateOf<EmployeeAreaTreeNodeOut?>(null) }
    var editingAssignment by remember { mutableStateOf<EmployeeAreaAssignmentOut?>(null) }
    var deletingAssignment by remember {
        mutableStateOf<Pair<EmployeeAreaAssignmentOut, EmployeeAreaTreeNodeOut>?>(null)
    }
    var participantOptions by remember {
        mutableStateOf<List<ParticipantOptionOut>>(emptyList())
    }
    var selectedParticipantOption by remember { mutableStateOf<ParticipantOptionOut?>(null) }
    var assignmentSearch by remember { mutableStateOf("") }
    var assignmentActionLoading by remember { mutableStateOf(false) }
    var assignmentError by remember { mutableStateOf<String?>(null) }
    var assignmentMessage by remember { mutableStateOf<String?>(null) }
    var assignmentAreaMenuExpanded by remember { mutableStateOf(false) }
    var calendarTree by remember { mutableStateOf<List<CalendarTreeNodeOut>>(emptyList()) }
    var expandedCalendarIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var calendarLoading by remember { mutableStateOf(false) }
    var calendarError by remember { mutableStateOf<String?>(null) }
    var calendarMessage by remember { mutableStateOf<String?>(null) }
    var editingCalendarDay by remember { mutableStateOf<CalendarTreeNodeOut?>(null) }
    var calendarIsHoliday by remember { mutableStateOf(false) }
    var calendarHolidayName by remember { mutableStateOf("") }
    var calendarSaving by remember { mutableStateOf(false) }

    fun requireActor(): String =
        actor.trim().takeIf(String::isNotEmpty)
            ?: throw IllegalArgumentException("Identifique al administrador")

    fun acceptAreas(loadedAreas: List<AreaTreeNodeOut>) {
        val firstLoad = areas.isEmpty()
        areas = loadedAreas
        val parentIds = loadedAreas.mapNotNull(AreaTreeNodeOut::id_padre).toSet()
        val validIds = loadedAreas.mapTo(mutableSetOf(), AreaTreeNodeOut::id_area)
        expandedAreaIds = if (firstLoad) {
            loadedAreas
                .filter { it.id_area in parentIds }
                .mapTo(mutableSetOf(), AreaTreeNodeOut::id_area)
        } else {
            expandedAreaIds.intersect(validIds)
        }
    }

    fun loadAreas() {
        loadingAreas = true
        areaError = null
        scope.launch {
            try {
                acceptAreas(repository.adminAreaTree(actor))
            } catch (error: Exception) {
                areaError = error.message
                    ?: "No fue posible transformar la respuesta de áreas"
            } finally {
                loadingAreas = false
            }
        }
    }

    fun loadEmployeeAreaTree() {
        employeeTreeLoading = true
        employeeTreeError = null
        scope.launch {
            try {
                val loaded = repository.adminEmployeeAreaTree(actor)
                val ids = flattenEmployeeAreaTree(loaded).mapTo(mutableSetOf()) { it.id_area }
                val firstLoad = employeeAreaTree.isEmpty()
                employeeAreaTree = loaded
                expandedEmployeeAreaIds = if (firstLoad) ids else {
                    expandedEmployeeAreaIds.intersect(ids)
                }
                types = repository.adminEmployeeAreaTypes(
                    actor.trim().ifBlank { "administrador-consulta" }
                )
                Log.i(
                    "AdminEmployeeArea",
                    "Áreas=${ids.size}; asignaciones=" +
                        flattenEmployeeAreaTree(loaded).sumOf { it.participantes.size }
                )
            } catch (error: Exception) {
                employeeTreeError = error.message
                    ?: "No fue posible cargar empleado-área"
            } finally {
                employeeTreeLoading = false
            }
        }
    }

    fun loadCalendarTree() {
        calendarLoading = true
        calendarError = null
        scope.launch {
            try {
                val loaded = repository.adminCalendarTree(actor)
                val validIds = mutableSetOf<Long>()
                fun collect(nodes: List<CalendarTreeNodeOut>) {
                    nodes.forEach { node ->
                        validIds += node.id_periodo
                        collect(node.hijos)
                    }
                }
                collect(loaded)
                calendarTree = loaded
                expandedCalendarIds = expandedCalendarIds.intersect(validIds)
            } catch (error: Exception) {
                calendarError = error.message ?: "No fue posible cargar el calendario"
            } finally {
                calendarLoading = false
            }
        }
    }

    fun beginCalendarEdit(day: CalendarTreeNodeOut) {
        if (!canEditCalendarNode(day)) return
        editingCalendarDay = day
        calendarIsHoliday = day.es_festivo
        calendarHolidayName = day.nombre_festivo.orEmpty()
        calendarError = null
        calendarMessage = null
    }

    fun saveCalendarHoliday() {
        val day = editingCalendarDay ?: return
        if (!validCalendarHolidayName(calendarIsHoliday, calendarHolidayName)) {
            calendarError = "El nombre del festivo es obligatorio"
            return
        }
        calendarSaving = true
        calendarError = null
        scope.launch {
            try {
                repository.updateAdminCalendarHoliday(
                    requireActor(),
                    day.id_periodo,
                    CalendarHolidayUpdateIn(
                        es_festivo = calendarIsHoliday,
                        nombre_festivo = calendarHolidayName.trim().takeIf {
                            calendarIsHoliday
                        }
                    )
                )
                editingCalendarDay = null
                calendarMessage = "Festivo actualizado correctamente"
                loadCalendarTree()
            } catch (error: Exception) {
                calendarError = error.message ?: "No fue posible actualizar el festivo"
            } finally {
                calendarSaving = false
            }
        }
    }

    fun closeAssignmentForm() {
        assignmentArea = null
        editingAssignment = null
        selectedParticipantOption = null
        assignmentSearch = ""
        assignmentDescription = ""
        startDate = ""
        endDate = ""
        selectedType = null
        assignmentError = null
        assignmentAreaMenuExpanded = false
    }

    fun beginAssignmentAdd(area: EmployeeAreaTreeNodeOut) {
        assignmentArea = area
        editingAssignment = null
        selectedParticipantOption = null
        assignmentSearch = ""
        assignmentDescription = ""
        startDate = java.text.SimpleDateFormat(
            "yyyy-MM-dd",
            java.util.Locale.US
        ).format(java.util.Date())
        endDate = ""
        selectedType = null
        assignmentError = null
        assignmentMessage = null
        expandedEmployeeAreaIds = expandedEmployeeAreaIds + area.id_area
        scope.launch {
            try {
                participantOptions = repository.adminParticipantOptions(actor)
                Log.i(
                    "AdminEmployeeArea",
                    "Opciones de participantes=${participantOptions.size}; área=${area.id_area}"
                )
            } catch (error: Exception) {
                assignmentError = error.message
                    ?: "Error al consultar participantes"
            }
        }
    }

    fun beginAssignmentEdit(
        assignment: EmployeeAreaAssignmentOut,
        area: EmployeeAreaTreeNodeOut
    ) {
        assignmentArea = area
        editingAssignment = assignment
        selectedParticipantOption = null
        assignmentSearch = ""
        assignmentDescription = assignment.descripcion.orEmpty()
        startDate = assignment.fecha_inicia
        endDate = assignment.fecha_final.orEmpty()
        selectedType = types.firstOrNull { it.codigo == assignment.codigo_tipo }
        assignmentError = null
        assignmentMessage = null
    }

    fun saveAssignment() {
        val area = assignmentArea ?: return
        val type = selectedType
        if (type == null) {
            assignmentError = "Seleccione un cargo"
            return
        }
        if (startDate.isBlank()) {
            assignmentError = "La fecha inicial es obligatoria"
            return
        }
        val current = editingAssignment
        val participant = selectedParticipantOption
        if (current == null && participant == null) {
            assignmentError = "Seleccione un participante"
            return
        }
        assignmentActionLoading = true
        assignmentError = null
        scope.launch {
            try {
                val normalizedActor = requireActor()
                if (current == null) {
                    repository.createAdminEmployeeArea(
                        normalizedActor,
                        EmployeeAreaAdminIn(
                            id_participante = participant!!.id_participante,
                            id_area = area.id_area,
                            codigo_tipo = type.codigo,
                            descripcion = assignmentDescription.trim()
                                .takeIf(String::isNotEmpty),
                            fecha_inicia = startDate.trim(),
                            fecha_final = endDate.trim().takeIf(String::isNotEmpty)
                        )
                    )
                    assignmentMessage = "Asignación creada correctamente"
                } else {
                    repository.updateAdminEmployeeArea(
                        normalizedActor,
                        current.id_empleado_area,
                        EmployeeAreaUpdateIn(
                            id_area = area.id_area,
                            codigo_tipo = type.codigo,
                            descripcion = assignmentDescription.trim()
                                .takeIf(String::isNotEmpty),
                            fecha_inicia = startDate.trim(),
                            fecha_final = endDate.trim().takeIf(String::isNotEmpty)
                        )
                    )
                    assignmentMessage = "Asignación actualizada correctamente"
                }
                val expandedArea = area.id_area
                closeAssignmentForm()
                expandedEmployeeAreaIds = expandedEmployeeAreaIds + expandedArea
                loadEmployeeAreaTree()
            } catch (error: Exception) {
                assignmentError = error.message ?: "Error al guardar"
            } finally {
                assignmentActionLoading = false
            }
        }
    }

    fun beginEdit(area: AreaTreeNodeOut) {
        Log.i("AdminAreaAction", "Botón EDIT pulsado; id=${area.id_area}")
        addingChildTo = null
        editingArea = area
        areaDescription = area.descripcion
        areaShortName = area.nombre_corto.orEmpty()
        areaParentId = area.id_padre?.takeUnless { it == 0 }?.toString().orEmpty()
        areaActionError = null
        areaActionMessage = null
    }

    fun beginAdd(area: AreaTreeNodeOut) {
        Log.i("AdminAreaAction", "Botón ADD pulsado; id=${area.id_area}")
        editingArea = null
        addingChildTo = area
        areaDescription = ""
        areaShortName = ""
        areaParentId = area.id_area.toString()
        expandedAreaIds = expandedAreaIds + area.id_area
        areaActionError = null
        areaActionMessage = null
    }

    fun closeAreaForm() {
        editingArea = null
        addingChildTo = null
        areaDescription = ""
        areaShortName = ""
        areaParentId = ""
        areaActionError = null
        parentMenuExpanded = false
    }

    fun saveArea() {
        val description = areaDescription.trim()
        if (description.isEmpty()) {
            areaActionError = "La descripción es obligatoria"
            return
        }
        val parentId = areaParentId.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
        if (areaParentId.isNotBlank() && parentId == null) {
            areaActionError = "El ID del área padre debe ser numérico"
            return
        }
        areaActionLoading = true
        areaActionError = null
        scope.launch {
            try {
                val normalizedActor = requireActor()
                val current = editingArea
                if (current != null) {
                    repository.updateAdminArea(
                        normalizedActor,
                        current.id_area,
                        description,
                        areaShortName.trim().ifBlank { null },
                        parentId
                    )
                    parentId?.let {
                        expandedAreaIds = expandedAreaIds + it
                    }
                    areaActionMessage = "Área administrativa actualizada"
                } else {
                    repository.createAdminArea(
                        normalizedActor,
                        description,
                        areaShortName.trim().ifBlank { null },
                        addingChildTo?.id_area
                    )
                    areaActionMessage = "Nueva rama creada"
                }
                closeAreaForm()
                loadAreas()
            } catch (error: Exception) {
                areaActionError = error.message ?: "No fue posible guardar el área"
            } finally {
                areaActionLoading = false
            }
        }
    }

    fun refresh() {
        loading = true
        message = null
        scope.launch {
            try {
                val normalizedActor = requireActor()
                types = repository.adminParticipantTypes(normalizedActor)
                acceptAreas(repository.adminAreaTree(normalizedActor))
                message = "Catálogos administrativos cargados"
            } catch (error: Exception) {
                message = error.message ?: "No fue posible cargar los catálogos"
            } finally {
                loading = false
            }
        }
    }

    fun refreshParticipants() {
        loading = true
        message = null
        scope.launch {
            val result = repository.syncParticipants()
            if (result.success) {
                participantRefreshToken++
                message = "Participantes actualizados: ${result.participants}"
            } else {
                message = result.error ?: "No fue posible actualizar participantes"
            }
            loading = false
        }
    }

    LaunchedEffect(section) {
        if (section == AdminMasterSection.ADMINISTRATIVE_AREAS) {
            loadAreas()
        }
        if (section == AdminMasterSection.EMPLOYEE_AREA) {
            loadEmployeeAreaTree()
        }
        if (section == AdminMasterSection.GENERAL_CALENDAR) {
            loadCalendarTree()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onBack
        ) { Text("Volver") }
        Text("Administrar maestros", style = MaterialTheme.typography.titleLarge)
        if (section == null) {
            Text("Seleccione la tabla maestra que desea administrar.")
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { section = AdminMasterSection.PARTICIPANTS }
            ) { Text("Participantes") }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { section = AdminMasterSection.PARTICIPANT_TYPES }
            ) { Text("Tipos de participante") }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { section = AdminMasterSection.EMPLOYEE_AREA }
            ) { Text("Empleado - área") }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { section = AdminMasterSection.ADMINISTRATIVE_AREAS }
            ) { Text("Áreas administrativas") }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { section = AdminMasterSection.GENERAL_CALENDAR }
            ) { Text("Calendario general") }
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { section = AdminMasterSection.WORK_SCHEDULES }
            ) { Text(WORK_SCHEDULES_LABEL) }
            return@Column
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { section = null }
        ) { Text("Volver a maestros") }
        if (section != AdminMasterSection.WORK_SCHEDULES) {
            Text("Las modificaciones requieren conexión y quedan auditadas.")
            OutlinedTextField(
                value = actor,
                onValueChange = { actor = it },
                label = { Text("Identificación del administrador") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(
                enabled = !loading,
                onClick = {
                    when (section) {
                        AdminMasterSection.PARTICIPANTS -> refreshParticipants()
                        AdminMasterSection.GENERAL_CALENDAR -> loadCalendarTree()
                        AdminMasterSection.ADMINISTRATIVE_AREAS -> loadAreas()
                        AdminMasterSection.EMPLOYEE_AREA -> loadEmployeeAreaTree()
                        else -> refresh()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading || calendarLoading) "Cargando…" else "Actualizar") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }

        if (section == AdminMasterSection.PARTICIPANTS) {
            ParticipantsAdminPanel(
                repository = repository,
                actor = actor,
                refreshToken = participantRefreshToken
            )
        }

        if (section == AdminMasterSection.PARTICIPANT_TYPES) {
            Text("Tipos de participante", style = MaterialTheme.typography.titleMedium)
            types.forEach { type ->
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    editingType = type
                    typeDescription = type.descripcion
                    selectedCapabilities = type.capacidades.toSet()
                    selectedType = type.takeIf { it.activo }
                }
            ) {
                Text(
                    "${type.codigo} · ${type.descripcion} · " +
                        type.capacidades.joinToString() +
                        if (type.activo) "" else " · INACTIVO"
                )
            }
            }
            OutlinedTextField(
            value = typeDescription,
            onValueChange = { typeDescription = it },
            label = { Text("Descripción del tipo") },
            modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            availableCapabilities.forEach { capability ->
                FilterChip(
                    selected = capability in selectedCapabilities,
                    onClick = {
                        selectedCapabilities =
                            if (capability in selectedCapabilities) {
                                selectedCapabilities - capability
                            } else {
                                selectedCapabilities + capability
                            }
                    },
                    label = { Text(capability) }
                )
            }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !loading,
                onClick = {
                    loading = true
                    scope.launch {
                        try {
                            val normalizedActor = requireActor()
                            val current = editingType
                            if (current == null) {
                                repository.createAdminParticipantType(
                                    normalizedActor,
                                    typeDescription,
                                    selectedCapabilities.toList()
                                )
                            } else {
                                repository.updateAdminParticipantType(
                                    normalizedActor,
                                    current.codigo,
                                    typeDescription,
                                    selectedCapabilities.toList()
                                )
                            }
                            editingType = null
                            typeDescription = ""
                            selectedCapabilities = setOf("EMPLEADO")
                            message = "Tipo guardado"
                            types = repository.adminParticipantTypes(normalizedActor)
                        } catch (error: Exception) {
                            message = error.message ?: "No fue posible guardar el tipo"
                        } finally {
                            loading = false
                        }
                    }
                }
            ) { Text(if (editingType == null) "Crear tipo" else "Guardar cambios") }
            editingType?.let { type ->
                OutlinedButton(
                    enabled = !loading,
                    onClick = {
                        loading = true
                        scope.launch {
                            try {
                                val normalizedActor = requireActor()
                                repository.setAdminParticipantTypeStatus(
                                    normalizedActor,
                                    type.codigo,
                                    !type.activo
                                )
                                types = repository.adminParticipantTypes(normalizedActor)
                                editingType = null
                                message = if (type.activo) {
                                    "Tipo desactivado"
                                } else {
                                    "Tipo activado"
                                }
                            } catch (error: Exception) {
                                message = error.message
                                    ?: "No fue posible cambiar el estado"
                            } finally {
                                loading = false
                            }
                        }
                    }
                ) { Text(if (type.activo) "Desactivar" else "Activar") }
            }
            }
        }

        if (section == AdminMasterSection.ADMINISTRATIVE_AREAS) {
            Text("Árbol de áreas administrativas", style = MaterialTheme.typography.titleMedium)
            if (loadingAreas) {
                Text("Cargando áreas…")
            }
            areaError?.let {
                Text("Error al cargar áreas: $it", color = MaterialTheme.colorScheme.error)
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = ::loadAreas
                ) { Text("Reintentar") }
            }
            if (!loadingAreas && areaError == null && areas.isEmpty()) {
                Text("El servidor no devolvió áreas administrativas.")
            }
            val orphanAreas = orphanAreaTreeNodes(areas)
            val orphanIds = orphanAreas.mapTo(mutableSetOf(), AreaTreeNodeOut::id_area)
            val regularAreas = areas.filterNot { it.id_area in orphanIds }
            val parentIds = areas.mapNotNull(AreaTreeNodeOut::id_padre).toSet()
            val toggleArea: (AreaTreeNodeOut) -> Unit = { area ->
                selectedArea = area
                if (area.id_area in parentIds) {
                    expandedAreaIds = if (area.id_area in expandedAreaIds) {
                        expandedAreaIds - area.id_area
                    } else {
                        expandedAreaIds + area.id_area
                    }
                }
            }
            AreaTreeRows(
                regularAreas,
                parentIds,
                expandedAreaIds,
                toggleArea,
                onDelete = { deletingArea = it },
                onEdit = ::beginEdit,
                onAdd = ::beginAdd
            )
            if (orphanAreas.isNotEmpty()) {
                Text(
                    "Áreas sin padre válido",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                AreaTreeRows(
                    orphanAreas,
                    parentIds,
                    expandedAreaIds,
                    toggleArea,
                    onDelete = { deletingArea = it },
                    onEdit = ::beginEdit,
                    onAdd = ::beginAdd
                )
            }
            selectedArea?.let { Text("Área seleccionada: ${it.ruta}") }
            areaActionMessage?.let {
                Text(it, color = Color(0xFF2E7D32))
            }
            if (editingArea == null && addingChildTo == null) {
                areaActionError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        if (section == AdminMasterSection.EMPLOYEE_AREA) {
            Text("Empleado–área", style = MaterialTheme.typography.titleMedium)
            if (employeeTreeLoading) Text("Cargando árbol…")
            employeeTreeError?.let {
                Text("Error al consultar asignaciones: $it", color = MaterialTheme.colorScheme.error)
                Button(onClick = ::loadEmployeeAreaTree) { Text("Reintentar") }
            }
            assignmentMessage?.let { Text(it, color = Color(0xFF2E7D32)) }
            if (assignmentArea == null) {
                assignmentError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            }
            EmployeeAreaTreeRows(
                nodes = employeeAreaTree,
                expandedIds = expandedEmployeeAreaIds,
                onToggle = { areaId ->
                    expandedEmployeeAreaIds =
                        if (areaId in expandedEmployeeAreaIds) {
                            expandedEmployeeAreaIds - areaId
                        } else {
                            expandedEmployeeAreaIds + areaId
                        }
                },
                onAdd = ::beginAssignmentAdd,
                onEdit = ::beginAssignmentEdit,
                onDelete = { assignment, area ->
                    deletingAssignment = assignment to area
                }
            )
        }

        if (section == AdminMasterSection.GENERAL_CALENDAR) {
            Text("Calendario general", style = MaterialTheme.typography.titleMedium)
            if (calendarLoading) Text("Cargando calendario…")
            calendarError?.takeIf { editingCalendarDay == null }?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            calendarMessage?.let { Text(it, color = Color(0xFF2E7D32)) }
            if (!calendarLoading && calendarError == null && calendarTree.isEmpty()) {
                Text("El calendario no contiene periodos activos.")
            }
            CalendarTreeRows(
                nodes = calendarTree,
                expandedIds = expandedCalendarIds,
                onToggle = { id ->
                    expandedCalendarIds = if (id in expandedCalendarIds) {
                        expandedCalendarIds - id
                    } else {
                        expandedCalendarIds + id
                    }
                },
                onEdit = ::beginCalendarEdit
            )
        }

        if (section == AdminMasterSection.WORK_SCHEDULES) {
            WorkSchedulesAdminPanel(repository = repository)
        }
    }

    editingCalendarDay?.let { day ->
        AlertDialog(
            onDismissRequest = { if (!calendarSaving) editingCalendarDay = null },
            title = { Text("Editar festivo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Fecha: ${day.fecha_inicio}")
                    Text("Día: ${day.nombre_dia_semana.orEmpty()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Switch(
                            checked = calendarIsHoliday,
                            enabled = !calendarSaving,
                            onCheckedChange = {
                                calendarIsHoliday = it
                                if (!it) calendarHolidayName = ""
                            }
                        )
                        Text("Es festivo")
                    }
                    OutlinedTextField(
                        value = calendarHolidayName,
                        onValueChange = { calendarHolidayName = it },
                        enabled = calendarIsHoliday && !calendarSaving,
                        label = { Text("Nombre del festivo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    calendarError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !calendarSaving,
                    onClick = { editingCalendarDay = null }
                ) { Text("Cancelar") }
            },
            confirmButton = {
                Button(
                    enabled = !calendarSaving &&
                        validCalendarHolidayName(calendarIsHoliday, calendarHolidayName),
                    onClick = ::saveCalendarHoliday
                ) { Text(if (calendarSaving) "Guardando…" else "Guardar") }
            }
        )
    }

    assignmentArea?.let { area ->
        val current = editingAssignment
        val query = assignmentSearch.trim().lowercase()
        val filteredOptions = participantOptions.filter { option ->
            query.isEmpty() || listOf(
                option.codigo,
                option.nombre_completo,
                option.nombres,
                option.apellidos,
                option.documento.orEmpty()
            ).any { it.lowercase().contains(query) }
        }.take(12)
        AlertDialog(
            onDismissRequest = {
                if (!assignmentActionLoading) closeAssignmentForm()
            },
            title = {
                Text(
                    if (current == null) {
                        "Asignar empleado al área"
                    } else {
                        "Editar asignación empleado-área"
                    }
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (current == null) {
                        Text("Área seleccionada: ${area.ruta}")
                        OutlinedTextField(
                            value = assignmentSearch,
                            onValueChange = { assignmentSearch = it },
                            enabled = !assignmentActionLoading,
                            label = { Text("Buscar por nombre, apellido o código") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        selectedParticipantOption?.let {
                            Text("Seleccionado: ${it.codigo} — ${it.nombre_completo}")
                        }
                        if (filteredOptions.isEmpty()) {
                            Text("No se encontraron participantes con ese criterio.")
                        } else {
                            filteredOptions.forEach { option ->
                                OutlinedButton(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !assignmentActionLoading,
                                    onClick = {
                                        selectedParticipantOption = option
                                        assignmentSearch = option.codigo
                                        Log.i(
                                            "AdminEmployeeArea",
                                            "Participante seleccionado=${option.id_participante}; " +
                                                "área=${area.id_area}"
                                        )
                                    }
                                ) {
                                    Text("${option.codigo}\n${option.nombre_completo}")
                                }
                            }
                        }
                    } else {
                        Text(
                            "Participante: ${current.codigo_participante} — " +
                                current.nombre_completo
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !assignmentActionLoading,
                                onClick = { assignmentAreaMenuExpanded = true }
                            ) { Text("Área: ${area.ruta}") }
                            DropdownMenu(
                                expanded = assignmentAreaMenuExpanded,
                                onDismissRequest = {
                                    assignmentAreaMenuExpanded = false
                                }
                            ) {
                                flattenEmployeeAreaTree(employeeAreaTree).forEach { optionArea ->
                                    DropdownMenuItem(
                                        text = { Text(optionArea.ruta) },
                                        onClick = {
                                            assignmentArea = optionArea
                                            assignmentAreaMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Text("Cargo")
                    types.filter { it.activo }.forEach { type ->
                        FilterChip(
                            selected = selectedType?.codigo == type.codigo,
                            enabled = !assignmentActionLoading,
                            onClick = { selectedType = type },
                            label = { Text(type.descripcion) }
                        )
                    }
                    OutlinedTextField(
                        value = assignmentDescription,
                        onValueChange = { assignmentDescription = it },
                        enabled = !assignmentActionLoading,
                        label = { Text("Descripción de la asignación") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        enabled = !assignmentActionLoading,
                        label = { Text("Fecha inicial (AAAA-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        enabled = !assignmentActionLoading,
                        label = { Text("Fecha final opcional (AAAA-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    assignmentError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !assignmentActionLoading &&
                        selectedType != null &&
                        startDate.isNotBlank() &&
                        (current != null || selectedParticipantOption != null),
                    onClick = ::saveAssignment
                ) {
                    Text(
                        when {
                            assignmentActionLoading -> "Guardando…"
                            current == null -> "Asignar"
                            else -> "Guardar cambios"
                        }
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !assignmentActionLoading,
                    onClick = ::closeAssignmentForm
                ) { Text("Cancelar") }
            }
        )
    }

    val formArea = editingArea ?: addingChildTo
    formArea?.let { area ->
        val isEditing = editingArea != null
        val excludedParentIds = if (isEditing) {
            descendantAreaIds(areas, area.id_area) + area.id_area
        } else {
            emptySet()
        }
        val availableParents = areas.filterNot {
            it.id_area in excludedParentIds
        }
        AlertDialog(
            onDismissRequest = {
                if (!areaActionLoading) closeAreaForm()
            },
            title = {
                Text(
                    if (isEditing) {
                        "Editar área administrativa"
                    } else {
                        "Añadir nueva rama"
                    }
                )
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isEditing) {
                        Text("Área actual: ${area.descripcion}")
                        Text("ID del área: ${area.id_area}")
                    } else {
                        Text("Área padre: ${area.ruta}")
                    }
                    OutlinedTextField(
                        value = areaDescription,
                        onValueChange = { areaDescription = it },
                        enabled = !areaActionLoading,
                        label = { Text("Descripción") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (areaDescription.isBlank() && areaActionError != null) {
                        Text(
                            "La descripción es obligatoria",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    OutlinedTextField(
                        value = areaShortName,
                        onValueChange = { areaShortName = it },
                        enabled = !areaActionLoading,
                        label = { Text("Nombre corto") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isEditing) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !areaActionLoading,
                                onClick = { parentMenuExpanded = true }
                            ) {
                                Text(
                                    areaParentId.toIntOrNull()?.let { parentId ->
                                        availableParents
                                            .firstOrNull { it.id_area == parentId }
                                            ?.let { "Área padre: ${it.ruta}" }
                                            ?: "Área padre no válida"
                                    } ?: "Sin padre (área raíz)"
                                )
                            }
                            DropdownMenu(
                                expanded = parentMenuExpanded,
                                onDismissRequest = { parentMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Sin padre (área raíz)") },
                                    onClick = {
                                        areaParentId = ""
                                        parentMenuExpanded = false
                                    }
                                )
                                availableParents.forEach { parent ->
                                    DropdownMenuItem(
                                        text = { Text(parent.ruta) },
                                        onClick = {
                                            areaParentId = parent.id_area.toString()
                                            parentMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    Text("La ruta se calcula automáticamente según el área padre.")
                    areaActionError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !areaActionLoading,
                    onClick = ::saveArea
                ) {
                    Text(
                        when {
                            areaActionLoading -> "Guardando…"
                            isEditing -> "Guardar cambios"
                            else -> "Crear rama"
                        }
                    )
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !areaActionLoading,
                    onClick = ::closeAreaForm
                ) { Text("Cancelar") }
            }
        )
    }

    deletingAssignment?.let { (assignment, area) ->
        AlertDialog(
            onDismissRequest = {
                if (!assignmentActionLoading) deletingAssignment = null
            },
            title = { Text("Retirar asignación del área") },
            text = {
                Text(
                    "¿Está seguro de retirar a ${assignment.codigo_participante} — " +
                        "${assignment.nombre_completo} del área “${area.descripcion}”?"
                )
            },
            confirmButton = {
                Button(
                    enabled = !assignmentActionLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        assignmentActionLoading = true
                        assignmentError = null
                        scope.launch {
                            try {
                                repository.retireAdminEmployeeArea(
                                    requireActor(),
                                    assignment.id_empleado_area
                                )
                                assignmentMessage = "Asignación retirada correctamente"
                                deletingAssignment = null
                                loadEmployeeAreaTree()
                            } catch (error: Exception) {
                                assignmentError = error.message
                                    ?: "No fue posible retirar la asignación"
                                deletingAssignment = null
                            } finally {
                                assignmentActionLoading = false
                            }
                        }
                    }
                ) { Text("Retirar") }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !assignmentActionLoading,
                    onClick = { deletingAssignment = null }
                ) { Text("Cancelar") }
            }
        )
    }

    deletingArea?.let { area ->
        AlertDialog(
            onDismissRequest = { deletingArea = null },
            title = { Text("Eliminar área") },
            text = {
                Text("¿Está seguro de eliminar el área “${area.descripcion}”?")
            },
            confirmButton = {
                Button(
                    enabled = !areaActionLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        Log.i(
                            "AdminAreaAction",
                            "Botón DEL confirmado; id=${area.id_area}"
                        )
                        areaActionLoading = true
                        areaActionError = null
                        scope.launch {
                            try {
                                repository.deleteAdminArea(requireActor(), area.id_area)
                                areaActionMessage = "Área administrativa eliminada"
                                deletingArea = null
                                loadAreas()
                            } catch (error: Exception) {
                                areaActionError = error.message
                                    ?: "No fue posible eliminar el área"
                                deletingArea = null
                            } finally {
                                areaActionLoading = false
                            }
                        }
                    }
                ) { Text("Eliminar") }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !areaActionLoading,
                    onClick = { deletingArea = null }
                ) { Text("Cancelar") }
            }
        )
    }
}
