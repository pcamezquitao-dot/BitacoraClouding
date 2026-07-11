package com.cactus.bitacora

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.cactus.bitacora.data.ApiConfig
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.data.CreateBitacoraResult
import com.cactus.bitacora.data.SyncRunResult
import com.cactus.bitacora.data.SyncSummary
import com.cactus.bitacora.data.models.AreaOut
import com.cactus.bitacora.data.models.BitacoraDiariaCreate
import com.cactus.bitacora.data.models.BitacoraDiariaOut
import com.cactus.bitacora.model.EmpleadoAreaActivaOut
import com.cactus.bitacora.model.ParticipanteOut
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG_QR_AREA = "QrArea"
private const val QR_AREA_TYPE = "AREA_ADMINISTRATIVA"
private const val INVALID_QR_AREA_MESSAGE =
    "QR de área inválido. Formato esperado: AREA_ADMINISTRATIVA|ID|NOMBRE"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    BitacoraApp()
                }
            }
        }
    }
}

private enum class AppScreen {
    Health,
    CreateDailyLog,
    QueryDailyLog,
    Sync,
    QrArea
}

private sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Loading : ConnectionState
    data class Available(val status: String) : ConnectionState
    data class Unavailable(val message: String) : ConnectionState
}

private sealed interface CreateBitacoraState {
    data object Idle : CreateBitacoraState
    data object Loading : CreateBitacoraState
    data class Success(val idBitacora: Int) : CreateBitacoraState
    data class Pending(val localId: Long, val message: String) : CreateBitacoraState
    data class Error(val message: String) : CreateBitacoraState
}

private sealed interface QueryBitacoraState {
    data object Idle : QueryBitacoraState
    data object Loading : QueryBitacoraState
    data class Success(val bitacora: BitacoraDiariaOut) : QueryBitacoraState
    data class Error(val message: String) : QueryBitacoraState
}

private sealed interface SyncState {
    data object Idle : SyncState
    data object Loading : SyncState
    data class Ready(val summary: SyncSummary, val lastRun: SyncRunResult? = null) : SyncState
    data class Error(val message: String) : SyncState
}

private sealed interface QrAreaState {
    data object Idle : QrAreaState
    data object Loading : QrAreaState
    data class Success(val area: AreaOut) : QrAreaState
    data class Error(val message: String) : QrAreaState
}

private data class ParticipanteValidado(
    val participante: ParticipanteOut,
    val asignacion: EmpleadoAreaActivaOut
)

private enum class DailyLogQrTarget { EMPLEADO, SUPERVISOR, AREA }

@Composable
fun BitacoraApp() {
    val context = LocalContext.current.applicationContext
    val repository = remember { BitacoraRepository(context) }
    var currentScreen by remember { mutableStateOf(AppScreen.Health) }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "BitacoraClouding",
                style = MaterialTheme.typography.headlineSmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = currentScreen != AppScreen.Health,
                    onClick = { currentScreen = AppScreen.Health }
                ) {
                    Text("Health")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    enabled = currentScreen != AppScreen.CreateDailyLog,
                    onClick = { currentScreen = AppScreen.CreateDailyLog }
                ) {
                    Text("Crear")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    enabled = currentScreen != AppScreen.QueryDailyLog,
                    onClick = { currentScreen = AppScreen.QueryDailyLog }
                ) {
                    Text("Consultar")
                }

                Button(
                    modifier = Modifier.weight(1f),
                    enabled = currentScreen != AppScreen.Sync,
                    onClick = { currentScreen = AppScreen.Sync }
                ) {
                    Text("Sync")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = currentScreen != AppScreen.QrArea,
                    onClick = { currentScreen = AppScreen.QrArea }
                ) {
                    Text("QR Área")
                }
            }

            when (currentScreen) {
                AppScreen.Health -> BackendStatusScreen(repository)
                AppScreen.CreateDailyLog -> CrearBitacoraDiariaScreen(repository)
                AppScreen.QueryDailyLog -> ConsultarBitacoraScreen(repository)
                AppScreen.Sync -> SyncScreen(repository)
                AppScreen.QrArea -> QrAreaScreen()
            }
        }
    }
}

