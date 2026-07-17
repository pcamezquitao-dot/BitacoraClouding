package com.cactus.bitacora.ui.evidence

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.data.local.BitacoraEvidenceEntity
import com.cactus.bitacora.data.local.EvidenceType
import com.cactus.bitacora.data.local.GpsStatus
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.location.BitacoraLocationProvider
import kotlinx.coroutines.launch
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
fun EvidencePanel(repository: BitacoraRepository, bitacoraLocalId: Long, remoteId: Int?, areaId: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember { BitacoraLocationProvider(context) }
    var evidences by remember { mutableStateOf(emptyList<BitacoraEvidenceEntity>()) }
    var menu by remember { mutableStateOf(false) }
    var textDialog by remember { mutableStateOf(false) }
    var observation by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var pendingType by remember { mutableStateOf<EvidenceType?>(null) }

    fun refresh() { scope.launch { evidences = repository.getEvidences(bitacoraLocalId) } }
    suspend fun persist(file: File, type: EvidenceType, mime: String, text: String? = null) {
        val location = try { locationProvider.getCurrentLocation() } catch (e: Exception) {
            message = "No se obtuvo GPS: ${e.message}. Active la ubicación y vuelva a intentar."
            return
        }
        repository.saveEvidence(
            BitacoraEvidenceEntity(
                bitacoraLocalId = bitacoraLocalId,
                areaId = areaId,
                clientUuid = UUID.randomUUID().toString(),
                evidenceType = type,
                textContent = text,
                originalName = file.name,
                localFilePath = file.absolutePath,
                mimeType = mime,
                fileSize = file.length(),
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                gpsTimestamp = location.timestamp,
                locationProvider = location.provider,
                gpsStatus = GpsStatus.READY,
                syncStatus = SyncStatus.PENDIENTE
            )
        )
        message = "Evidencia guardada localmente"
        refresh()
    }
    fun newFile(extension: String): File {
        val directory = File(context.filesDir, "evidencias").apply { mkdirs() }
        return File(directory, "${UUID.randomUUID()}.$extension")
    }
    fun fileUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingFile; if (ok && file != null) scope.launch { persist(file, EvidenceType.PHOTO, "image/jpeg") }
        else message = "Captura de fotografía cancelada"
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val file = pendingFile; if (ok && file != null) scope.launch { persist(file, EvidenceType.VIDEO, "video/mp4") }
        else message = "Captura de video cancelada"
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val source = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && source != null) {
            val file = newFile("m4a")
            context.contentResolver.openInputStream(source)?.use { input -> file.outputStream().use(input::copyTo) }
            scope.launch { persist(file, EvidenceType.AUDIO, "audio/mp4") }
        } else message = "Grabación de audio cancelada"
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.all { it }) {
            when (pendingType) {
                EvidenceType.PHOTO -> pendingFile?.let { photoLauncher.launch(fileUri(it)) }
                EvidenceType.VIDEO -> pendingFile?.let { videoLauncher.launch(fileUri(it)) }
                EvidenceType.AUDIO -> audioLauncher.launch(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION))
                else -> Unit
            }
        } else message = "Permiso rechazado. No se puede capturar la evidencia."
    }
    fun launch(type: EvidenceType) {
        menu = false; pendingType = type
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (type == EvidenceType.PHOTO || type == EvidenceType.VIDEO) permissions += Manifest.permission.CAMERA
        if (type == EvidenceType.AUDIO || type == EvidenceType.VIDEO) permissions += Manifest.permission.RECORD_AUDIO
        val missing = permissions.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        pendingFile = when (type) { EvidenceType.PHOTO -> newFile("jpg"); EvidenceType.VIDEO -> newFile("mp4"); else -> null }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray()) else when (type) {
            EvidenceType.PHOTO -> photoLauncher.launch(fileUri(pendingFile!!))
            EvidenceType.VIDEO -> videoLauncher.launch(fileUri(pendingFile!!))
            EvidenceType.AUDIO -> audioLauncher.launch(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION))
            else -> Unit
        }
    }

    LaunchedEffect(bitacoraLocalId) { refresh() }
    Surface(Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Evidencias de la bitácora", style = MaterialTheme.typography.titleMedium)
            Text("ID local: $bitacoraLocalId · ID servidor: ${remoteId ?: "pendiente"}")
            Button(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) { Text("Agregar evidencia") }
            message?.let { Text(it, color = if (it.startsWith("No") || it.contains("rechazado")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            if (evidences.isEmpty()) Text("Esta bitácora aún no tiene evidencias")
            evidences.forEach { evidence ->
                Surface(Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                    Column(Modifier.padding(8.dp)) {
                        Text("${evidence.evidenceType.label} — ${evidence.syncStatus.uiLabel}")
                        Text(evidence.textContent ?: evidence.originalName.orEmpty())
                        Text(DateFormat.getDateTimeInstance().format(Date(evidence.createdAt)))
                        evidence.lastSyncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            evidence.localFilePath?.let { path ->
                                OutlinedButton(onClick = {
                                    val file = File(path)
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(fileUri(file), evidence.mimeType ?: "*/*")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                }) { Text(if (evidence.evidenceType == EvidenceType.AUDIO) "Escuchar" else "Visualizar") }
                            }
                            if (evidence.syncStatus != SyncStatus.SINCRONIZADO) OutlinedButton(onClick = {
                                scope.launch { repository.deleteEvidence(evidence.localId); refresh() }
                            }) { Text("Eliminar") }
                        }
                    }
                }
            }
        }
    }
    if (menu) AlertDialog(onDismissRequest = { menu = false }, confirmButton = {}, title = { Text("Agregar evidencia") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Button({ launch(EvidenceType.PHOTO) }, Modifier.fillMaxWidth()) { Text("Tomar fotografía") }
            Button({ launch(EvidenceType.VIDEO) }, Modifier.fillMaxWidth()) { Text("Grabar video") }
            Button({ launch(EvidenceType.AUDIO) }, Modifier.fillMaxWidth()) { Text("Grabar audio") }
            Button({ menu = false; textDialog = true }, Modifier.fillMaxWidth()) { Text("Escribir observación textual") }
            OutlinedButton({ menu = false }, Modifier.fillMaxWidth()) { Text("Cancelar") }
        }
    })
    if (textDialog) AlertDialog(onDismissRequest = { textDialog = false }, title = { Text("Observación textual") }, text = {
        OutlinedTextField(observation, { observation = it }, label = { Text("Descripción") })
    }, confirmButton = { Button(enabled = observation.isNotBlank(), onClick = {
        val value = observation.trim(); textDialog = false; observation = ""
        scope.launch { val file = newFile("txt").apply { writeText(value) }; persist(file, EvidenceType.TEXT, "text/plain", value) }
    }) { Text("Guardar") } }, dismissButton = { OutlinedButton({ textDialog = false }) { Text("Cancelar") } })
}

private val EvidenceType.label: String get() = when (this) {
    EvidenceType.PHOTO, EvidenceType.ID_PHOTO -> "Fotografía"
    EvidenceType.VIDEO -> "Video"
    EvidenceType.AUDIO -> "Audio"
    EvidenceType.TEXT -> "Observación"
}

private val SyncStatus.uiLabel: String get() = when (this) {
    SyncStatus.LOCAL, SyncStatus.PENDIENTE, SyncStatus.PENDING_GPS -> "PENDING"
    SyncStatus.SYNCING -> "SYNCING"
    SyncStatus.SINCRONIZADO -> "SYNCED"
    SyncStatus.ERROR -> "ERROR"
}
