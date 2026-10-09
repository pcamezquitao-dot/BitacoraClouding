package com.cactus.bitacora.ui.admin

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.Cu30Definition
import com.cactus.bitacora.model.Cu30Step
import com.cactus.bitacora.model.Cu30WorkOut
import com.cactus.bitacora.model.Cu30WorkSummary
import kotlinx.coroutines.launch
import retrofit2.HttpException


internal fun cu30CanApprove(pending: List<String>, state: String): Boolean =
    pending.isEmpty() && state != "APROBADA"

internal fun selectCu30Activity(step: Cu30Step, id: Long, name: String): Cu30Step = step.copy(
    id_actividad = id, nombre_actividad = name,
    propuesta_codigo = null, propuesta_nombre = null, propuesta_descripcion = null
)

internal fun proposeCu30Activity(step: Cu30Step): Cu30Step = step.copy(
    id_actividad = null, nombre_actividad = null,
    propuesta_codigo = step.propuesta_codigo ?: "PROP-${step.paso_id}",
    propuesta_nombre = step.propuesta_nombre ?: "",
    propuesta_descripcion = step.propuesta_descripcion ?: ""
)

internal fun changeCu30StepType(step: Cu30Step, type: String): Cu30Step = step.copy(
    tipo = type,
    id_actividad = if (type == "actividad") step.id_actividad else null,
    nombre_actividad = if (type == "actividad") step.nombre_actividad else null,
    propuesta_codigo = if (type == "actividad") step.propuesta_codigo else null,
    propuesta_nombre = if (type == "actividad") step.propuesta_nombre else null,
    propuesta_descripcion = if (type == "actividad") step.propuesta_descripcion else null,
)

internal fun parseCu30Transitions(value: String): List<String> = value.split(',')
    .map(String::trim).filter(String::isNotEmpty).distinct()

internal fun parseCu30Values(value: String): List<String> = value.split(',')
    .map(String::trim).filter(String::isNotEmpty).distinct()

internal fun cu30FailureMessage(failure: Throwable, fallback: String): String {
    if (failure is HttpException) {
        val raw = runCatching { failure.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        Regex("\\\"detail\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(raw)?.groupValues?.getOrNull(1)?.let { return it }
    }
    return failure.message?.takeIf { it.isNotBlank() } ?: fallback
}

private fun share(context: Context, title: String, content: String) {
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_TEXT, content)
    }, "Exportar $title"))
}

