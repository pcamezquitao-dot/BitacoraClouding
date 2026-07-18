package com.cactus.bitacora.biometric.technical

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cactus.bitacora.biometric.FaceIdentificationTarget
import com.cactus.bitacora.biometric.local.EnrolledParticipant
import com.cactus.bitacora.biometric.local.LocalFaceTemplateRepository
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

enum class FaceFlowMode { ENROLLMENT, IDENTIFICATION }

data class FaceEnrollmentIdentity(
    val participantId: Int,
    val participantCode: String,
    val displayName: String
)

data class FaceRecognitionCandidate(
    val participantId: Int,
    val participantCode: String,
    val displayName: String,
    val similarity: Float
)

fun faceEnrollmentSelectionError(
    participantCode: String,
    participantSelected: Boolean
): String? = when {
    participantCode.isBlank() -> "Escriba o escanee el código del participante"
    !participantSelected -> "Busque y seleccione primero el participante"
    else -> null
}

private enum class FaceVisibleState(val label: String) {
    SEARCHING("Buscando rostro"),
    DETECTED("Rostro detectado"),
    COMPARING("Comparando identidad"),
    RECOGNIZED("Persona reconocida"),
    SUCCESS("Proceso completado"),
    NOT_RECOGNIZED("Rostro no reconocido"),
    ERROR("Error")
}

