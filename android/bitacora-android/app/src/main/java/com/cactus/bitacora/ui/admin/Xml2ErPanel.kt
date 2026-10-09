package com.cactus.bitacora.ui.admin

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.Xml2ErPreview
import com.cactus.bitacora.model.Xml2ErResult
import kotlinx.coroutines.launch

internal data class SelectedXml(val name: String, val content: String)

internal fun readSelectedXml(context: Context, uri: Uri): SelectedXml {
    val name = context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "proceso.xml"
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("No fue posible leer el archivo seleccionado")
    require(bytes.size <= 5 * 1024 * 1024) { "El XML supera 5 MiB" }
    return SelectedXml(name, bytes.toString(Charsets.UTF_8))
}

@Composable
fun Xml2ErPanel(repository: BitacoraRepository, actor: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf<SelectedXml?>(null) }
    var preview by remember { mutableStateOf<Xml2ErPreview?>(null) }
    var result by remember { mutableStateOf<Xml2ErResult?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { readSelectedXml(context, uri) }
            .onSuccess { selected = it; preview = null; result = null; message = "SELECCIONADO" }
            .onFailure { message = it.message ?: "No fue posible leer el XML" }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("XML2ER", style = MaterialTheme.typography.titleLarge)
        Text("Importa una definición; no inicia casos ni ejecuta actividades.")
        Text("Destino: ${com.cactus.bitacora.util.AppConfig.BASE_URL}")
        OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = { picker.launch(arrayOf("application/xml", "text/xml", "text/plain")) }) { Text("Seleccionar XML") }
        selected?.let { Text("Archivo: ${it.name}") }
        Button(modifier = Modifier.fillMaxWidth(), enabled = !loading && selected != null && actor.isNotBlank(), onClick = {
            val file = selected ?: return@Button
            loading = true; message = "VALIDANDO"; preview = null; result = null
            scope.launch { try { preview = repository.validateXml2Er(actor, file.name, file.content); message = preview?.estado } catch (e: Exception) { message = "CON ERRORES: ${e.message}" } finally { loading = false } }
        }) { Text("Validar") }
        if (loading) CircularProgressIndicator()
        preview?.let { value ->
            Text("Proceso: ${value.proceso} · versión ${value.version}")
            Text("Base/entorno: ${value.base} / ${value.entorno}")
            value.cantidades.forEach { (table, count) -> Text("$table: nuevos ${count.nuevos}, reutilizados ${count.reutilizados}, conflictos ${count.conflictos}") }
            value.conflictos.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(modifier = Modifier.fillMaxWidth(), enabled = !loading && value.estado == "VALIDADO" && value.token_validacion != null, onClick = {
                val file = selected ?: return@Button; val token = value.token_validacion ?: return@Button
                loading = true; message = "IMPORTANDO"
                scope.launch { try { result = repository.importXml2Er(actor, file.name, file.content, token); message = result?.estado } catch (e: Exception) { message = "VERIFICACIÓN PENDIENTE: ${e.message}" } finally { loading = false } }
            }) { Text("Importar") }
        }
        result?.let { value ->
            Text("${value.estado}: ${value.proceso} v${value.version}", style = MaterialTheme.typography.titleMedium)
            Text("Commit confirmado: ${if (value.confirmado) "Sí" else "No"}")
            Text("SHA-256: ${value.sha256}")
            value.correspondencias.forEach { (kind, values) -> Text("$kind: ${values.entries.joinToString { "${it.key}→${it.value}" }}") }
        }
        message?.let { Text(it, color = if (it.startsWith("CON ERRORES") || it.startsWith("VERIFICACIÓN")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = !loading && selected != null, onClick = { selected = null; preview = null; result = null; message = "Cancelado; no se realizaron escrituras" }) { Text("Cancelar") }
        }
    }
}
