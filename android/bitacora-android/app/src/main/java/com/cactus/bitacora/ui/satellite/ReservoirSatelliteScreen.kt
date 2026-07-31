package com.cactus.bitacora.ui.satellite

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.model.ReservoirSatelliteImageOut
import com.cactus.bitacora.model.ReservoirSatelliteOut

@Composable
fun ReservoirSatelliteScreen(
    repository: BitacoraRepository,
    onBack: () -> Unit
) {
    var reservoirs by remember { mutableStateOf<List<ReservoirSatelliteOut>>(emptyList()) }
    var selectedReservoir by remember { mutableStateOf<ReservoirSatelliteOut?>(null) }
    var images by remember { mutableStateOf<List<ReservoirSatelliteImageOut>>(emptyList()) }
    var selectedImage by remember { mutableStateOf<ReservoirSatelliteImageOut?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var imageLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reservoirMenu by remember { mutableStateOf(false) }
    var imageMenu by remember { mutableStateOf(false) }
    var imageScale by remember(selectedImage?.id_imagen_satelital) {
        mutableStateOf(1f)
    }
    var imageOffset by remember(selectedImage?.id_imagen_satelital) {
        mutableStateOf(Offset.Zero)
    }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            reservoirs = repository.getReservoirs()
            selectedReservoir = reservoirs.firstOrNull()
            if (reservoirs.isEmpty()) error = "No hay embalses activos registrados."
        } catch (exception: Exception) {
            error = "Seguimiento satelital no disponible. Verifique la conexión."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(selectedReservoir?.id_embalse) {
        val reservoir = selectedReservoir ?: return@LaunchedEffect
        images = emptyList()
        selectedImage = null
        bitmap = null
        error = null
        try {
            images = repository.getReservoirImages(reservoir.id_embalse)
            selectedImage = images.firstOrNull()
        } catch (exception: Exception) {
            error = "No fue posible consultar las imágenes del embalse."
        }
    }

    LaunchedEffect(selectedImage?.id_imagen_satelital) {
        val image = selectedImage ?: return@LaunchedEffect
        imageLoading = true
        bitmap = null
        try {
            val bytes = repository.downloadSatelliteImage(image.imagen_url)
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: error("Formato de imagen no compatible")
        } catch (exception: Exception) {
            error = "No fue posible visualizar la imagen satelital."
        } finally {
            imageLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "Seguimiento satelital de embalses",
            style = MaterialTheme.typography.headlineSmall
        )
        if (loading) {
            CircularProgressIndicator()
            Text("Cargando embalses…")
        } else {
            Box(modifier = Modifier.fillMaxWidth().clipToBounds()) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { reservoirMenu = true }
                ) {
                    Text(selectedReservoir?.nombre ?: "Seleccionar embalse")
                }
                DropdownMenu(
                    expanded = reservoirMenu,
                    onDismissRequest = { reservoirMenu = false }
                ) {
                    reservoirs.forEach { reservoir ->
                        DropdownMenuItem(
                            text = { Text(reservoir.nombre) },
                            onClick = {
                                selectedReservoir = reservoir
                                reservoirMenu = false
                            }
                        )
                    }
                }
            }
        }

        selectedReservoir?.let { reservoir ->
            Text("Nombre: ${reservoir.nombre}")
            Text("País: ${reservoir.pais}")
            Text("Departamento: ${reservoir.departamento.orEmpty()}")
            Text("Municipio(s): ${reservoir.municipio.orEmpty()}")
            reservoir.descripcion?.let { Text("Descripción: $it") }
            Text("Fuente geográfica: ${reservoir.fuente_geografica ?: "Pendiente"}")
            Text(
                "Última fecha procesada: " +
                    (reservoir.ultima_fecha_procesada ?: "Sin imágenes procesadas")
            )
            Text("Estado: ${reservoir.estado_seguimiento}")

            if (images.isEmpty() && error == null) {
                Text("No hay imágenes disponibles para este embalse.")
            } else if (images.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { imageMenu = true }
                    ) {
                        Text(selectedImage?.fecha_captura ?: "Seleccionar fecha")
                    }
                    DropdownMenu(
                        expanded = imageMenu,
                        onDismissRequest = { imageMenu = false }
                    ) {
                        images.forEach { image ->
                            DropdownMenuItem(
                                text = { Text(image.fecha_captura) },
                                onClick = {
                                    selectedImage = image
                                    imageMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        selectedImage?.let { image ->
            Text("Fecha de captura: ${image.fecha_captura}")
            Text("Nubosidad: ${image.porcentaje_nubes?.let { "$it %" } ?: "Sin dato"}")
            Text(
                "Píxeles válidos: " +
                    (image.porcentaje_pixeles_validos?.let { "$it %" } ?: "Sin dato")
            )
            Text("Fuente: ${image.fuente}")
            Text("Estado de procesamiento: ${image.estado}")
        }
        if (imageLoading) {
            CircularProgressIndicator()
            Text("Cargando imagen…")
        }
        bitmap?.let {
            Box(modifier = Modifier.fillMaxWidth()) {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = "Imagen satelital del embalse seleccionado",
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(selectedImage?.id_imagen_satelital) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                imageScale = (imageScale * zoom).coerceIn(1f, 5f)
                                imageOffset = if (imageScale == 1f) {
                                    Offset.Zero
                                } else {
                                    imageOffset + pan
                                }
                            }
                        }
                        .graphicsLayer(
                            scaleX = imageScale,
                            scaleY = imageScale,
                            translationX = imageOffset.x,
                            translationY = imageOffset.y
                        ),
                    contentScale = ContentScale.Fit
                )
            }
            Text("Zoom: ${"%.1f".format(imageScale)}×")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = imageScale > 1f,
                    onClick = {
                        imageScale = (imageScale - 0.5f).coerceAtLeast(1f)
                        if (imageScale == 1f) imageOffset = Offset.Zero
                    }
                ) { Text("−") }
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    enabled = imageScale < 5f,
                    onClick = { imageScale = (imageScale + 0.5f).coerceAtMost(5f) }
                ) { Text("+") }
                OutlinedButton(
                    modifier = Modifier.weight(2f),
                    onClick = {
                        imageScale = 1f
                        imageOffset = Offset.Zero
                    }
                ) { Text("Restablecer") }
            }
        }
        if (selectedImage != null) {
            Text(
                "Medición no disponible en esta primera iteración.",
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Text(
            "Medición satelital estimada de la superficie visible del agua. " +
                "No representa directamente el volumen almacenado ni el porcentaje oficial " +
                "de llenado.",
            style = MaterialTheme.typography.bodySmall
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
            Text("Regresar")
        }
    }
}
