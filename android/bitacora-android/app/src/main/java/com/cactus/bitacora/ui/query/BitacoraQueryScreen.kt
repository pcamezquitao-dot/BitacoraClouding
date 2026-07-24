package com.cactus.bitacora.ui.query

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.cactus.bitacora.api.NetworkClient
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.data.local.BitacoraEvidenceEntity
import com.cactus.bitacora.data.local.BitacoraLocalEntity
import com.cactus.bitacora.data.local.BitacoraQueryHeader
import com.cactus.bitacora.data.local.EvidenceType
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.util.AppConfig
import java.io.File
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class QueryLevel { LIST, DETAIL, VIEWER }
internal enum class EvidenceViewerKind { PHOTO, VIDEO, AUDIO, TEXT }

internal fun previousQueryLevel(level: QueryLevel): QueryLevel? = when (level) {
    QueryLevel.VIEWER -> QueryLevel.DETAIL
    QueryLevel.DETAIL -> QueryLevel.LIST
    QueryLevel.LIST -> null
}

internal fun evidenceStatusLabel(evidence: BitacoraEvidenceEntity, localFileExists: Boolean): String =
    when {
        evidence.syncStatus == SyncStatus.ERROR -> "Error de sincronización"
        evidence.evidenceType == EvidenceType.TEXT -> evidence.syncStatus.queryLabel
        localFileExists && evidence.syncStatus == SyncStatus.SINCRONIZADO -> "Archivo local"
        localFileExists && evidence.syncStatus != SyncStatus.SINCRONIZADO ->
            "Pendiente de sincronización"
        evidence.remoteId != null -> "Disponible en servidor"
        else -> "Archivo local no disponible"
    }

internal fun canOpenEvidenceLocally(localFilePath: String?, fileExists: Boolean): Boolean =
    !localFilePath.isNullOrBlank() && fileExists

internal fun canShowDeletionActions(allowDelete: Boolean): Boolean = allowDelete

internal fun evidenceRemoteUrl(remoteId: Int): String =
    "${AppConfig.BASE_URL}bitacora-area-evidencias/$remoteId/archivo"

internal fun associatedEvidences(
    bitacoraLocalId: Long,
    bitacoraServerId: Int?,
    evidences: List<BitacoraEvidenceEntity>
): List<BitacoraEvidenceEntity> =
    evidences.filter {
        it.syncStatus != SyncStatus.PENDIENTE_ELIMINAR &&
            (it.bitacoraLocalId == bitacoraLocalId ||
                (bitacoraServerId != null && it.bitacoraServerId == bitacoraServerId))
    }.distinctBy {
        when {
            it.clientUuid.isNotBlank() -> "uuid:${it.clientUuid}"
            it.remoteId != null -> "remote:${it.remoteId}"
            else -> "local:${it.localId}"
        }
    }

internal fun viewerKind(type: EvidenceType): EvidenceViewerKind = when (type) {
    EvidenceType.PHOTO, EvidenceType.ID_PHOTO -> EvidenceViewerKind.PHOTO
    EvidenceType.VIDEO -> EvidenceViewerKind.VIDEO
    EvidenceType.AUDIO -> EvidenceViewerKind.AUDIO
    EvidenceType.TEXT -> EvidenceViewerKind.TEXT
}

private sealed interface QueryLoadState {
    data object Loading : QueryLoadState
    data class Ready(val items: List<BitacoraQueryHeader>) : QueryLoadState
    data class Error(val message: String) : QueryLoadState
}