@Composable
fun FaceTechnicalScreen(
    target: FaceIdentificationTarget?,
    mode: FaceFlowMode,
    enrollmentIdentity: FaceEnrollmentIdentity? = null,
    onConfirmed: (FaceRecognitionCandidate) -> Unit,
    onEnrollmentComplete: () -> Unit,
    onTestRecognition: () -> Unit,
    onUseQr: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val repository = remember { LocalFaceTemplateRepository(context.applicationContext) }
    val cameraHandle = remember { FaceCameraHandle() }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var visibleState by remember { mutableStateOf(FaceVisibleState.SEARCHING) }
    var detail by remember { mutableStateOf("Centre el rostro dentro del recuadro") }
    var qualityAccepted by remember { mutableStateOf(false) }
    var candidate by remember { mutableStateOf<FaceRecognitionCandidate?>(null) }
    var activeTemplateCount by remember { mutableStateOf<Int?>(null) }
    var enrollmentSaved by remember { mutableStateOf(false) }
    var operationInProgress by remember { mutableStateOf(false) }
    var existingEnrollment by remember { mutableStateOf<Boolean?>(null) }
    var replaceExisting by remember { mutableStateOf(false) }
    var showReplaceConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val enrollmentEmbeddings = remember { mutableStateListOf<FloatArray>() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val embedder = remember { FaceNetEmbeddingGenerator(context.applicationContext) }

    fun resetAttempt() {
        candidate = null
        qualityAccepted = false
        operationInProgress = false
        visibleState = FaceVisibleState.SEARCHING
        detail = "Centre el rostro dentro del recuadro"
    }

    val analyzer = remember {
        FaceTechnicalAnalyzer(
            embedder = embedder,
            onQuality = { quality ->
                mainExecutor.execute {
                    qualityAccepted = quality.accepted
                    if (!operationInProgress && candidate == null && !enrollmentSaved) {
                        visibleState = if (quality.accepted) {
                            FaceVisibleState.DETECTED
                        } else {
                            FaceVisibleState.SEARCHING
                        }
                        detail = quality.message
                    }
                }
            },
            onFaceCount = { count ->
                mainExecutor.execute {
                    if (!operationInProgress && count != 1 && candidate == null && !enrollmentSaved) {
                        qualityAccepted = false
                        visibleState = FaceVisibleState.SEARCHING
                        detail = when {
                            count == 0 -> "No se detectó un rostro"
                            else -> "Se detectaron varias caras; debe aparecer solo una"
                        }
                    }
                }
            },
            onEmbedding = { embedding ->
                mainExecutor.execute {
                    qualityAccepted = false
                    visibleState = FaceVisibleState.COMPARING
                    detail = if (mode == FaceFlowMode.ENROLLMENT) {
                        "Procesando captura ${enrollmentEmbeddings.size + 1} de " +
                            LocalFaceTemplateRepository.REQUIRED_ENROLLMENT_CAPTURES
                    } else {
                        "Comparando contra las plantillas locales"
                    }
                }
                scope.launch {
                    try {
                        if (mode == FaceFlowMode.ENROLLMENT) {
                            val identity = requireNotNull(enrollmentIdentity) {
                                "Falta la identidad del participante que se desea registrar"
                            }
                            enrollmentEmbeddings += embedding
                            if (enrollmentEmbeddings.size >=
                                LocalFaceTemplateRepository.REQUIRED_ENROLLMENT_CAPTURES
                            ) {
                                repository.enroll(
                                    identity.participantId,
                                    identity.participantCode,
                                    identity.displayName,
                                    enrollmentEmbeddings.toList(),
                                    embedder.modelVersion,
                                    replaceExisting = replaceExisting
                                )
                                enrollmentEmbeddings.clear()
                                activeTemplateCount = repository.activeCount()
                                enrollmentSaved = true
                                visibleState = FaceVisibleState.SUCCESS
                                detail = "Rostro registrado correctamente para ${identity.displayName}"
                            } else {
                                visibleState = FaceVisibleState.DETECTED
                                detail = "Captura válida. Realice la siguiente captura."
                                qualityAccepted = true
                            }
                        } else {
                            val match = repository.identify(embedding)
                            candidate = match?.toCandidate()
                            visibleState = if (match == null) {
                                FaceVisibleState.NOT_RECOGNIZED
                            } else {
                                FaceVisibleState.RECOGNIZED
                            }
                            detail = if (match == null) {
                                "El rostro no coincide con ningún participante registrado"
                            } else {
                                "Rostro reconocido. Revise la identidad antes de confirmar"
                            }
                        }
                    } catch (error: Exception) {
                        visibleState = FaceVisibleState.ERROR
                        detail = "No fue posible procesar el rostro: " +
                            (error.message ?: error.javaClass.simpleName)
                    } finally {
                        operationInProgress = false
                    }
                }
            },
            onError = { message ->
                mainExecutor.execute {
                    operationInProgress = false
                    visibleState = FaceVisibleState.ERROR
                    detail = "No fue posible procesar la imagen: $message"
                }
            }
        )
    }

    LaunchedEffect(mode, enrollmentIdentity?.participantId) {
        activeTemplateCount = repository.activeCount()
        existingEnrollment = if (mode == FaceFlowMode.ENROLLMENT) {
            enrollmentIdentity?.let { repository.getEnrollment(it.participantId) != null }
        } else {
            null
        }
        if (mode == FaceFlowMode.IDENTIFICATION && activeTemplateCount == 0) {
            visibleState = FaceVisibleState.NOT_RECOGNIZED
            detail = "No hay participantes enrolados. Este participante no tiene rostro registrado"
        }
    }

    val cameraAllowed = mode == FaceFlowMode.IDENTIFICATION ||
        (existingEnrollment == false || replaceExisting)

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (!granted) {
            visibleState = FaceVisibleState.ERROR
            detail = "Permiso de cámara denegado. No es posible iniciar el flujo facial"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraHandle.close()
            analyzer.close()
            embedder.close()
        }
    }

    if (showReplaceConfirmation) {
        AlertDialog(
            onDismissRequest = { showReplaceConfirmation = false },
            title = { Text("Reemplazar enrolamiento") },
            text = {
                Text(
                    "Ya existe un rostro registrado para este participante. " +
                        "¿Desea reemplazarlo?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        replaceExisting = true
                        showReplaceConfirmation = false
                        detail = "Centre el rostro dentro del recuadro"
                    }
                ) { Text("Sí, reemplazar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showReplaceConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Eliminar enrolamiento") },
            text = {
                Text(
                    "¿Confirma que desea eliminar el rostro registrado para este participante?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        val participantId = enrollmentIdentity?.participantId ?: return@Button
                        operationInProgress = true
                        scope.launch {
                            try {
                                repository.deleteEnrollment(participantId)
                                existingEnrollment = false
                                replaceExisting = false
                                visibleState = FaceVisibleState.SUCCESS
                                detail = "Enrolamiento eliminado correctamente"
                            } catch (error: Exception) {
                                visibleState = FaceVisibleState.ERROR
                                detail = "No fue posible eliminar el enrolamiento: " +
                                    (error.message ?: error.javaClass.simpleName)
                            } finally {
                                operationInProgress = false
                            }
                        }
                    }
                ) { Text("Sí, eliminar") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (mode == FaceFlowMode.ENROLLMENT) {
                "Modo: ENROLAMIENTO"
            } else {
                "Modo: RECONOCIMIENTO"
            },
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            if (mode == FaceFlowMode.ENROLLMENT) {
                "Identidad: participante"
            } else {
                "Rol requerido: " +
                    if (target == FaceIdentificationTarget.EMPLEADO) "empleado" else "supervisor"
            },
            style = MaterialTheme.typography.titleMedium
        )
        enrollmentIdentity?.let {
            Text("Nombre: ${it.displayName}")
            Text("Código: ${it.participantCode}")
        }

        if (mode == FaceFlowMode.ENROLLMENT && existingEnrollment == null) {
            Text("Comprobando enrolamiento existente…")
        }
        if (mode == FaceFlowMode.ENROLLMENT && existingEnrollment == true && !replaceExisting) {
            Text(
                "Ya existe un rostro registrado para este participante",
                color = MaterialTheme.colorScheme.error
            )
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showReplaceConfirmation = true }
            ) { Text("Reemplazar enrolamiento") }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !operationInProgress,
                onClick = { showDeleteConfirmation = true }
            ) { Text("Eliminar enrolamiento") }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    cameraHandle.close()
                    onTestRecognition()
                }
            ) { Text("Probar reconocimiento") }
        }

        if (cameraAllowed && !permissionGranted) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Permitir cámara")
            }
        } else if (cameraAllowed) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { viewContext ->
                        PreviewView(viewContext).also { previewView ->
                            val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                            providerFuture.addListener({
                                try {
                                    val provider = providerFuture.get()
                                    val preview = Preview.Builder().build().also {
                                        it.setSurfaceProvider(previewView.surfaceProvider)
                                    }
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(
                                            ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                                        )
                                        .build()
                                        .also { it.setAnalyzer(analyzer.executor, analyzer) }
                                    cameraHandle.bind(
                                        provider = provider,
                                        lifecycleOwner = lifecycleOwner,
                                        preview = preview,
                                        analysis = analysis
                                    )
                                } catch (error: Exception) {
                                    visibleState = FaceVisibleState.ERROR
                                    detail = "No fue posible iniciar la cámara: " +
                                        (error.message ?: error.javaClass.simpleName)
                                }
                            }, ContextCompat.getMainExecutor(viewContext))
                        }
                    }
                )
                Box(
                    modifier = Modifier
                        .size(width = 220.dp, height = 285.dp)
                        .border(
                            width = 3.dp,
                            color = if (qualityAccepted) Color.Green else Color.White,
                            shape = RoundedCornerShape(45.dp)
                        )
                )
            }
        }

        Text(visibleState.label, style = MaterialTheme.typography.titleMedium)
        Text(detail)

        candidate?.let {
            Text("Nombre: ${it.displayName}")
            Text("Código del participante: ${it.participantCode}")
            Text("Nivel de confianza: ${"%.1f".format(it.similarity * 100f)} %")
        }

        if (mode == FaceFlowMode.ENROLLMENT) {
            Text(
                "Capturas: ${enrollmentEmbeddings.size}/" +
                    LocalFaceTemplateRepository.REQUIRED_ENROLLMENT_CAPTURES
            )
        } else if (activeTemplateCount == 0) {
            Text(
                "No hay participantes enrolados en este dispositivo.",
                color = MaterialTheme.colorScheme.error
            )
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = permissionGranted &&
                qualityAccepted &&
                !operationInProgress &&
                candidate == null &&
                !enrollmentSaved &&
                (mode == FaceFlowMode.ENROLLMENT || activeTemplateCount != 0),
            onClick = {
                operationInProgress = true
                qualityAccepted = false
                visibleState = FaceVisibleState.COMPARING
                detail = if (mode == FaceFlowMode.ENROLLMENT) {
                    "Capturando muestras"
                } else {
                    "Capturando rostro para el reconocimiento"
                }
                if (!analyzer.requestCapture()) {
                    operationInProgress = false
                    visibleState = FaceVisibleState.ERROR
                    detail = "Ya hay una captura facial en proceso"
                }
            }
        ) {
            Text(
                if (mode == FaceFlowMode.ENROLLMENT) {
                    "Capturar enrolamiento"
                } else {
                    "Capturar"
                }
            )
        }

        if (candidate != null) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    cameraHandle.close()
                    onConfirmed(requireNotNull(candidate))
                }
            ) { Text("Confirmar") }
        }
        if (enrollmentSaved) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    cameraHandle.close()
                    onEnrollmentComplete()
                }
            ) { Text("Volver al formulario") }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = !operationInProgress,
            onClick = {
                analyzer.cancelPendingCapture()
                enrollmentEmbeddings.clear()
                enrollmentSaved = false
                resetAttempt()
            }
        ) { Text("Intentar nuevamente") }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                cameraHandle.close()
                analyzer.cancelPendingCapture()
                onUseQr()
            }
        ) { Text("Usar QR") }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                cameraHandle.close()
                analyzer.cancelPendingCapture()
                onCancel()
            }
        ) { Text("Cancelar y salir") }
    }
}

