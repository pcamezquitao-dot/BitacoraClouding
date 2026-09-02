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
import kotlinx.coroutines.launch

@Composable
internal fun ManagementModeScreen(repository: BitacoraRepository, onExit: () -> Unit) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var session by remember { mutableStateOf<ManagementSessionOut?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val active = session
    if (active != null) {
        ManagementReportHoursScreen(repository, active, onBack = { session = null })
        return
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Identificación de Gerencia", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(code, { code = it }, label = { Text("Código del directivo") }, modifier = Modifier.fillMaxWidth())
        Button({ scope.launch { runCatching { repository.identifyManagement(code) }.onSuccess { session = it; error = null }.onFailure { error = it.message ?: "Acceso a Gerencia rechazado" } } }, modifier = Modifier.fillMaxWidth()) { Text("Continuar") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedButton(onExit, modifier = Modifier.fillMaxWidth()) { Text("Volver al menú principal") }
    }
}