@Composable
fun BitacoraQueryScreen(repository: BitacoraRepository, allowDelete: Boolean = false) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<QueryLoadState>(QueryLoadState.Loading) }
    var selected by remember { mutableStateOf<BitacoraLocalEntity?>(null) }
    var evidences by remember { mutableStateOf(emptyList<BitacoraEvidenceEntity>()) }
    var selectedEvidence by remember { mutableStateOf<BitacoraEvidenceEntity?>(null) }
    var level by remember { mutableStateOf(QueryLevel.LIST) }
    var detailLoading by remember { mutableStateOf(false) }
    var detailError by remember { mutableStateOf<String?>(null) }
    var pendingBitacoraDeletion by remember { mutableStateOf<BitacoraLocalEntity?>(null) }
    var pendingEvidenceDeletion by remember { mutableStateOf<BitacoraEvidenceEntity?>(null) }
    var deletionInProgress by remember { mutableStateOf(false) }
    var deletionError by remember { mutableStateOf<String?>(null) }

    fun load() {
        state = QueryLoadState.Loading
        scope.launch {
            state = try {
                QueryLoadState.Ready(repository.getLocalBitacoras())
            } catch (error: Exception) {
                QueryLoadState.Error(error.message ?: "No fue posible cargar las bitácoras")
            }
        }
    }

    fun openDetail(bitacora: BitacoraLocalEntity) {
        selected = bitacora
        evidences = emptyList()
        detailError = null
        detailLoading = true
        level = QueryLevel.DETAIL
        scope.launch {
            try {
                evidences = associatedEvidences(
                    bitacora.localId,
                    bitacora.backendId,
                    repository.getEvidences(bitacora.localId)
                )
            } catch (error: Exception) {
                detailError = error.message ?: "No fue posible cargar las evidencias"
            } finally {
                detailLoading = false
            }
        }
    }

    fun goBack(): Boolean {
        return when (level) {
            QueryLevel.VIEWER -> {
                selectedEvidence = null
                level = QueryLevel.DETAIL
                true
            }
            QueryLevel.DETAIL -> {
                selected = null
                evidences = emptyList()
                level = QueryLevel.LIST
                true
            }
            QueryLevel.LIST -> false
        }
    }

    BackHandler(enabled = level != QueryLevel.LIST) { goBack() }
    LaunchedEffect(Unit) { load() }

    when (level) {
        QueryLevel.LIST -> QueryList(state = state, onRetry = ::load, onSelect = ::openDetail)
        QueryLevel.DETAIL -> selected?.let {
            BitacoraQueryDetail(
                bitacora = it,
                evidences = evidences,
                loading = detailLoading,
                error = detailError,
                onRetry = { openDetail(it) },
                onOpen = { evidence ->
                    selectedEvidence = evidence
                    level = QueryLevel.VIEWER
                },
                allowDelete = allowDelete,
                deletionInProgress = deletionInProgress,
                deletionError = deletionError,
                onDeleteBitacora = { pendingBitacoraDeletion = it },
                onDeleteEvidence = { pendingEvidenceDeletion = it },
                onBack = { goBack() }
            )
        }
        QueryLevel.VIEWER -> selectedEvidence?.let {
            EvidenceViewer(evidence = it, onBack = { goBack() })
        }
    }

    pendingBitacoraDeletion?.let { bitacora ->
        DeleteConfirmationDialog(
            title = "Eliminar bitácora",
            message = "Se eliminarán permanentemente la bitácora " +
                "${bitacora.backendId ?: "local #${bitacora.localId}"}, todas sus " +
                "evidencias y los archivos asociados. Esta acción no se puede deshacer.",
            enabled = !deletionInProgress,
            onDismiss = { pendingBitacoraDeletion = null },
            onConfirm = {
                pendingBitacoraDeletion = null
                deletionInProgress = true
                deletionError = null
                scope.launch {
                    try {
                        repository.deleteBitacora(bitacora.localId)
                        selected = null
                        evidences = emptyList()
                        level = QueryLevel.LIST
                        load()
                    } catch (error: Exception) {
                        deletionError = error.message ?: "No fue posible eliminar la bitácora"
                    } finally {
                        deletionInProgress = false
                    }
                }
            }
        )
    }
    pendingEvidenceDeletion?.let { evidence ->
        DeleteConfirmationDialog(
            title = "Eliminar evidencia",
            message = "Se eliminará permanentemente esta evidencia y su archivo asociado. " +
                "Esta acción no se puede deshacer.",
            enabled = !deletionInProgress,
            onDismiss = { pendingEvidenceDeletion = null },
            onConfirm = {
                pendingEvidenceDeletion = null
                deletionInProgress = true
                deletionError = null
                scope.launch {
                    try {
                        repository.deleteEvidence(evidence.localId)
                        selected?.let(::openDetail)
                    } catch (error: Exception) {
                        deletionError = error.message ?: "No fue posible eliminar la evidencia"
                    } finally {
                        deletionInProgress = false
                    }
                }
            }
        )
    }
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    message: String,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(enabled = enabled, onClick = onConfirm) { Text("Sí, eliminar") }
        },
        dismissButton = {
            TextButton(enabled = enabled, onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun QueryList(
    state: QueryLoadState,
    onRetry: () -> Unit,
    onSelect: (BitacoraLocalEntity) -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Consultar bitácoras", style = MaterialTheme.typography.titleLarge)
        when (state) {
            QueryLoadState.Loading -> {
                CircularProgressIndicator()
                Text("Cargando bitácoras")
            }
            is QueryLoadState.Error -> {
                Text(
                    "No fue posible cargar las bitácoras",
                    color = MaterialTheme.colorScheme.error
                )
                Text(state.message)
                Button(onClick = onRetry) { Text("Reintentar") }
            }
            is QueryLoadState.Ready -> {
                if (state.items.isEmpty()) {
                    Text("No hay bitácoras disponibles")
                    OutlinedButton(onClick = onRetry) { Text("Reintentar") }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.items, key = { it.bitacora.localId }) { header ->
                            BitacoraHeader(header, onSelect)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BitacoraHeader(
    header: BitacoraQueryHeader,
    onSelect: (BitacoraLocalEntity) -> Unit
) {
    val bitacora = header.bitacora
    Surface(
        onClick = { onSelect(bitacora) },
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "Bitácora ${bitacora.backendId ?: "local #${bitacora.localId}"}",
                style = MaterialTheme.typography.titleMedium
            )
            Text("Fecha: ${formatDate(bitacora.createdAtMillis)}")
            Text("Hora inicial: ${formatMinute(bitacora.tsInMin)} · final: ${formatMinute(bitacora.tsOutMin)}")
            Text("Empleado: ${bitacora.idEmpleado} · Supervisor: ${bitacora.idSupervisor ?: "sin asignar"}")
            Text("Área: ${bitacora.qrArea ?: "sin información"}")
            Text("Tipo de anotación: ${bitacora.tipoAnotacion ?: "sin información"}")
            Text("Observaciones: ${shortSummary(bitacora.observaciones)}")
            Text("Evidencias: ${header.evidenceCount}")
            Text("Estado: ${bitacora.syncStatus.queryLabel}")
            Text("Tocar para ver detalle y evidencias")
        }
    }
}

@Composable
private fun BitacoraQueryDetail(
    bitacora: BitacoraLocalEntity,
    evidences: List<BitacoraEvidenceEntity>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onOpen: (BitacoraEvidenceEntity) -> Unit,
    allowDelete: Boolean,
    deletionInProgress: Boolean,
    deletionError: String?,
    onDeleteBitacora: (BitacoraLocalEntity) -> Unit,
    onDeleteEvidence: (BitacoraEvidenceEntity) -> Unit,
    onBack: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Volver") }
            Text("Detalle de la bitácora", style = MaterialTheme.typography.titleLarge)
        }
        Text("ID local: ${bitacora.localId} · ID servidor: ${bitacora.backendId ?: "pendiente"}")
        Text("Fecha: ${formatDate(bitacora.createdAtMillis)}")
        Text("Horario: ${formatMinute(bitacora.tsInMin)} - ${formatMinute(bitacora.tsOutMin)}")
        Text("Empleado: ${bitacora.idEmpleado}")
        Text("Supervisor: ${bitacora.idSupervisor ?: "sin asignar"}")
        Text("Área: ${bitacora.qrArea ?: "sin información"}")
        Text("Tipo de anotación: ${bitacora.tipoAnotacion ?: "sin información"}")
        Text("Observaciones: ${bitacora.observaciones ?: "sin observaciones"}")
        Text("Estado: ${bitacora.syncStatus.queryLabel}")
        deletionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (deletionInProgress) {
            CircularProgressIndicator()
            Text("Eliminando")
        }
        if (canShowDeletionActions(allowDelete)) {
            OutlinedButton(
                enabled = !deletionInProgress,
                onClick = { onDeleteBitacora(bitacora) }
            ) { Text("Eliminar bitácora y todas sus evidencias") }
        }
        Text("Evidencias (${evidences.size})", style = MaterialTheme.typography.titleMedium)
        when {
            loading -> {
                CircularProgressIndicator()
                Text("Cargando evidencias")
            }
            error != null -> {
                Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = onRetry) { Text("Reintentar") }
            }
            evidences.isEmpty() -> Text("Esta bitácora no tiene evidencias")
            else -> Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                evidences.forEach { evidence ->
                    EvidenceRow(
                        evidence = evidence,
                        onOpen = onOpen,
                        allowDelete = allowDelete,
                        deletionInProgress = deletionInProgress,
                        onDelete = onDeleteEvidence
                    )
                }
            }
        }
    }
}

