package com.cactus.bitacora.ui.evidence

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class PendingEvidencePreview(
    val file: File,
    val type: EvidenceType,
    val mime: String,
    val durationSeconds: Int? = null,
    val text: String? = null
)

internal enum class EvidenceReviewAction { CANCEL, REPEAT, SAVE }

internal fun shouldPersistEvidence(action: EvidenceReviewAction): Boolean =
    action == EvidenceReviewAction.SAVE

internal fun shouldDeleteTemporaryEvidence(localMetadataSaved: Boolean): Boolean =
    localMetadataSaved

@Composable
fun EvidencePanel(
    repository: BitacoraRepository,
    bitacoraLocalId: Long,
    remoteId: Int?,
    areaId: Int
) {
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
    var preview by remember { mutableStateOf<PendingEvidencePreview?>(null) }

    fun refresh() {
        scope.launch { evidences = repository.getEvidences(bitacoraLocalId) }
    }

    fun newTemporaryFile(extension: String): File {
        val directory = File(context.cacheDir, "evidencias_temporales").apply { mkdirs() }
        return File(directory, "${UUID.randomUUID()}.$extension")
    }

    fun fileUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)

    fun openFile(review: PendingEvidencePreview) {
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri(review.file), review.mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    suspend fun savePreview(review: PendingEvidencePreview) {
        val location = try {
            locationProvider.getCurrentLocation()
        } catch (error: Exception) {
            message = "No se pudo guardar la evidencia: GPS no disponible"
            return
        }
        val definitiveDirectory = File(context.filesDir, "evidencias").apply { mkdirs() }
        val definitive = File(definitiveDirectory, review.file.name)
        try {
            withContext(Dispatchers.IO) {
                review.file.copyTo(definitive, overwrite = false)
            }
            repository.saveEvidence(
                BitacoraEvidenceEntity(
                    bitacoraLocalId = bitacoraLocalId,
                    areaId = areaId,
                    clientUuid = UUID.randomUUID().toString(),
                    evidenceType = review.type,
                    textContent = review.text,
                    originalName = definitive.name,
                    localFilePath = definitive.absolutePath,
                    mimeType = review.mime,
                    fileSize = definitive.length(),
                    durationSeconds = review.durationSeconds,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    gpsTimestamp = location.timestamp,
                    locationProvider = location.provider,
                    gpsStatus = GpsStatus.READY,
                    syncStatus = SyncStatus.PENDIENTE_CREAR
                )
            )
            if (shouldDeleteTemporaryEvidence(localMetadataSaved = true)) {
                review.file.delete()
            }
            preview = null
            pendingFile = null
            message = "Evidencia guardada localmente. Pendiente de sincronización"
            refresh()
        } catch (error: Exception) {
            definitive.delete()
            message = "No se pudo guardar la evidencia"
        }
    }

    lateinit var launchCapture: (EvidenceType) -> Unit

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingFile
        if (ok && file != null) {
            preview = PendingEvidencePreview(file, EvidenceType.PHOTO, "image/jpeg")
        } else {
            file?.delete()
            pendingFile = null
            message = "Captura de fotografía cancelada"
        }
    }
    val videoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { ok ->
        val file = pendingFile
        if (ok && file != null) {
            preview = PendingEvidencePreview(
                file,
                EvidenceType.VIDEO,
                "video/mp4",
                mediaDurationSeconds(file)
            )
        } else {
            file?.delete()
            pendingFile = null
            message = "Captura de video cancelada"
        }
    }
    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val source = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && source != null) {
            val file = newTemporaryFile("m4a")
            context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use(input::copyTo)
            }
            pendingFile = file
            preview = PendingEvidencePreview(
                file,
                EvidenceType.AUDIO,
                "audio/mp4",
                mediaDurationSeconds(file)
            )
        } else {
            message = "Grabación de audio cancelada"
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            pendingType?.let { launchCapture(it) }
        } else {
            pendingFile?.delete()
            pendingFile = null
            message = "Permiso rechazado. No se puede capturar la evidencia."
        }
    }

    launchCapture = { type ->
        menu = false
        pendingType = type
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (type == EvidenceType.PHOTO || type == EvidenceType.VIDEO) {
            permissions += Manifest.permission.CAMERA
        }
        if (type == EvidenceType.AUDIO || type == EvidenceType.VIDEO) {
            permissions += Manifest.permission.RECORD_AUDIO
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            when (type) {
                EvidenceType.PHOTO -> {
                    pendingFile = newTemporaryFile("jpg")
                    photoLauncher.launch(fileUri(requireNotNull(pendingFile)))
                }
                EvidenceType.VIDEO -> {
                    pendingFile = newTemporaryFile("mp4")
                    videoLauncher.launch(fileUri(requireNotNull(pendingFile)))
                }
                EvidenceType.AUDIO ->
                    audioLauncher.launch(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION))
                else -> Unit
            }
        }
    }

    LaunchedEffect(bitacoraLocalId) { refresh() }

    Surface(Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Evidencias agregadas", style = MaterialTheme.typography.titleMedium)
            Text("ID local: $bitacoraLocalId · ID servidor: ${remoteId ?: "pendiente"}")
            Button(
                onClick = { menu = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Agregar evidencia") }
            message?.let {
                Text(
                    it,
                    color = if (it.startsWith("No") || it.contains("rechazado")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
            if (evidences.isEmpty()) Text("Esta bitácora aún no tiene evidencias")
            evidences.forEach { evidence ->
                Surface(Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
                    Column(Modifier.padding(8.dp)) {
                        Text("${evidence.evidenceType.label} — ${evidence.syncStatus.uiLabel}")
                        Text(evidence.textContent ?: evidence.originalName.orEmpty())
                        Text(DateFormat.getDateTimeInstance().format(Date(evidence.createdAt)))
                        evidence.lastSyncError?.let {
                            Text(it, color = MaterialTheme.colorScheme.error)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            evidence.localFilePath?.let { path ->
                                OutlinedButton(onClick = {
                                    val file = File(path)
                                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(
                                            fileUri(file),
                                            evidence.mimeType ?: "*/*"
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    })
                                }) {
                                    Text(if (evidence.evidenceType == EvidenceType.AUDIO) {
                                        "Escuchar"
                                    } else {
                                        "Abrir"
                                    })
                                }
                            }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    repository.deleteEvidence(evidence.localId)
                                    refresh()
                                }
                            }) { Text("Eliminar") }
                        }
                    }
                }
            }
        }
    }

    preview?.let { review ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Revisar evidencia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tipo: ${review.type.label}")
                    Text("Tamaño: ${review.file.length()} bytes")
                    review.durationSeconds?.let { Text("Duración: $it segundos") }
                    if (review.type == EvidenceType.PHOTO) {
                        val bitmap = remember(review.file.absolutePath) {
                            BitmapFactory.decodeFile(review.file.absolutePath)
                        }
                        bitmap?.let {
                            Image(
                                bitmap = it.asImageBitmap(),
                                contentDescription = "Vista previa de fotografía",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    if (review.type != EvidenceType.TEXT) {
                        OutlinedButton(
                            onClick = { openFile(review) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (review.type == EvidenceType.PHOTO) {
                                    "Abrir vista completa"
                                } else {
                                    "Reproducir"
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { scope.launch { savePreview(review) } }) {
                    Text("Guardar evidencia")
                }
            },
            dismissButton = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(onClick = {
                        review.file.delete()
                        preview = null
                        pendingFile = null
                        message = "Evidencia descartada"
                    }) { Text("Cancelar") }
                    OutlinedButton(onClick = {
                        review.file.delete()
                        preview = null
                        pendingFile = null
                        launchCapture(review.type)
                    }) { Text("Repetir") }
                }
            }
        )
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            confirmButton = {},
            title = { Text("Agregar evidencia") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        { launchCapture(EvidenceType.PHOTO) },
                        Modifier.fillMaxWidth()
                    ) { Text("Tomar fotografía") }
                    Button(
                        { launchCapture(EvidenceType.VIDEO) },
                        Modifier.fillMaxWidth()
                    ) { Text("Grabar video") }
                    Button(
                        { launchCapture(EvidenceType.AUDIO) },
                        Modifier.fillMaxWidth()
                    ) { Text("Grabar audio") }
                    Button(
                        { menu = false; textDialog = true },
                        Modifier.fillMaxWidth()
                    ) { Text("Escribir observación textual") }
                    OutlinedButton(
                        { menu = false },
                        Modifier.fillMaxWidth()
                    ) { Text("Cancelar") }
                }
            }
        )
    }

    if (textDialog) {
        AlertDialog(
            onDismissRequest = { textDialog = false },
            title = { Text("Observación textual") },
            text = {
                OutlinedTextField(
                    observation,
                    { observation = it },
                    label = { Text("Descripción") }
                )
            },
            confirmButton = {
                Button(
                    enabled = observation.isNotBlank(),
                    onClick = {
                        val value = observation.trim()
                        textDialog = false
                        observation = ""
                        val file = newTemporaryFile("txt").apply { writeText(value) }
                        pendingFile = file
                        preview = PendingEvidencePreview(
                            file,
                            EvidenceType.TEXT,
                            "text/plain",
                            text = value
                        )
                    }
                ) { Text("Revisar") }
            },
            dismissButton = {
                OutlinedButton({ textDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

private fun mediaDurationSeconds(file: File): Int? =
    runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.let { (it / 1000L).toInt() }
        }
    }.getOrNull()

private val EvidenceType.label: String
    get() = when (this) {
        EvidenceType.PHOTO, EvidenceType.ID_PHOTO -> "Fotografía"
        EvidenceType.VIDEO -> "Video"
        EvidenceType.AUDIO -> "Audio"
        EvidenceType.TEXT -> "Observación"
    }

private val SyncStatus.uiLabel: String
    get() = when (this) {
        SyncStatus.PENDIENTE_CREAR -> "Pendiente de sincronización"
        SyncStatus.PENDIENTE_ACTUALIZAR -> "Pendiente de actualización"
        SyncStatus.PENDIENTE_ELIMINAR -> "Pendiente de eliminación"
        SyncStatus.SINCRONIZADO -> "Evidencia sincronizada"
        SyncStatus.ERROR -> "Error de sincronización"
    }