private fun EnrolledParticipant.toCandidate() = FaceRecognitionCandidate(
    participantId = participantId,
    participantCode = participantCode,
    displayName = displayName,
    similarity = similarity
)

private class FaceCameraHandle {
    @Volatile private var provider: ProcessCameraProvider? = null
    private val closed = AtomicBoolean(false)

    fun bind(
        provider: ProcessCameraProvider,
        lifecycleOwner: androidx.lifecycle.LifecycleOwner,
        preview: Preview,
        analysis: ImageAnalysis
    ) {
        if (closed.get()) {
            provider.unbindAll()
            return
        }
        this.provider = provider
        provider.unbindAll()
        if (closed.get()) {
            provider.unbindAll()
            this.provider = null
            return
        }
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_FRONT_CAMERA,
            preview,
            analysis
        )
    }

    fun close() {
        closed.set(true)
        provider?.unbindAll()
        provider = null
    }
}

private class FaceTechnicalAnalyzer(
    private val embedder: FaceNetEmbeddingGenerator,
    private val onQuality: (FaceQuality) -> Unit,
    private val onFaceCount: (Int) -> Unit,
    private val onEmbedding: (FloatArray) -> Unit,
    private val onError: (String) -> Unit
) : ImageAnalysis.Analyzer {
    val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicBoolean(false)
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.20f)
            .build()
    )
    private val captureRequested = AtomicBoolean(false)

    fun requestCapture(): Boolean = captureRequested.compareAndSet(false, true)

    fun cancelPendingCapture() {
        captureRequested.set(false)
    }

    override fun analyze(imageProxy: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            busy.set(false)
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        detector.process(InputImage.fromMediaImage(mediaImage, rotation))
            .addOnSuccessListener { faces ->
                onFaceCount(faces.size)
                if (faces.size == 1) {
                    val rotatedWidth = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
                    val rotatedHeight = if (rotation % 180 == 0) imageProxy.height else imageProxy.width
                    val face = faces.first()
                    val quality = FaceTechnicalMath.quality(
                        face.boundingBox,
                        rotatedWidth,
                        rotatedHeight
                    )
                    onQuality(quality)
                    if (quality.accepted && captureRequested.compareAndSet(true, false)) {
                        val bitmap = imageProxy.toRotatedBitmap(rotation)
                        val safeRect = Rect(
                            face.boundingBox.left.coerceIn(0, bitmap.width - 1),
                            face.boundingBox.top.coerceIn(0, bitmap.height - 1),
                            face.boundingBox.right.coerceIn(1, bitmap.width),
                            face.boundingBox.bottom.coerceIn(1, bitmap.height)
                        )
                        if (safeRect.width() > 0 && safeRect.height() > 0) {
                            val cropped = Bitmap.createBitmap(
                                bitmap,
                                safeRect.left,
                                safeRect.top,
                                safeRect.width(),
                                safeRect.height()
                            )
                            scope.launch {
                                try {
                                    onEmbedding(embedder.generateEmbedding(cropped))
                                } catch (error: Exception) {
                                    onError(error.message ?: error.javaClass.simpleName)
                                } finally {
                                    cropped.recycle()
                                    bitmap.recycle()
                                }
                            }
                        } else {
                            bitmap.recycle()
                            onError("El rostro quedó fuera del área válida de captura")
                        }
                    }
                }
            }
            .addOnFailureListener { onError(it.message ?: it.javaClass.simpleName) }
            .addOnCompleteListener {
                imageProxy.close()
                busy.set(false)
            }
    }

    fun close() {
        captureRequested.set(false)
        detector.close()
        scope.cancel()
        executor.shutdownNow()
    }
}