@Composable
private fun EvidenceRow(
    evidence: BitacoraEvidenceEntity,
    onOpen: (BitacoraEvidenceEntity) -> Unit,
    allowDelete: Boolean,
    deletionInProgress: Boolean,
    onDelete: (BitacoraEvidenceEntity) -> Unit
) {
    val fileExists = evidence.localFilePath?.let { File(it).isFile } == true
    Surface(Modifier.fillMaxWidth(), tonalElevation = 1.dp) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("${evidence.typeLabel} ${evidence.typeSymbol}", style = MaterialTheme.typography.titleMedium)
            Text("Fecha: ${formatDate(evidence.createdAt)}")
            evidence.fileSize?.let { Text("Tamaño: ${formatBytes(it)}") }
            Text("Estado: ${evidenceStatusLabel(evidence, fileExists)}")
            Text(evidence.textContent ?: evidence.originalName ?: "Sin descripción")
            Button(onClick = { onOpen(evidence) }) { Text("Abrir") }
            if (canShowDeletionActions(allowDelete)) {
                OutlinedButton(
                    enabled = !deletionInProgress,
                    onClick = { onDelete(evidence) }
                ) { Text("Eliminar evidencia") }
            }
        }
    }
}

@Composable
private fun EvidenceViewer(evidence: BitacoraEvidenceEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    val file = evidence.localFilePath?.let(::File)
    val fileExists = file?.isFile == true
    var error by remember(evidence.localId) { mutableStateOf<String?>(null) }
    val localUri = remember(evidence.localId, fileExists) {
        if (fileExists) {
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.files",
                    requireNotNull(file)
                )
            }.onFailure { error = "No fue posible abrir la URI segura" }.getOrNull()
        } else {
            null
        }
    }
    val remoteUrl = remember(evidence.remoteId) {
        evidence.remoteId?.let(::evidenceRemoteUrl)
    }
    val playbackUri = localUri ?: remoteUrl?.let(Uri::parse)

    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onBack) { Text("Volver") }
        Text(evidence.typeLabel, style = MaterialTheme.typography.titleLarge)
        Text("Fecha: ${formatDate(evidence.createdAt)}")
        Text("Estado: ${evidence.syncStatus.queryLabel}")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (viewerKind(evidence.evidenceType) == EvidenceViewerKind.TEXT) {
            Text(
                evidence.textContent ?: "Sin contenido",
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            )
            return@Column
        }
        if (playbackUri == null) {
            Text(
                "El archivo local no existe y no hay una copia disponible en el servidor",
                color = MaterialTheme.colorScheme.error
            )
            return@Column
        }
        when (viewerKind(evidence.evidenceType)) {
            EvidenceViewerKind.PHOTO -> if (fileExists) {
                PhotoViewer(file = requireNotNull(file), onError = { error = it })
            } else {
                RemotePhotoViewer(url = requireNotNull(remoteUrl), onError = { error = it })
            }
            EvidenceViewerKind.VIDEO ->
                VideoViewer(uri = playbackUri, onError = { error = it })
            EvidenceViewerKind.AUDIO -> AudioViewer(playbackUri, evidence.durationSeconds) {
                error = it
            }
            EvidenceViewerKind.TEXT -> Unit
        }
    }
}

