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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private enum class TechnicalCapture { TEMPLATE, COMPARISON }

@Composable
fun FaceTechnicalScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var status by remember { mutableStateOf("Conceda permiso y coloque una sola cara frente a la cámara") }
    var qualityAccepted by remember { mutableStateOf(false) }
    var template by remember { mutableStateOf<FloatArray?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val embedder = remember { FaceNetEmbeddingGenerator(context.applicationContext) }
    val analyzer = remember {
        FaceTechnicalAnalyzer(
            embedder = embedder,
            onQuality = { quality ->
                mainExecutor.execute {
                    status = quality.message
                    qualityAccepted = quality.accepted
                }
            },
            onFaceCount = { count ->
                mainExecutor.execute {
                    if (count != 1) {
                        qualityAccepted = false
                        status = if (count == 0) "No se detecta una cara" else "Se detectaron varias caras"
                    }
                }
            },
            onEmbedding = { type, embedding ->
                mainExecutor.execute {
                    when (type) {
                        TechnicalCapture.TEMPLATE -> {
                            template = embedding
                            result = null
                            status = "Plantilla técnica capturada en memoria"
                        }
                        TechnicalCapture.COMPARISON -> {
                            val reference = template
                            result = if (reference == null) {
                                "Capture primero la plantilla técnica"
                            } else {
                                val distance = FaceTechnicalMath.l2Distance(reference, embedding)
                                val similarity = FaceTechnicalMath.cosineSimilarity(reference, embedding)
                                "Distancia L2: %.4f · Similitud coseno: %.4f".format(distance, similarity)
                            }
                        }
                    }
                }
            },
            onError = { message ->
                mainExecutor.execute { status = "Error técnico: $message" }
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (!granted) status = "Permiso de cámara denegado"
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            embedder.close()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Prueba técnica de reconocimiento facial", style = MaterialTheme.typography.titleLarge)
        Text("Aislada: no identifica participantes, no guarda fotos y no modifica la bitácora.")

        if (!permissionGranted) {
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Permitir cámara")
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f),
                factory = { viewContext ->
                    PreviewView(viewContext).also { previewView ->
                        val providerFuture = ProcessCameraProvider.getInstance(viewContext)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also { it.setAnalyzer(analyzer.executor, analyzer) }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_FRONT_CAMERA,
                                preview,
                                analysis
                            )
                        }, ContextCompat.getMainExecutor(viewContext))
                    }
                }
            )
        }

        Text(status, color = if (qualityAccepted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = permissionGranted && qualityAccepted,
                onClick = { analyzer.request(TechnicalCapture.TEMPLATE) }
            ) { Text("Capturar plantilla") }
            Button(
                enabled = permissionGranted && qualityAccepted && template != null,
                onClick = { analyzer.request(TechnicalCapture.COMPARISON) }
            ) { Text("Comparar rostro") }
        }
        result?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        if (template != null) {
            OutlinedButton(onClick = {
                template = null
                result = null
                status = "Plantilla eliminada de memoria"
            }) { Text("Limpiar prueba") }
        }
        Text("Modelo: FaceNet · entrada 160×160 RGB · salida 128 float")
    }
}

private class FaceTechnicalAnalyzer(
    private val embedder: FaceNetEmbeddingGenerator,
    private val onQuality: (FaceQuality) -> Unit,
    private val onFaceCount: (Int) -> Unit,
    private val onEmbedding: (TechnicalCapture, FloatArray) -> Unit,
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
    @Volatile private var requested: TechnicalCapture? = null

    fun request(type: TechnicalCapture) {
        requested = type
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
        val image = InputImage.fromMediaImage(mediaImage, rotation)
        detector.process(image)
            .addOnSuccessListener { faces ->
                onFaceCount(faces.size)
                if (faces.size == 1) {
                    val rotatedWidth = if (rotation % 180 == 0) imageProxy.width else imageProxy.height
                    val rotatedHeight = if (rotation % 180 == 0) imageProxy.height else imageProxy.width
                    val face = faces.first()
                    val quality = FaceTechnicalMath.quality(face.boundingBox, rotatedWidth, rotatedHeight)
                    onQuality(quality)
                    val capture = requested
                    if (quality.accepted && capture != null) {
                        requested = null
                        val bitmap = imageProxy.toRotatedBitmap(rotation)
                        val safeRect = Rect(
                            face.boundingBox.left.coerceIn(0, bitmap.width - 1),
                            face.boundingBox.top.coerceIn(0, bitmap.height - 1),
                            face.boundingBox.right.coerceIn(1, bitmap.width),
                            face.boundingBox.bottom.coerceIn(1, bitmap.height)
                        )
                        if (safeRect.width() > 0 && safeRect.height() > 0) {
                            val cropped = Bitmap.createBitmap(
                                bitmap, safeRect.left, safeRect.top, safeRect.width(), safeRect.height()
                            )
                            scope.launch {
                                try {
                                    onEmbedding(capture, embedder.generateEmbedding(cropped))
                                } catch (error: Exception) {
                                    onError(error.message ?: error.javaClass.simpleName)
                                } finally {
                                    cropped.recycle()
                                    bitmap.recycle()
                                }
                            }
                        } else {
                            bitmap.recycle()
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
        detector.close()
        executor.shutdown()
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
        source, 0, 0, source.width, source.height,
        Matrix().apply { postRotate(rotationDegrees.toFloat()) }, true
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
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    var outputIndex = offset
    for (row in 0 until planeHeight) {
        val rowStart = row * rowStride
        for (column in 0 until planeWidth) {
            output[outputIndex] = buffer.get(rowStart + column * pixelStride)
            outputIndex += outputStride
        }
    }
}
