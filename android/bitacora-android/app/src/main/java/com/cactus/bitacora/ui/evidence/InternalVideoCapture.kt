package com.cactus.bitacora.ui.evidence

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import java.io.File


@Composable
internal fun InternalVideoCapture(
    outputFile: File,
    onCompleted: (File) -> Unit,
    onCancelled: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember(context) { ContextCompat.getMainExecutor(context) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var preparing by remember { mutableStateOf(true) }
    var cancelled by remember { mutableStateOf(false) }

    LaunchedEffect(previewView) {
        val view = previewView ?: return@LaunchedEffect
        try {
            val provider = ProcessCameraProvider.getInstance(context).get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view.surfaceProvider)
            }
            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(
                        Quality.HD,
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.HD),
                    )
                )
                .build()
            val capture = VideoCapture.withOutput(recorder)
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                capture,
            )
            cameraProvider = provider
            videoCapture = capture
            preparing = false
        } catch (error: Exception) {
            preparing = false
            onError("No fue posible iniciar la cámara de video")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recording?.close()
            cameraProvider?.unbindAll()
        }
    }

    fun startRecording() {
        val capture = videoCapture ?: return
        cancelled = false
        outputFile.delete()
        outputFile.createNewFile()
        var pending = capture.output.prepareRecording(
            context,
            FileOutputOptions.Builder(outputFile).build(),
        )
        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }
        recording = pending.start(executor) { event ->
            if (event is VideoRecordEvent.Finalize) {
                recording = null
                if (cancelled) {
                    outputFile.delete()
                } else if (!event.hasError() && outputFile.isFile && outputFile.length() > 0L) {
                    onCompleted(outputFile)
                } else {
                    outputFile.delete()
                    onError("La grabación de video no produjo un archivo válido")
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Grabar evidencia de video", style = MaterialTheme.typography.titleLarge)
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(420.dp),
            factory = {
                PreviewView(it).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = this
                }
            },
        )
        if (preparing) CircularProgressIndicator()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (recording == null) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !preparing && videoCapture != null,
                    onClick = ::startRecording,
                ) { Text("Grabar") }
            } else {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { recording?.stop() },
                ) { Text("Finalizar") }
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    cancelled = true
                    val active = recording
                    if (active != null) {
                        active.stop()
                    } else {
                        outputFile.delete()
                    }
                    onCancelled()
                },
            ) { Text("Cancelar") }
        }
    }
}