@Composable
private fun RemotePhotoViewer(url: String, onError: (String) -> Unit) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    LaunchedEffect(url) {
        try {
            bitmap = withContext(Dispatchers.IO) {
                val request = okhttp3.Request.Builder().url(url).build()
                NetworkClient.okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val bytes = response.body?.bytes() ?: error("Respuesta vacía")
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        ?: error("Formato de imagen inválido")
                }
            }
        } catch (error: Exception) {
            onError(
                "No fue posible descargar la fotografía: " +
                    (error.message ?: error.javaClass.simpleName)
            )
        } finally {
            loading = false
        }
    }
    if (loading) {
        CircularProgressIndicator()
        Text("Descargando fotografía")
    } else {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Fotografía remota",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PhotoViewer(file: File, onError: (String) -> Unit) {
    val bitmap = remember(file.absolutePath) { BitmapFactory.decodeFile(file.absolutePath) }
    if (bitmap == null) {
        LaunchedEffect(file.absolutePath) { onError("La fotografía tiene un formato inválido") }
        return
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformable = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        offsetX += pan.x
        offsetY += pan.y
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Fotografía completa",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                .transformable(transformable)
        )
    }
}

@Composable
private fun VideoViewer(uri: Uri, onError: (String) -> Unit) {
    val context = LocalContext.current
    var loading by remember(uri) { mutableStateOf(true) }
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    loading = playbackState == Player.STATE_BUFFERING ||
                        playbackState == Player.STATE_IDLE
                }

                override fun onPlayerError(error: PlaybackException) {
                    loading = false
                    onError("No fue posible reproducir el video: ${error.errorCodeName}")
                }
            })
            prepare()
            playWhenReady = true
        }
    }
    if (loading) {
        CircularProgressIndicator()
        Text("Cargando video")
    }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).also { view ->
                view.player = player
                view.useController = true
            }
        },
        update = { it.player = player }
    )
    DisposableEffect(uri) {
        onDispose {
            player.release()
        }
    }
}

