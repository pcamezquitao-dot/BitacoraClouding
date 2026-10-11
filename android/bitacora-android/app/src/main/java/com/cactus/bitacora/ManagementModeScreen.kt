package com.cactus.bitacora

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
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
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.ManagementSessionOut
import com.cactus.bitacora.feature.bpm.ParticipantBpmTasksScreen
import kotlinx.coroutines.launch

private enum class ManagementView { MENU, HOURS, BPM_TASKS }

@Composable
internal fun ManagementModeScreen(repository: BitacoraRepository, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<ManagementSessionOut?>(null) }
    var view by remember { mutableStateOf(ManagementView.MENU) }
    var error by remember { mutableStateOf<String?>(null) }
    val active = session
    if (active != null) {
        if (view == ManagementView.HOURS) {
            ManagementReportHoursScreen(repository, active, onBack = { view = ManagementView.MENU })
        } else if (view == ManagementView.BPM_TASKS) {
            ParticipantBpmTasksScreen(active.codigo) { view = ManagementView.MENU }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Gerencia", style = MaterialTheme.typography.headlineSmall)
                Text("${active.codigo} · ${active.nombre_completo}")
                Button({ view = ManagementView.HOURS }, modifier = Modifier.fillMaxWidth()) { Text("Reporte de horas") }
                Button({ view = ManagementView.BPM_TASKS }, modifier = Modifier.fillMaxWidth()) { Text("Tareas BPM") }
                OutlinedButton({ session = null; code = "" }, modifier = Modifier.fillMaxWidth()) { Text("Cambiar participante") }
                OutlinedButton(onExit, modifier = Modifier.fillMaxWidth()) { Text("Salir del modulo") }
            }
        }
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Identificación de Gerencia", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(code, { code = it }, label = { Text("Código del directivo") }, modifier = Modifier.fillMaxWidth())
        Button({ scope.launch { runCatching { repository.identifyManagement(code) }.onSuccess { session = it; view = ManagementView.MENU; error = null }.onFailure { error = it.message ?: "Acceso a Gerencia rechazado" } } }, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onExit, modifier = Modifier.fillMaxWidth()) { Text("Volver al menú principal") }
    }
}