@Composable
internal fun Cu30NormalizaProcesoPanel(repository: BitacoraRepository, actor: String) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var works by remember { mutableStateOf<List<Cu30WorkSummary>>(emptyList()) }
    var selected by remember { mutableStateOf<Cu30WorkOut?>(null) }
    var draft by remember { mutableStateOf<Cu30Definition?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var original by remember { mutableStateOf("") }
    var product by remember { mutableStateOf("texto") }
    var diagramSvg by remember { mutableStateOf<String?>(null) }

    fun loadWorks() {
        if (actor.isBlank()) { message = "Identifique al administrador"; return }
        scope.launch {
            loading = true; message = null
            runCatching { repository.cu30Works(actor) }
                .onSuccess { works = it }
                .onFailure { message = cu30FailureMessage(it, "No fue posible cargar los trabajos CU30") }
            loading = false
        }
    }
    fun open(id: String) = scope.launch {
        loading = true; message = null
        runCatching { repository.cu30Work(actor, id) }
            .onSuccess { selected = it; draft = it.version.definicion }
            .onFailure { message = cu30FailureMessage(it, "No fue posible abrir el trabajo") }
        loading = false
    }

    LaunchedEffect(actor) { if (actor.isNotBlank()) loadWorks() }

    if (selected == null) {
        Text("Normalizar proceso", style = MaterialTheme.typography.titleLarge)
        Text("Conserve el requerimiento, relacione actividades y apruebe texto, PlantUML y XML como una sola versión.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = actor.isNotBlank() && !loading, onClick = { creating = true }) { Text("Nuevo") }
            OutlinedButton(enabled = !loading, onClick = ::loadWorks) { Text("Actualizar") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (!loading && works.isEmpty()) Text("No hay trabajos CU30")
        works.forEach { work ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(work.titulo, style = MaterialTheme.typography.titleMedium)
                    Text("Versión ${work.version_actual} · ${work.estado}")
                    Text("Actualizado: ${work.actualizado_en}")
                    Button(onClick = { open(work.id_trabajo) }) { Text("Retomar") }
                }
            }
        }
    } else {
        val work = selected!!
        val definition = draft ?: work.version.definicion
        OutlinedButton(onClick = { selected = null; draft = null; loadWorks() }) { Text("Volver a trabajos") }
        Text(work.titulo, style = MaterialTheme.typography.titleLarge)
        Text("Versión ${work.version.numero} · ${work.version.estado}")
        Text("Original SHA-256: ${work.requerimiento_sha256}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        Text("Requerimiento original", style = MaterialTheme.typography.titleMedium)
        Text(work.requerimiento_original, modifier = Modifier.background(Color(0xFFF2F2F2)).padding(8.dp))
        OutlinedTextField(
            value = definition.observaciones.joinToString("\n"),
            onValueChange = { value -> draft = definition.copy(observaciones = value.lines().map(String::trim).filter(String::isNotEmpty)) },
            label = { Text("Pendientes u observaciones (una por línea)") },
            supportingText = { Text("Mientras exista una observación, la aprobación permanece bloqueada") },
            modifier = Modifier.fillMaxWidth()
        )
        Text("Correspondencias y definición", style = MaterialTheme.typography.titleMedium)
        definition.pasos.forEachIndexed { index, step ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${step.paso_id} · ${step.tipo}", style = MaterialTheme.typography.titleSmall)
                    Text(step.requerimiento)
                    Text("Tipo de paso")
                    listOf(listOf("actividad", "decision"), listOf("espera", "final")).forEach { types ->
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            types.forEach { type ->
                            FilterChip(
                                selected = step.tipo == type,
                                onClick = { draft = definition.copy(pasos = definition.pasos.mapIndexed { i, item -> if (i == index) changeCu30StepType(item, type) else item }) },
                                label = { Text(type) }
                            )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = step.siguiente.joinToString(", "),
                        onValueChange = { value -> draft = definition.copy(pasos = definition.pasos.mapIndexed { i, item -> if (i == index) item.copy(siguiente = parseCu30Transitions(value)) else item }) },
                        label = { Text("Siguientes (IDs separados por coma)") },
                        supportingText = { Text("Las decisiones requieren al menos dos destinos válidos") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (step.tipo != "final") {
                        OutlinedTextField(
                            value = step.responsable.orEmpty(),
                            onValueChange = { value -> draft = definition.copy(pasos = definition.pasos.mapIndexed { i, item -> if (i == index) item.copy(responsable = value) else item }) },
                            label = { Text("Responsable") }, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    listOf(
                        "Documentos" to step.documentos,
                        "Reglas" to step.reglas,
                        "Datos" to step.datos,
                        "Integraciones (solo documentadas)" to step.integraciones,
                    ).forEach { (label, values) ->
                        OutlinedTextField(
                            value = values.joinToString(", "),
                            onValueChange = { value ->
                                val parsed = parseCu30Values(value)
                                draft = definition.copy(pasos = definition.pasos.mapIndexed { i, item ->
                                    if (i != index) item else when (label) {
                                        "Documentos" -> item.copy(documentos = parsed)
                                        "Reglas" -> item.copy(reglas = parsed)
                                        "Datos" -> item.copy(datos = parsed)
                                        else -> item.copy(integraciones = parsed)
                                    }
                                })
                            },
                            label = { Text(label) }, modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (step.tipo == "actividad") {
                        step.candidates.forEach { candidate ->
                            OutlinedButton(
                                onClick = { draft = definition.copy(pasos = definition.pasos.mapIndexed { i, item -> if (i == index) selectCu30Activity(item, candidate.id_actividad, candidate.nombre) else item }) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column {
                                    Text("${candidate.id_actividad} · ${candidate.nombre}")
                                    Text(candidate.descripcion ?: "Sin descripción", style = MaterialTheme.typography.bodySmall)
                                    Text("Coincide: ${candidate.coincidencias.joinToString().ifEmpty { "sin coincidencia suficiente" }}", style = MaterialTheme.typography.bodySmall)
                                    if (candidate.diferencias.isNotEmpty()) Text("Difiere: ${candidate.diferencias.joinToString()}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        OutlinedButton(onClick = { draft = definition.copy(pasos = definition.pasos.mapIndexed { i, item -> if (i == index) proposeCu30Activity(item) else item }) }) { Text("Proponer actividad nueva") }
                        if (step.propuesta_codigo != null) {
                            Text("Código provisional: ${step.propuesta_codigo}")
                            OutlinedTextField(value = step.propuesta_nombre.orEmpty(), onValueChange = { value -> draft = definition.copy(pasos = definition.pasos.mapIndexed { i, item -> if (i == index) item.copy(propuesta_nombre = value) else item }) }, label = { Text("Nombre propuesto") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = step.propuesta_descripcion.orEmpty(), onValueChange = { value -> draft = definition.copy(pasos = definition.pasos.mapIndexed { i, item -> if (i == index) item.copy(propuesta_descripcion = value) else item }) }, label = { Text("Descripción funcional") }, modifier = Modifier.fillMaxWidth())
                        }
                        val choice = step.nombre_actividad ?: step.propuesta_nombre
                        Text("Selección explícita: ${choice ?: "PENDIENTE"}", color = if (choice == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Button(enabled = !loading, onClick = {
            scope.launch {
                loading = true; message = null
                runCatching { repository.regenerateCu30Work(actor, work.id_trabajo, definition, work.version.numero) }
                    .onSuccess { selected = it; draft = it.version.definicion; message = "Productos regenerados en versión ${it.version.numero}" }
                    .onFailure { message = cu30FailureMessage(it, "No fue posible regenerar") }
                loading = false
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Guardar correcciones y regenerar") }

        if (work.version.pendientes.isNotEmpty()) {
            Text("Pendientes que bloquean aprobación", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
            work.version.pendientes.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
        }
        Text("Productos de la misma versión", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("texto" to "Texto", "plantuml" to "PlantUML", "xml" to "XML").forEach { (key, label) ->
                FilterChip(selected = product == key, onClick = { product = key }, label = { Text(label) })
            }
        }
        val content = when (product) { "plantuml" -> work.version.plantuml; "xml" -> work.version.xml_definicion; else -> work.version.texto_normalizado }
        if (product == "plantuml") {
            Text("Diagrama PlantUML renderizado", style = MaterialTheme.typography.titleSmall)
            LaunchedEffect(work.version.numero) {
                diagramSvg = runCatching { repository.cu30DiagramSvg(actor, work.id_trabajo) }
                    .onFailure { message = cu30FailureMessage(it, "No fue posible renderizar PlantUML") }
                    .getOrNull()
            }
            diagramSvg?.let { svg ->
                AndroidView(
                    factory = { WebView(it).apply { settings.javaScriptEnabled = false } },
                    update = { it.loadDataWithBaseURL(null, svg, "image/svg+xml", "UTF-8", null) },
                    modifier = Modifier.fillMaxWidth().height(360.dp)
                )
            } ?: Text("Renderizando…")
        }
        Text(content, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth().background(Color(0xFF202124)).padding(8.dp), color = Color.White)
        OutlinedButton(onClick = { share(context, "cu30-v${work.version.numero}.$product", content) }) { Text("Exportar") }
        Button(
            enabled = cu30CanApprove(work.version.pendientes, work.version.estado) && !loading,
            onClick = {
                scope.launch {
                    loading = true; message = null
                    runCatching { repository.approveCu30Work(actor, work.id_trabajo, work.version.numero) }
                        .onSuccess { message = "Versión ${it.numero_version} aprobada; disponible para CU31"; selected = repository.cu30Work(actor, work.id_trabajo); draft = selected?.version?.definicion }
                        .onFailure { message = cu30FailureMessage(it, "No fue posible aprobar") }
                    loading = false
                }
            }, modifier = Modifier.fillMaxWidth()
        ) { Text("Aprobar conjuntamente texto, PlantUML y XML") }
        message?.let { Text(it, color = if (it.startsWith("Versión")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
    }

    if (creating) AlertDialog(
        onDismissRequest = { creating = false },
        title = { Text("Nuevo trabajo CU30") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Nombre del proceso") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(original, { original = it }, label = { Text("Requerimiento textual original") }, minLines = 8, modifier = Modifier.fillMaxWidth())
        } },
        confirmButton = { Button(enabled = title.isNotBlank() && original.isNotBlank() && !loading, onClick = {
            scope.launch {
                loading = true; message = null
                runCatching { repository.createCu30Work(actor, title, original) }
                    .onSuccess { selected = it; draft = it.version.definicion; creating = false; title = ""; original = "" }
                    .onFailure { message = cu30FailureMessage(it, "No fue posible crear el trabajo") }
                loading = false
            }
        }) { Text("Analizar") } },
        dismissButton = { OutlinedButton(onClick = { creating = false }) { Text("Cancelar") } }
    )
}