@Composable
private fun AudioViewer(uri: Uri, durationSeconds: Int?, onError: (String) -> Unit) {
    val context = LocalContext.current
    val player = remember(uri) {
        runCatching { MediaPlayer.create(context, uri) }
            .onFailure { onError("No fue posible preparar el audio") }
            .getOrNull()
    }
    Text("Duración: ${durationSeconds?.let { "$it segundos" } ?: "no disponible"}")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = player != null, onClick = {
            runCatching { player?.start() }.onFailure { onError("No fue posible reproducir el audio") }
        }) { Text("Reproducir") }
        OutlinedButton(enabled = player != null, onClick = {
            runCatching { player?.pause() }.onFailure { onError("No fue posible pausar el audio") }
        }) { Text("Pausar") }
        OutlinedButton(enabled = player != null, onClick = {
            runCatching {
                player?.pause()
                player?.seekTo(0)
            }.onFailure { onError("No fue posible detener el audio") }
        }) { Text("Detener") }
    }
    DisposableEffect(uri) { onDispose { player?.release() } }
}

private fun formatDate(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))

private fun formatMinute(value: Int?): String {
    if (value == null) return "sin información"
    return "%02d:%02d".format((value / 60) % 24, value % 60)
}

private fun shortSummary(value: String?): String {
    val normalized = value?.trim().orEmpty()
    return when {
        normalized.isBlank() -> "sin observaciones"
        normalized.length <= 80 -> normalized
        else -> normalized.take(77) + "..."
    }
}

private fun formatBytes(value: Long): String =
    if (value < 1024L) "$value bytes" else "%.1f KB".format(value / 1024.0)

private val BitacoraEvidenceEntity.typeLabel: String
    get() = when (evidenceType) {
        EvidenceType.PHOTO, EvidenceType.ID_PHOTO -> "Fotografía"
        EvidenceType.VIDEO -> "Video"
        EvidenceType.AUDIO -> "Audio"
        EvidenceType.TEXT -> "Texto"
    }

private val BitacoraEvidenceEntity.typeSymbol: String
    get() = when (evidenceType) {
        EvidenceType.PHOTO, EvidenceType.ID_PHOTO -> "▣"
        EvidenceType.VIDEO -> "▶"
        EvidenceType.AUDIO -> "♪"
        EvidenceType.TEXT -> "≡"
    }

private val SyncStatus.queryLabel: String
    get() = when (this) {
        SyncStatus.PENDIENTE_CREAR -> "Pendiente de sincronización"
        SyncStatus.PENDIENTE_ACTUALIZAR -> "Pendiente de actualización"
        SyncStatus.PENDIENTE_ELIMINAR -> "Pendiente de eliminación"
        SyncStatus.SINCRONIZADO -> "Sincronizada"
        SyncStatus.ERROR -> "Error"
    }

internal fun safeExternalOpen(intentStarter: (Intent) -> Unit, uri: Uri, mime: String): Boolean =
    try {
        intentStarter(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: IllegalArgumentException) {
        false
    }