@Composable
fun BackendStatusScreen(repository: BitacoraRepository) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) }

    fun checkConnection() {
        state = ConnectionState.Loading
        scope.launch {
            state = try {
                val health = repository.checkHealth()
                ConnectionState.Available(health.status)
            } catch (e: Exception) {
                ConnectionState.Unavailable(e.message ?: "No fue posible conectar con el backend")
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Backend oficial",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            text = ApiConfig.BASE_URL,
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(4.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is ConnectionState.Loading,
            onClick = { checkConnection() }
        ) {
            Text("Probar conexion")
        }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is ConnectionState.Loading,
            onClick = { state = ConnectionState.Idle }
        ) {
            Text("Limpiar estado")
        }

        when (val currentState = state) {
            ConnectionState.Idle -> Text("Estado: pendiente")
            ConnectionState.Loading -> {
                CircularProgressIndicator()
                Text("Estado: conectando")
            }
            is ConnectionState.Available -> {
                Text(
                    text = "Backend disponible",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Health status: ${currentState.status}")
            }
            is ConnectionState.Unavailable -> {
                Text(
                    text = "Backend no disponible",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(currentState.message)
            }
        }
    }
}

@Composable
fun ConsultarBitacoraScreen(repository: BitacoraRepository) {
    val scope = rememberCoroutineScope()
    var idBitacora by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<QueryBitacoraState>(QueryBitacoraState.Idle) }

    fun queryDailyLog() {
        val id = idBitacora.toIntOrNull()

        if (id == null) {
            state = QueryBitacoraState.Error("id_bitacora debe ser numerico")
            return
        }

        state = QueryBitacoraState.Loading
        scope.launch {
            state = try {
                val response = repository.getBitacoraDiaria(id)
                QueryBitacoraState.Success(response)
            } catch (e: Exception) {
                QueryBitacoraState.Error(e.message ?: "No fue posible consultar la bitacora")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Consultar Bitacora",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = idBitacora,
            onValueChange = { idBitacora = it },
            label = { Text("id_bitacora") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is QueryBitacoraState.Loading,
            onClick = { queryDailyLog() }
        ) {
            Text("Consultar bitacora")
        }

        when (val currentState = state) {
            QueryBitacoraState.Idle -> Text("Estado: pendiente")
            QueryBitacoraState.Loading -> {
                CircularProgressIndicator()
                Text("Estado: consultando bitacora")
            }
            is QueryBitacoraState.Success -> {
                Text(
                    text = "Bitacora encontrada",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                BitacoraDetail(currentState.bitacora)
            }
            is QueryBitacoraState.Error -> {
                Text(
                    text = "Error al consultar bitacora",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(currentState.message)
            }
        }
    }
}

@Composable
fun BitacoraDetail(bitacora: BitacoraDiariaOut) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("id_bitacora: ${bitacora.id_bitacora}")
        Text("id_empleado: ${bitacora.id_empleado}")
        Text("id_supervisor: ${bitacora.id_supervisor ?: "null"}")
        Text("ts_in_min: ${bitacora.ts_in_min}")
        Text("ts_out_min: ${bitacora.ts_out_min ?: "null"}")
        Text("tipo_anotacion: ${bitacora.tipo_anotacion ?: "null"}")
        Text("observaciones: ${bitacora.observaciones ?: "null"}")
    }
}

@Composable
fun CrearBitacoraDiariaScreen(repository: BitacoraRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var qrEmpleado by remember { mutableStateOf("") }
    var qrSupervisor by remember { mutableStateOf("") }
    var qrArea by remember { mutableStateOf("") }
    var empleado by remember { mutableStateOf<ParticipanteValidado?>(null) }
    var supervisor by remember { mutableStateOf<ParticipanteValidado?>(null) }
    var area by remember { mutableStateOf<AreaOut?>(null) }
    var empleadoError by remember { mutableStateOf<String?>(null) }
    var supervisorError by remember { mutableStateOf<String?>(null) }
    var areaError by remember { mutableStateOf<String?>(null) }
    var validatingTarget by remember { mutableStateOf<DailyLogQrTarget?>(null) }
    var scanningTarget by remember { mutableStateOf<DailyLogQrTarget?>(null) }
    var pendingCameraTarget by remember { mutableStateOf<DailyLogQrTarget?>(null) }
    var tipoAnotacion by remember { mutableStateOf("") }
    var observaciones by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<CreateBitacoraState>(CreateBitacoraState.Idle) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) scanningTarget = pendingCameraTarget
        else {
            when (pendingCameraTarget) {
                DailyLogQrTarget.EMPLEADO -> empleadoError = "Se necesita permiso de cámara para leer el QR del empleado."
                DailyLogQrTarget.SUPERVISOR -> supervisorError = "Se necesita permiso de cámara para leer el QR del supervisor."
                DailyLogQrTarget.AREA -> areaError = "Se necesita permiso de cámara para leer el QR del área."
                null -> Unit
            }
        }
        pendingCameraTarget = null
    }

    fun validateEmpleado(rawQr: String) {
        val qr = rawQr.trim()
        empleado = null
        empleadoError = null
        area = null
        areaError = null
        if (qr.isBlank()) {
            empleadoError = "Debe ingresar o escanear el QR del empleado"
            return
        }
        validatingTarget = DailyLogQrTarget.EMPLEADO
        scope.launch {
            try {
                val participante = repository.getParticipanteByQr(qr)
                val asignacion = try {
                    repository.getAsignacionActiva(participante.id_participante)
                } catch (_: Exception) {
                    throw IllegalStateException("El empleado no tiene una asignación de área activa")
                }
                empleado = ParticipanteValidado(participante, asignacion)
            } catch (e: Exception) {
                empleadoError = e.message ?: "No fue posible validar el empleado"
            } finally {
                validatingTarget = null
            }
        }
    }

    fun validateSupervisor(rawQr: String) {
        val qr = rawQr.trim()
        supervisor = null
        supervisorError = null
        if (qr.isBlank()) {
            supervisorError = "Debe ingresar o escanear el QR del supervisor"
            return
        }
        validatingTarget = DailyLogQrTarget.SUPERVISOR
        scope.launch {
            try {
                val participante = repository.getParticipanteByQr(qr)
                val asignacion = try {
                    repository.getAsignacionActiva(participante.id_participante)
                } catch (_: Exception) {
                    throw IllegalStateException("El supervisor no tiene una asignación de área activa")
                }
                supervisor = ParticipanteValidado(participante, asignacion)
            } catch (e: Exception) {
                supervisorError = e.message ?: "No fue posible validar el supervisor"
            } finally {
                validatingTarget = null
            }
        }
    }

    fun validateArea(rawQr: String) {
        val qr = rawQr.trim()
        area = null
        areaError = null
        if (qr.isBlank()) {
            areaError = "Debe ingresar o escanear el QR del área"
            return
        }
        val empleadoActual = empleado
        if (empleadoActual == null) {
            areaError = "Primero debe validar un empleado con asignación activa"
            return
        }
        validatingTarget = DailyLogQrTarget.AREA
        scope.launch {
            try {
                val areaConsultada = repository.getAreaByQr(qr)
                if (areaConsultada.id_area != empleadoActual.asignacion.id_area) {
                    throw IllegalStateException(
                        "El área seleccionada no coincide con la asignación activa del empleado"
                    )
                }
                area = areaConsultada
            } catch (e: Exception) {
                areaError = e.message ?: "No fue posible validar el área"
            } finally {
                validatingTarget = null
            }
        }
    }

    fun startScan(target: DailyLogQrTarget) {
        pendingCameraTarget = target
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            scanningTarget = target
            pendingCameraTarget = null
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val canCreate = empleado != null && supervisor != null && area != null &&
        area?.id_area == empleado?.asignacion?.id_area && validatingTarget == null && scanningTarget == null

    fun createDailyLog() {
        val empleadoValidado = empleado
        val supervisorValidado = supervisor
        val areaValidada = area
        val tipo = tipoAnotacion.toIntOrNull()

        if (empleadoValidado == null || supervisorValidado == null || areaValidada == null || !canCreate) {
            state = CreateBitacoraState.Error("Debe validar empleado, supervisor y área antes de crear la bitácora")
            return
        }

        if (tipoAnotacion.isNotBlank() && tipo == null) {
            state = CreateBitacoraState.Error("tipo_anotacion debe ser numerico")
            return
        }

        state = CreateBitacoraState.Loading
        scope.launch {
            state = try {
                when (val result = repository.crearBitacoraDiaria(
                    BitacoraDiariaCreate(
                        id_empleado = empleadoValidado.participante.id_participante,
                        id_supervisor = supervisorValidado.participante.id_participante,
                        tipo_anotacion = tipo,
                        observaciones = observaciones.ifBlank { null },
                        client_uuid = UUID.randomUUID().toString(),
                        qr_area = qrArea.trim()
                    )
                )) {
                    is CreateBitacoraResult.Sincronizada ->
                        CreateBitacoraState.Success(result.bitacora.id_bitacora)
                    is CreateBitacoraResult.Pendiente ->
                        CreateBitacoraState.Pending(result.localId, result.message)
                }
            } catch (e: Exception) {
                CreateBitacoraState.Error(e.message ?: "No fue posible crear la bitacora diaria")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Crear Bitacora Diaria",
            style = MaterialTheme.typography.titleMedium
        )

        QrValidationSection(
            title = "Empleado",
            qr = qrEmpleado,
            onQrChange = { qrEmpleado = it; empleado = null; empleadoError = null; area = null },
            onScan = { startScan(DailyLogQrTarget.EMPLEADO) },
            onValidate = { validateEmpleado(qrEmpleado) },
            loading = validatingTarget == DailyLogQrTarget.EMPLEADO,
            error = empleadoError
        )
        empleado?.let {
            Text("Nombre: ${it.participante.nombreCompleto()}")
            Text("Identificación: ${it.participante.identificacion_participante.orEmpty()}")
            Text("Área asignada: ${it.asignacion.area_descripcion ?: it.asignacion.id_area}")
        }

        QrValidationSection(
            title = "Supervisor",
            qr = qrSupervisor,
            onQrChange = { qrSupervisor = it; supervisor = null; supervisorError = null },
            onScan = { startScan(DailyLogQrTarget.SUPERVISOR) },
            onValidate = { validateSupervisor(qrSupervisor) },
            loading = validatingTarget == DailyLogQrTarget.SUPERVISOR,
            error = supervisorError
        )
        supervisor?.let {
            Text("Nombre: ${it.participante.nombreCompleto()}")
            Text("Identificación: ${it.participante.identificacion_participante.orEmpty()}")
        }

        QrValidationSection(
            title = "Área",
            qr = qrArea,
            onQrChange = { qrArea = it; area = null; areaError = null },
            onScan = { startScan(DailyLogQrTarget.AREA) },
            onValidate = { validateArea(qrArea) },
            loading = validatingTarget == DailyLogQrTarget.AREA,
            error = areaError
        )
        area?.let { Text("Área seleccionada: ${it.descripcion}") }

        scanningTarget?.let { target ->
            QrAreaScanner(
                prompt = "Apunte la cámara al QR de ${target.name.lowercase()}",
                errorMessage = "No fue posible leer el QR de ${target.name.lowercase()}.",
                onQrScanned = { rawQr ->
                    scanningTarget = null
                    when (target) {
                        DailyLogQrTarget.EMPLEADO -> { qrEmpleado = rawQr; validateEmpleado(rawQr) }
                        DailyLogQrTarget.SUPERVISOR -> { qrSupervisor = rawQr; validateSupervisor(rawQr) }
                        DailyLogQrTarget.AREA -> { qrArea = rawQr; validateArea(rawQr) }
                    }
                },
                onError = { message ->
                    scanningTarget = null
                    when (target) {
                        DailyLogQrTarget.EMPLEADO -> empleadoError = message
                        DailyLogQrTarget.SUPERVISOR -> supervisorError = message
                        DailyLogQrTarget.AREA -> areaError = message
                    }
                }
            )
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = tipoAnotacion,
            onValueChange = { tipoAnotacion = it },
            label = { Text("tipo_anotacion") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = observaciones,
            onValueChange = { observaciones = it },
            label = { Text("observaciones") },
            minLines = 3
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = canCreate && state !is CreateBitacoraState.Loading,
            onClick = { createDailyLog() }
        ) {
            Text("Crear bitacora diaria")
        }

        when (val currentState = state) {
            CreateBitacoraState.Idle -> Text("Estado: pendiente")
            CreateBitacoraState.Loading -> {
                CircularProgressIndicator()
                Text("Estado: creando bitacora diaria")
            }
            is CreateBitacoraState.Success -> {
                Text(
                    text = "Bitacora diaria creada",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text("id_bitacora: ${currentState.idBitacora}")
            }
            is CreateBitacoraState.Pending -> {
                Text(
                    text = "Bitacora guardada localmente",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(currentState.message)
                Text("local_id: ${currentState.localId}")
                Text("estado: PENDIENTE")
            }
            is CreateBitacoraState.Error -> {
                Text(
                    text = "Error al crear bitacora diaria",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(currentState.message)
            }
        }
    }
}

@Composable
fun SyncScreen(repository: BitacoraRepository) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<SyncState>(SyncState.Idle) }

    fun loadSummary(lastRun: SyncRunResult? = null) {
        state = SyncState.Loading
        scope.launch {
            state = try {
                SyncState.Ready(repository.getSyncSummary(), lastRun)
            } catch (e: Exception) {
                SyncState.Error(e.message ?: "No fue posible leer el estado local")
            }
        }
    }

    fun syncNow() {
        state = SyncState.Loading
        scope.launch {
            state = try {
                val result = repository.sincronizarPendientes()
                SyncState.Ready(repository.getSyncSummary(), result)
            } catch (e: Exception) {
                SyncState.Error(e.message ?: "No fue posible sincronizar")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Sincronizacion local",
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is SyncState.Loading,
            onClick = { loadSummary() }
        ) {
            Text("Actualizar conteos")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is SyncState.Loading,
            onClick = { syncNow() }
        ) {
            Text("Sincronizar ahora")
        }

        when (val currentState = state) {
            SyncState.Idle -> Text("Estado: pendiente")
            SyncState.Loading -> {
                CircularProgressIndicator()
                Text("Estado: trabajando")
            }
            is SyncState.Ready -> {
                Text("total pendientes: ${currentState.summary.pendientes}")
                Text("total sincronizados: ${currentState.summary.sincronizados}")
                Text("total con error: ${currentState.summary.errores}")

                currentState.lastRun?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Ultima sincronizacion")
                    Text("revisados: ${it.revisados}")
                    Text("sincronizados: ${it.sincronizados}")
                    Text("errores: ${it.errores}")
                }
            }
            is SyncState.Error -> {
                Text(
                    text = "Error de sincronizacion",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(currentState.message)
            }
        }
    }
}

@Composable
fun QrAreaScreen() {
    val context = LocalContext.current
    var qrCode by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<QrAreaState>(QrAreaState.Idle) }
    var isScanning by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG_QR_AREA, "Permiso camara concedido")
            isScanning = true
        } else {
            Log.w(TAG_QR_AREA, "Permiso camara rechazado")
            state = QrAreaState.Error("Se necesita permiso de cámara para leer el QR del área.")
        }
    }

    fun applyQrText(rawQr: String) {
        Log.i(TAG_QR_AREA, "QR leido: $rawQr")
        qrCode = rawQr
        val area = parseAreaQr(rawQr)
        if (area == null) {
            Log.w(TAG_QR_AREA, "Error de formato QR Area: $rawQr")
            state = QrAreaState.Error(INVALID_QR_AREA_MESSAGE)
            return
        }

        Log.i(TAG_QR_AREA, "Tipo QR detectado: $QR_AREA_TYPE")
        Log.i(TAG_QR_AREA, "ID area: ${area.id_area}")
        Log.i(TAG_QR_AREA, "Nombre area: ${area.descripcion}")
        state = QrAreaState.Success(area)
    }

    fun validateManualQr() {
        val qr = qrCode.trim()

        if (qr.isBlank()) {
            state = QrAreaState.Error("Debe escribir o pegar un codigo QR")
            return
        }

        applyQrText(qr)
    }

    fun startQrScan() {
        Log.i(TAG_QR_AREA, "Boton escanear presionado")
        when (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)) {
            PackageManager.PERMISSION_GRANTED -> {
                Log.i(TAG_QR_AREA, "Permiso camara concedido")
                isScanning = true
            }
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "QR Área",
            style = MaterialTheme.typography.titleMedium
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !isScanning,
            onClick = { startQrScan() }
        ) {
            Text("Escanear QR Área")
        }

        if (isScanning) {
            QrAreaScanner(
                onQrScanned = { rawQr ->
                    isScanning = false
                    applyQrText(rawQr)
                },
                onError = { message ->
                    isScanning = false
                    state = QrAreaState.Error(message)
                }
            )
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = qrCode,
            onValueChange = { qrCode = it },
            label = { Text("codigo QR manual para pruebas") },
            minLines = 2
        )

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is QrAreaState.Loading,
            onClick = { validateManualQr() }
        ) {
            Text("Validar QR manual")
        }

        when (val currentState = state) {
            QrAreaState.Idle -> Text("Estado: pendiente")
            QrAreaState.Loading -> {
                CircularProgressIndicator()
                Text("Estado: consultando area")
            }
            is QrAreaState.Success -> {
                Text(
                    text = "Area encontrada",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Área seleccionada: ${currentState.area.descripcion}")
                Text("id_area: ${currentState.area.id_area}")
                Text("descripcion: ${currentState.area.descripcion}")
            }
            is QrAreaState.Error -> {
                Text(
                    text = "Error al consultar area",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(currentState.message)
            }
        }
    }
}

private fun parseAreaQr(rawQr: String): AreaOut? {
    val parts = rawQr.trim().split("|")
    if (parts.size != 3) return null

    val type = parts[0].trim()
    val idArea = parts[1].trim().toIntOrNull()
    val areaName = parts[2].trim()

    if (type != QR_AREA_TYPE || idArea == null || areaName.isBlank()) return null

    return AreaOut(
        id_area = idArea,
        descripcion = areaName
    )
}

@Composable
private fun QrValidationSection(
    title: String,
    qr: String,
    onQrChange: (String) -> Unit,
    onScan: () -> Unit,
    onValidate: () -> Unit,
    loading: Boolean,
    error: String?
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = qr,
        onValueChange = onQrChange,
        label = { Text("QR $title (manual)") },
        singleLine = true
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(modifier = Modifier.weight(1f), enabled = !loading, onClick = onScan) {
            Text("Escanear")
        }
        OutlinedButton(modifier = Modifier.weight(1f), enabled = !loading, onClick = onValidate) {
            Text("Validar manual")
        }
    }
    if (loading) CircularProgressIndicator()
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

private fun ParticipanteOut.nombreCompleto(): String =
    listOfNotNull(nombre?.takeIf { it.isNotBlank() }, apellido?.takeIf { it.isNotBlank() })
        .joinToString(" ")
        .ifBlank { "Sin nombre registrado" }

@Composable
private fun QrAreaScanner(
    onQrScanned: (String) -> Unit,
    onError: (String) -> Unit,
    prompt: String = "Apunte la cámara al QR del área",
    errorMessage: String = "No fue posible leer el QR del área."
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val hasScanned = remember { AtomicBoolean(false) }
    val isProcessing = remember { AtomicBoolean(false) }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(Unit) {
        onDispose {
            barcodeScanner.close()
            cameraExecutor.shutdown()
        }
    }

    Text(prompt)

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processQrImage(
                                imageProxy = imageProxy,
                                barcodeScanner = barcodeScanner,
                                hasScanned = hasScanned,
                                isProcessing = isProcessing,
                                onQrScanned = onQrScanned,
                                onError = onError,
                                errorMessage = errorMessage
                            )
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analyzer
                    )
                } catch (e: Exception) {
                    Log.e(TAG_QR_AREA, "No fue posible abrir la camara", e)
                    onError(errorMessage)
                }
            }, ContextCompat.getMainExecutor(viewContext))

            previewView
        },
        update = {}
    )
}

@OptIn(ExperimentalGetImage::class)
private fun processQrImage(
    imageProxy: ImageProxy,
    barcodeScanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    hasScanned: AtomicBoolean,
    isProcessing: AtomicBoolean,
    onQrScanned: (String) -> Unit,
    onError: (String) -> Unit,
    errorMessage: String
) {
    if (hasScanned.get() || !isProcessing.compareAndSet(false, true)) {
        imageProxy.close()
        return
    }

    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        isProcessing.set(false)
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    barcodeScanner.process(image)
        .addOnSuccessListener { barcodes ->
            val qrValue = barcodes.firstOrNull { it.rawValue != null }?.rawValue
            if (qrValue != null && hasScanned.compareAndSet(false, true)) {
                onQrScanned(qrValue)
            }
        }
        .addOnFailureListener { e ->
            Log.e(TAG_QR_AREA, "Error leyendo QR", e)
            if (hasScanned.compareAndSet(false, true)) {
                onError(errorMessage)
            }
        }
        .addOnCompleteListener {
            isProcessing.set(false)
            imageProxy.close()
        }
}