private fun ImageProxy.toRotatedBitmap(rotationDegrees: Int): Bitmap {
    val nv21 = yuv420ToNv21()
    val output = ByteArrayOutputStream()
    YuvImage(nv21, ImageFormat.NV21, width, height, null)
        .compressToJpeg(Rect(0, 0, width, height), 95, output)
    val source = BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())
    if (rotationDegrees == 0) return source
    val rotated = Bitmap.createBitmap(
        source,
        0,
        0,
        source.width,
        source.height,
        Matrix().apply { postRotate(rotationDegrees.toFloat()) },
        true
    )
    source.recycle()
    return rotated
}

private fun ImageProxy.yuv420ToNv21(): ByteArray {
    val output = ByteArray(width * height * 3 / 2)
    copyPlane(planes[0], width, height, output, 0, 1)
    copyPlane(planes[2], width / 2, height / 2, output, width * height, 2)
    copyPlane(planes[1], width / 2, height / 2, output, width * height + 1, 2)
    return output
}

private fun copyPlane(
    plane: ImageProxy.PlaneProxy,
    planeWidth: Int,
    planeHeight: Int,
    output: ByteArray,
    offset: Int,
    outputStride: Int
) {
    val buffer = plane.buffer
    var outputIndex = offset
    for (row in 0 until planeHeight) {
        val rowStart = row * plane.rowStride
        for (column in 0 until planeWidth) {
            output[outputIndex] = buffer.get(rowStart + column * plane.pixelStride)
            outputIndex += outputStride
        }
    }
}
