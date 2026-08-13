package com.cactus.bitacora

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Size
import android.util.Log
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.cactus.bitacora.data.normalizeParticipantQuery
import com.cactus.bitacora.data.models.AreaOut
import com.cactus.bitacora.data.models.BitacoraDiariaCreate
import com.cactus.bitacora.data.models.BitacoraDiariaOut
import com.cactus.bitacora.data.CatalogAreaOption
import com.cactus.bitacora.data.local.BitacoraEvidenceEntity
import com.cactus.bitacora.data.local.EvidenceType
import com.cactus.bitacora.data.local.GpsStatus
import com.cactus.bitacora.data.local.SyncStatus
import com.cactus.bitacora.model.EmpleadoAreaActivaOut
import com.cactus.bitacora.model.ParticipanteOut
import com.cactus.bitacora.model.ObjetoMonitoreoSatelitalOut
import com.cactus.bitacora.location.BitacoraLocationProvider
import com.cactus.bitacora.location.LocationSnapshot
import com.cactus.bitacora.ui.evidence.EvidencePanel
import com.cactus.bitacora.ui.query.BitacoraQueryScreen
import com.cactus.bitacora.ui.admin.AdminCatalogScreen
import com.cactus.bitacora.ui.home.BitacoraVisualTheme
import com.cactus.bitacora.ui.home.MainBottomBar
import com.cactus.bitacora.ui.home.MainHeader
import com.cactus.bitacora.ui.satellite.ReservoirSatelliteScreen
import com.cactus.bitacora.biometric.FaceIdentificationTarget
import com.cactus.bitacora.biometric.technical.FaceEnrollmentIdentity
import com.cactus.bitacora.biometric.technical.FaceFlowMode
import com.cactus.bitacora.biometric.technical.FaceRecognitionCandidate
import com.cactus.bitacora.biometric.technical.FaceTechnicalScreen
import com.cactus.bitacora.sync.OfflineSyncScheduler
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import org.json.JSONObject
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG_QR_SCANNER = "QrScanner"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BitacoraVisualTheme {
                Surface {
                    BitacoraApp()
                }
            }
        }
    }
}

internal enum class AppScreen {
    Health,
    FaceEnrollment,
    AdminCatalog,
    CreateDailyLog,
    QueryDailyLog,
    Sync,
    ReservoirSatellite,
    More
}

internal enum class AppEnvironment(val label: String) {
    ADMINISTRADOR("Administrador"),
    CIUDADANO("Ciudadano"),
    SUPERVISOR("Supervisor"),
    SEGUIMIENTO_SATELITAL("Seguimiento satelital de embalses")
}

internal data class CitizenEventType(
    val idTipoNovedad: Int,
    val label: String
)

internal val citizenEventTypes = listOf(
    CitizenEventType(idTipoNovedad = 1, label = "PERMISO"),
    CitizenEventType(idTipoNovedad = 2, label = "INCAPACIDAD"),
    CitizenEventType(idTipoNovedad = 3, label = "INCIDENTE"),
    CitizenEventType(idTipoNovedad = 4, label = "INGRESO"),
    CitizenEventType(idTipoNovedad = 5, label = "SALIDA"),
    CitizenEventType(idTipoNovedad = 6, label = "REPORTE DE CULTIVO"),
    CitizenEventType(idTipoNovedad = 7, label = "AUTORIZA HORAS EXTRAS"),
    CitizenEventType(idTipoNovedad = 8, label = "REPORTE GPS"),
    CitizenEventType(idTipoNovedad = 9, label = "SATELITAL")
)

internal val satelliteTrackingTypes = listOf(
    "SUPERFICIE_DE_AGUA",
    "CAMBIO_TERRITORIAL",
    "INUNDACION",
    "DEFORESTACION",
    "CULTIVO",
    "MINERIA",
    "GLACIAR",
    "RIO",
    "ASENTAMIENTO",
    "OTRO"
)

internal fun CitizenEventType.requiresTextEvidence(): Boolean =
    idTipoNovedad != 4 && idTipoNovedad != 5

internal fun CitizenEventType.allowsAudioEvidence(): Boolean =
    idTipoNovedad != 4 && idTipoNovedad != 5

internal const val ADMIN_ONLY_MESSAGE =
    "Esta función está disponible únicamente para el administrador"

internal fun canAccessEnrollment(environment: AppEnvironment?): Boolean =
    environment == AppEnvironment.ADMINISTRADOR

internal fun isScreenAllowed(environment: AppEnvironment?, screen: AppScreen): Boolean =
    when (screen) {
        AppScreen.FaceEnrollment, AppScreen.AdminCatalog ->
            environment == AppEnvironment.ADMINISTRADOR
        AppScreen.ReservoirSatellite ->
            environment == AppEnvironment.SEGUIMIENTO_SATELITAL
        else -> environment == AppEnvironment.ADMINISTRADOR ||
            environment == AppEnvironment.CIUDADANO
    }

internal fun environmentMenuScreens(environment: AppEnvironment): List<AppScreen> =
    if (environment == AppEnvironment.SEGUIMIENTO_SATELITAL) {
        listOf(AppScreen.ReservoirSatellite)
    } else {
        buildList {
            add(AppScreen.Health)
            if (canAccessEnrollment(environment)) add(AppScreen.FaceEnrollment)
            if (environment == AppEnvironment.ADMINISTRADOR) add(AppScreen.AdminCatalog)
            add(AppScreen.CreateDailyLog)
            add(AppScreen.QueryDailyLog)
            add(AppScreen.Sync)
            add(AppScreen.More)
        }
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
    data class Success(val localId: Long, val idBitacora: Int, val areaId: Int) : CreateBitacoraState
    data class Pending(val localId: Long, val areaId: Int, val message: String) : CreateBitacoraState
    data class Error(val message: String) : CreateBitacoraState
}

private sealed interface SyncState {
    data object Idle : SyncState
    data object Loading : SyncState
    data class Ready(val summary: SyncSummary, val lastRun: SyncRunResult? = null) : SyncState
    data class Error(val message: String) : SyncState
}

private data class ParticipanteValidado(
    val participante: ParticipanteOut,
    val asignacion: EmpleadoAreaActivaOut
)

internal enum class DailyLogQrTarget { EMPLEADO, SUPERVISOR, AREA }

private data class DailyLogFaceSession(
    val target: DailyLogQrTarget,
    val mode: FaceFlowMode,
    val enrollmentIdentity: FaceEnrollmentIdentity? = null
)

@Composable
fun BitacoraApp() {
    val context = LocalContext.current.applicationContext
    val repository = remember { BitacoraRepository(context) }
    var activeEnvironment by remember { mutableStateOf<AppEnvironment?>(null) }
    var currentScreen by remember { mutableStateOf(AppScreen.Health) }
    var selectedCitizenEvent by remember { mutableStateOf<CitizenEventType?>(null) }
    var navigationMessage by remember { mutableStateOf<String?>(null) }
    var confirmOldestDeletion by remember { mutableStateOf(false) }
    var deletingOldest by remember { mutableStateOf(false) }
    var oldestDeletionMessage by remember { mutableStateOf<String?>(null) }
    var backendOnline by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        OfflineSyncScheduler.schedule(context)
        OfflineSyncScheduler.enqueueNow(context)
    }
    LaunchedEffect(activeEnvironment, currentScreen) {
        backendOnline = if (
            activeEnvironment == AppEnvironment.ADMINISTRADOR ||
            activeEnvironment == AppEnvironment.CIUDADANO ||
            activeEnvironment == AppEnvironment.SUPERVISOR
        ) {
            try {
                repository.checkHealth()
                true
            } catch (_: Exception) {
                false
            }
        } else {
            null
        }
    }
    BackHandler(enabled = activeEnvironment != null && currentScreen != AppScreen.Health) {
        if (activeEnvironment == AppEnvironment.SEGUIMIENTO_SATELITAL) {
            activeEnvironment = null
            currentScreen = AppScreen.Health
        } else {
            currentScreen = mainDestinationAfterBack()
        }
    }

    fun selectEnvironment(environment: AppEnvironment) {
        activeEnvironment = environment
        currentScreen = if (environment == AppEnvironment.SEGUIMIENTO_SATELITAL) {
            AppScreen.ReservoirSatellite
        } else {
            AppScreen.Health
        }
        navigationMessage = null
    }

    fun changeEnvironment() {
        currentScreen = AppScreen.Health
        navigationMessage = null
        activeEnvironment = null
        selectedCitizenEvent = null
    }

    fun openScreen(screen: AppScreen) {
        if (isScreenAllowed(activeEnvironment, screen)) {
            currentScreen = screen
            navigationMessage = null
        } else {
            currentScreen = AppScreen.Health
            navigationMessage = ADMIN_ONLY_MESSAGE
        }
    }

    val environment = activeEnvironment
    Scaffold(
        bottomBar = {
            if (
                environment == AppEnvironment.ADMINISTRADOR ||
                environment == AppEnvironment.CIUDADANO
            ) {
                MainBottomBar(
                    currentScreen = currentScreen,
                    onNavigate = { destination ->
                        if (destination == AppScreen.CreateDailyLog) {
                            selectedCitizenEvent = null
                        }
                        openScreen(destination)
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (
                environment == AppEnvironment.ADMINISTRADOR &&
                currentScreen == AppScreen.AdminCatalog
            ) {
                AdminCatalogScreen(
                    repository = repository,
                    onBack = { currentScreen = mainDestinationAfterBack() }
                )
                return@Column
            }

            if (
                environment == AppEnvironment.SEGUIMIENTO_SATELITAL &&
                currentScreen == AppScreen.ReservoirSatellite
            ) {
                ReservoirSatelliteScreen(
                    repository = repository,
                    onBack = ::changeEnvironment
                )
                return@Column
            }

            if (environment == null) {
                EnvironmentSelectionScreen(onSelect = ::selectEnvironment)
                return@Column
            }

            if (environment == AppEnvironment.SUPERVISOR) {
                SupervisorModeScreen(repository = repository, onExit = ::changeEnvironment)
                return@Column
            }

            MainHeader(
                environment = environment,
                online = backendOnline,
                onChangeEnvironment = ::changeEnvironment
            )
            navigationMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }


            Box(modifier = Modifier.weight(1f)) {
                when (currentScreen) {
                    AppScreen.Health -> if (environment == AppEnvironment.CIUDADANO) {
                        CitizenEventTypeScreen { eventType ->
                            selectedCitizenEvent = eventType
                            openScreen(AppScreen.CreateDailyLog)
                        }
                    } else {
                        BackendStatusScreen(repository)
                    }
                    AppScreen.FaceEnrollment -> if (canAccessEnrollment(environment)) {
                        FaceEnrollmentAdminScreen(
                            repository = repository,
                            onBack = { currentScreen = mainDestinationAfterBack() }
                        )
                    } else {
                        RestrictedEnrollmentScreen(
                            onBack = { currentScreen = mainDestinationAfterBack() }
                        )
                    }
                    AppScreen.AdminCatalog -> if (
                        environment == AppEnvironment.ADMINISTRADOR
                    ) {
                        AdminCatalogScreen(
                            repository = repository,
                            onBack = { currentScreen = mainDestinationAfterBack() }
                        )
                    } else {
                        RestrictedEnrollmentScreen(
                            onBack = { currentScreen = mainDestinationAfterBack() }
                        )
                    }
                    AppScreen.CreateDailyLog -> CrearBitacoraDiariaScreen(
                        repository = repository,
                        citizenEventType = selectedCitizenEvent
                    )
                    AppScreen.QueryDailyLog -> BitacoraQueryScreen(
                        repository = repository,
                        allowDelete = environment == AppEnvironment.ADMINISTRADOR
                    )
                    AppScreen.Sync -> SyncScreen(repository)
                    AppScreen.ReservoirSatellite -> ReservoirSatelliteScreen(
                        repository = repository,
                        onBack = { currentScreen = mainDestinationAfterBack() }
                    )
                    AppScreen.More -> MoreScreen(
                        environment = environment,
                        deletingOldest = deletingOldest,
                        deletionMessage = oldestDeletionMessage,
                        onEnrollment = { openScreen(AppScreen.FaceEnrollment) },
                        onAdminCatalog = { openScreen(AppScreen.AdminCatalog) },
                        onRefreshCatalogs = { openScreen(AppScreen.Sync) },
                        onDeleteOldest = { confirmOldestDeletion = true },
                        onChangeEnvironment = ::changeEnvironment
                    )
                }
            }
        }
    }

    if (confirmOldestDeletion && activeEnvironment == AppEnvironment.ADMINISTRADOR) {
        AlertDialog(
            onDismissRequest = { confirmOldestDeletion = false },
            title = { Text("Eliminar las 10 bitácoras más antiguas") },
            text = {
                Text(
                    "Se eliminarán permanentemente hasta 10 bitácoras, comenzando por " +
                        "las más antiguas, junto con sus evidencias y archivos asociados."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deletingOldest,
                    onClick = {
                        confirmOldestDeletion = false
                        deletingOldest = true
                        oldestDeletionMessage = null
                        scope.launch {
                            try {
                                repository.deleteOldestBitacoras(10)
                                oldestDeletionMessage = "Se eliminaron las bitácoras más antiguas"
                            } catch (error: Exception) {
                                oldestDeletionMessage = error.message
                                    ?: "No fue posible eliminar las bitácoras más antiguas"
                            } finally {
                                deletingOldest = false
                            }
                        }
                    }
                ) {
                    Text("Sí, eliminar")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !deletingOldest,
                    onClick = { confirmOldestDeletion = false }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun CitizenEventTypeScreen(onSelect: (CitizenEventType) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "¿Qué desea registrar?",
            style = MaterialTheme.typography.titleLarge
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = citizenEventTypes.filterNot { it.idTipoNovedad == 9 },
                key = { it.idTipoNovedad }
            ) { eventType ->
                val visual = eventVisual(eventType)
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(124.dp)
                        .semantics {
                            contentDescription = "Registrar ${visual.second}"
                        },
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    onClick = { onSelect(eventType) }
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(visual.first, style = MaterialTheme.typography.headlineSmall)
                        Text(visual.second, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

private fun eventVisual(eventType: CitizenEventType): Pair<String, String> = when (
    eventType.idTipoNovedad
) {
    1 -> "✓" to "Permiso"
    2 -> "✚" to "Incapacidad"
    3 -> "!" to "Incidente"
    4 -> "→" to "Ingreso / entrada"
    5 -> "←" to "Salida"
    6 -> "♧" to "Reporte de cultivo"
    7 -> "◷" to "Autorización de horas extras"
    8 -> "▤" to "Reporte GPS"
    else -> "•" to eventType.label
}

@Composable
private fun MoreScreen(
    environment: AppEnvironment,
    deletingOldest: Boolean,
    deletionMessage: String?,
    onEnrollment: () -> Unit,
    onAdminCatalog: () -> Unit,
    onRefreshCatalogs: () -> Unit,
    onDeleteOldest: () -> Unit,
    onChangeEnvironment: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            if (environment == AppEnvironment.ADMINISTRADOR) {
                "Administración"
            } else {
                "Más opciones"
            },
            style = MaterialTheme.typography.titleLarge
        )
        if (environment == AppEnvironment.ADMINISTRADOR) {
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                onClick = onEnrollment
            ) { Text("Enrolamiento facial") }
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                onClick = onAdminCatalog
            ) { Text("Administrar maestros") }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                onClick = onRefreshCatalogs
            ) { Text("Actualizar catálogos y sincronización") }
            Button(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                enabled = !deletingOldest,
                onClick = onDeleteOldest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) { Text("Borrar las 10 bitácoras más antiguas") }
            deletionMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            onClick = onChangeEnvironment
        ) { Text("Cambiar ambiente") }
    }
}

@Composable
private fun EnvironmentSelectionScreen(onSelect: (AppEnvironment) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Bitácora", style = MaterialTheme.typography.headlineSmall)
        Text("Seleccione cómo desea ingresar", style = MaterialTheme.typography.titleLarge)
        Button(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            onClick = { onSelect(AppEnvironment.ADMINISTRADOR) }
        ) { Text("Administrador") }
        Button(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            onClick = { onSelect(AppEnvironment.CIUDADANO) }
        ) { Text("Ciudadano") }
        Button(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            onClick = { onSelect(AppEnvironment.SUPERVISOR) }
        ) { Text("SUPERVISOR") }
        Button(
            modifier = Modifier.fillMaxWidth().height(72.dp),
            onClick = { onSelect(AppEnvironment.SEGUIMIENTO_SATELITAL) }
        ) { Text("Seguimiento satelital de embalses") }
    }
}

@Composable
private fun RestrictedEnrollmentScreen(onBack: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(ADMIN_ONLY_MESSAGE, color = MaterialTheme.colorScheme.error)
        Button(onClick = onBack) { Text("Volver") }
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
private fun FaceEnrollmentAdminScreen(
    repository: BitacoraRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<ParticipanteOut>>(emptyList()) }
    var selectedParticipant by remember { mutableStateOf<ParticipanteOut?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var diagnostic by remember { mutableStateOf<String?>(null) }
    var activeAssignments by remember { mutableStateOf<List<EmpleadoAreaActivaOut>>(emptyList()) }
    var assignmentsInfo by remember { mutableStateOf("Sin información de roles activos") }
    var scanningQr by remember { mutableStateOf(false) }
    var faceSession by remember { mutableStateOf<DailyLogFaceSession?>(null) }

    suspend fun acceptParticipant(participant: ParticipanteOut): Boolean =
        try {
            selectedParticipant = participant
            results = emptyList()
            query = participant.identificacion_participante.orEmpty()
            error = null
            activeAssignments = try {
                repository.getAsignacionesActivas(participant.id_participante)
            } catch (_: Exception) {
                emptyList()
            }
            assignmentsInfo = if (activeAssignments.isEmpty()) {
                "Sin roles o asignaciones activas (esto no impide enrolar)"
            } else {
                activeAssignments.joinToString(" · ") {
                    "${roleLabel(it.cargo)} / ${it.area_descripcion ?: "Área ${it.id_area}"}"
                }
            }
            true
        } catch (exception: Exception) {
            selectedParticipant = null
            error = exception.message ?: "No fue posible seleccionar el participante"
            false
        }

    fun chooseParticipant(participant: ParticipanteOut) {
        loading = true
        error = null
        scope.launch {
            acceptParticipant(participant)
            loading = false
        }
    }

    fun resolveQr(rawCode: String) {
        val code = normalizeParticipantQuery(rawCode)
        if (code.isBlank()) {
            error = "Escriba o escanee el código del participante"
            return
        }
        query = code
        loading = true
        error = null
        scope.launch {
            try {
                val participant = repository.getParticipanteByQr(code)
                diagnostic =
                    "Código recibido: $rawCode\n" +
                    "Código normalizado: $code\n" +
                    "Fuente consultada: API participante/by_qr\n" +
                    "Registros encontrados: 1"
                acceptParticipant(participant)
            } catch (exception: HttpException) {
                selectedParticipant = null
                diagnostic =
                    "Código recibido: $rawCode\n" +
                    "Código normalizado: $code\n" +
                    "Fuente consultada: API participante/by_qr\n" +
                    "Registros encontrados: 0\n" +
                    "Motivo: el código no existe en participante"
                error = if (exception.code() == 404) {
                    "No existe un participante con el código $code"
                } else {
                    exception.httpDetail() ?: exception.message()
                }
            } catch (exception: Exception) {
                selectedParticipant = null
                error = exception.httpDetail()
                    ?: exception.message
                    ?: "No fue posible encontrar el participante"
            } finally {
                loading = false
            }
        }
    }

    fun search() {
        val received = query
        val term = normalizeParticipantQuery(received)
        if (term.isBlank()) {
            error = "Escriba un código o nombre para buscar"
            return
        }
        loading = true
        error = null
        selectedParticipant = null
        scope.launch {
            try {
                val searchResult = repository.searchParticipantes(received)
                results = searchResult.participants
                diagnostic =
                    "Código recibido: ${searchResult.receivedCode}\n" +
                    "Código normalizado: ${searchResult.normalizedCode}\n" +
                    "Fuente consultada: ${searchResult.source}\n" +
                    "Registros encontrados: ${searchResult.participants.size}"
                if (results.isEmpty()) {
                    error = "No existe un participante para el código o nombre indicado"
                    diagnostic = diagnostic.orEmpty() +
                        "\nMotivo: sin coincidencias en participante"
                } else if (
                    results.size == 1 &&
                    results.first().identificacion_participante
                        ?.let(::normalizeParticipantQuery) == term
                ) {
                    acceptParticipant(results.first())
                }
            } catch (exception: Exception) {
                results = emptyList()
                diagnostic =
                    "Código recibido: $received\n" +
                    "Código normalizado: $term\n" +
                    "Fuente consultada: API del servidor\n" +
                    "Registros encontrados: 0\n" +
                    "Motivo: ${exception.message ?: "error de comunicación"}"
                error = exception.httpDetail()
                    ?: exception.message
                    ?: "No fue posible buscar participantes"
            } finally {
                loading = false
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        scanningQr = granted
        if (!granted) error = "Se necesita permiso de cámara para leer el QR personal"
    }

    fun startQrScan() {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            scanningQr = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    BackHandler {
        when {
            faceSession != null -> faceSession = null
            scanningQr -> scanningQr = false
            else -> onBack()
        }
    }

    faceSession?.let { session ->
        key(session.target, session.mode, session.enrollmentIdentity?.participantId) {
            FaceTechnicalScreen(
                target = null,
                mode = session.mode,
                enrollmentIdentity = session.enrollmentIdentity,
                onConfirmed = {
                    message = "Reconocimiento correcto: ${it.displayName}"
                    faceSession = null
                },
                onEnrollmentComplete = {
                    message = "Rostro registrado correctamente"
                    selectedParticipant = null
                    query = ""
                    activeAssignments = emptyList()
                    assignmentsInfo = "Sin información de roles activos"
                    faceSession = null
                },
                onTestRecognition = {
                    faceSession = session.copy(
                        mode = FaceFlowMode.IDENTIFICATION,
                        enrollmentIdentity = null
                    )
                },
                onUseQr = {
                    faceSession = null
                    startQrScan()
                },
                onCancel = { faceSession = null }
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Administrar rostros", style = MaterialTheme.typography.titleLarge)
        Text("Busque y seleccione directamente un participante")

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = query,
            onValueChange = {
                query = it
                selectedParticipant = null
                error = null
            },
            enabled = !loading,
            label = { Text("Código o nombre") },
            singleLine = true
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = query.isNotBlank() && !loading,
                onClick = { search() }
            ) { Text("Buscar") }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = !loading && !scanningQr,
                onClick = { startQrScan() }
            ) { Text("Leer QR personal") }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = query.isNotBlank() && !loading,
            onClick = { search() }
        ) {
            Text("Actualizar participantes desde el servidor")
        }

        if (scanningQr) {
            QrScanner(
                prompt = "Apunte la cámara al QR personal",
                errorMessage = "No fue posible leer el QR personal.",
                onQrScanned = {
                    scanningQr = false
                    resolveQr(it)
                },
                onError = {
                    scanningQr = false
                    error = it
                }
            )
        }

        if (loading) {
            CircularProgressIndicator()
            Text("Buscando participante…")
        }

        results.forEach { participant ->
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { chooseParticipant(participant) }
            ) {
                Text(
                    "${participant.nombreCompleto()} · " +
                        (participant.identificacion_participante ?: participant.id_participante)
                )
            }
        }

        selectedParticipant?.let { participant ->
            Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 2.dp) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Participante seleccionado", color = MaterialTheme.colorScheme.primary)
                    Text("Nombre: ${participant.nombreCompleto()}")
                    Text("Apellido: ${participant.apellido.orEmpty()}")
                    Text(
                        "Código: " +
                            (participant.identificacion_participante ?: participant.id_participante)
                    )
                    participant.documento?.takeIf { it.isNotBlank() }?.let {
                        Text("Documento: $it")
                    }
                    Text("Roles/asignaciones: $assignmentsInfo")
                }
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedParticipant != null && !loading,
            onClick = {
                val participant = selectedParticipant ?: return@Button
                faceSession = DailyLogFaceSession(
                    target = DailyLogQrTarget.EMPLEADO,
                    mode = FaceFlowMode.ENROLLMENT,
                    enrollmentIdentity = FaceEnrollmentIdentity(
                        participantId = participant.id_participante,
                        participantCode = participant.identificacion_participante
                            ?: participant.id_participante.toString(),
                        displayName = participant.nombreCompleto()
                    )
                )
            }
        ) { Text("Enrolar rostro") }

        if (selectedParticipant == null) {
            Text(
                "Seleccione primero un participante",
                color = MaterialTheme.colorScheme.error
            )
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        diagnostic?.let {
            Text("Diagnóstico de búsqueda\n$it", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onBack) {
            Text("Cancelar y regresar")
        }
    }
}

internal val DAILY_LOG_FACE_MODE = FaceFlowMode.IDENTIFICATION
internal const val ENROLLMENT_IDENTITY_SCOPE = "Participante"

internal fun mainDestinationAfterBack(): AppScreen = AppScreen.Health

internal fun areaMatchesAssignment(selectedAreaId: Int?, assignedAreaId: Int?): Boolean =
    selectedAreaId != null && assignedAreaId != null && selectedAreaId == assignedAreaId

internal fun roleLabel(cargo: Int?): String = when (cargo) {
    3 -> "Supervisor"
    4 -> "Gerente"
    null -> "Rol no especificado"
    else -> "Empleado/otro (cargo $cargo)"
}

internal fun assignmentAllowsTarget(cargo: Int?, target: DailyLogQrTarget): Boolean = when (target) {
    DailyLogQrTarget.SUPERVISOR -> cargo == 3
    DailyLogQrTarget.EMPLEADO -> cargo != 3 && cargo != 4
    DailyLogQrTarget.AREA -> false
}

internal fun allowsOfflineManager(
    normalizedQr: String,
    participantId: Int,
    cargo: Int?,
    active: Boolean,
    startDate: String?,
    endDate: String?,
    today: String
): Boolean {
    if (!active) return false
    if (startDate != null && startDate > today) return false
    if (endDate != null && endDate < today) return false
    return cargo == 4 ||
        (
            normalizedQr.trim().uppercase(Locale.ROOT) == "P0001" &&
                participantId == 1 &&
                cargo == 3
        )
}

internal fun missingRoleMessage(target: DailyLogQrTarget): String =
    "El participante reconocido no tiene el rol activo requerido: " +
        if (target == DailyLogQrTarget.SUPERVISOR) "Supervisor" else "Empleado"

internal const val SAVE_DAILY_LOG_LABEL = "Guardar bitácora"

@Composable
internal fun CrearBitacoraDiariaScreen(
    repository: BitacoraRepository,
    citizenEventType: CitizenEventType? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locationProvider = remember { BitacoraLocationProvider(context.applicationContext) }
    var qrEmpleado by remember { mutableStateOf("") }
    var qrSupervisor by remember { mutableStateOf("") }
    var qrArea by remember { mutableStateOf("") }
    var empleado by remember { mutableStateOf<ParticipanteValidado?>(null) }
    var supervisor by remember { mutableStateOf<ParticipanteValidado?>(null) }
    var area by remember { mutableStateOf<AreaOut?>(null) }
    var availableAreas by remember { mutableStateOf<List<CatalogAreaOption>>(emptyList()) }
    var areaMenuExpanded by remember { mutableStateOf(false) }
    var supervisorFallbackRequired by remember { mutableStateOf(false) }
    var empleadoError by remember { mutableStateOf<String?>(null) }
    var supervisorError by remember { mutableStateOf<String?>(null) }
    var areaError by remember { mutableStateOf<String?>(null) }
    var validatingTarget by remember { mutableStateOf<DailyLogQrTarget?>(null) }
    var scanningTarget by remember { mutableStateOf<DailyLogQrTarget?>(null) }
    var pendingCameraTarget by remember { mutableStateOf<DailyLogQrTarget?>(null) }
    var horaEntrada by remember { mutableStateOf("") }
    var horaSalida by remember { mutableStateOf("") }
    var tipoAnotacion by remember {
        mutableStateOf(citizenEventType?.idTipoNovedad?.toString().orEmpty())
    }
    var observaciones by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<CreateBitacoraState>(CreateBitacoraState.Idle) }
    var gpsLocation by remember { mutableStateOf<LocationSnapshot?>(null) }
    var gpsLoading by remember { mutableStateOf(false) }
    var gpsError by remember { mutableStateOf<String?>(null) }
    var faceSession by remember { mutableStateOf<DailyLogFaceSession?>(null) }
    val isSatellite = citizenEventType?.idTipoNovedad == 9
    var satelliteObjects by remember {
        mutableStateOf<List<ObjetoMonitoreoSatelitalOut>>(emptyList())
    }
    var selectedSatelliteObject by remember {
        mutableStateOf<ObjetoMonitoreoSatelitalOut?>(null)
    }
    var satelliteObjectMenuExpanded by remember { mutableStateOf(false) }
    var selectedTrackingType by remember { mutableStateOf<String?>(null) }
    var trackingTypeMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(citizenEventType?.idTipoNovedad) {
        if (citizenEventType != null) {
            tipoAnotacion = citizenEventType.idTipoNovedad.toString()
        }
        availableAreas = repository.getAdministrativeAreas()
        if (availableAreas.isEmpty()) {
            repository.syncReferenceCatalogs()
            availableAreas = repository.getAdministrativeAreas()
        }
        if (isSatellite) {
            satelliteObjects = repository.getObjetosMonitoreoSatelital()
            selectedSatelliteObject = satelliteObjects.firstOrNull()
        }
    }

    suspend fun resolveEmployeeSupervisor(employee: ParticipanteValidado) {
        supervisor = null
        supervisorError = null
        supervisorFallbackRequired = false
        try {
            val (participant, assignment) = repository.getSupervisorForEmployee(
                employee.participante.id_participante
            )
            supervisor = ParticipanteValidado(participant, assignment)
        } catch (_: Exception) {
            supervisorFallbackRequired = true
            supervisorError = "No se localizó supervisor. Escanee el QR del gerente."
        }
    }

    fun obtainGps() {
        gpsLocation = null
        gpsError = null
        if (!locationProvider.hasPermission()) {
            gpsError = "Se necesita permiso de ubicación para crear la bitácora"
            return
        }
        if (!locationProvider.isLocationEnabled()) {
            gpsError = "La ubicación está desactivada. Actívela y vuelva a intentar."
            return
        }
        gpsLoading = true
        scope.launch {
            try {
                gpsLocation = locationProvider.getCurrentLocation()
            } catch (e: Exception) {
                gpsError = e.message ?: "No fue posible obtener el GPS"
            } finally {
                gpsLoading = false
            }
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) obtainGps()
        else gpsError = "Permiso de ubicación rechazado. No se puede crear la bitácora."
    }

    LaunchedEffect(citizenEventType?.idTipoNovedad) {
        if (citizenEventType != null) {
            if (locationProvider.hasPermission()) {
                obtainGps()
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }
    }

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
                val asignacion = repository.getAsignacionParaRol(
                    participante.id_participante,
                    supervisor = false
                )
                empleado = ParticipanteValidado(participante, asignacion)
                if (citizenEventType != null) {
                    resolveEmployeeSupervisor(requireNotNull(empleado))
                }
            } catch (e: Exception) {
                empleadoError = e.httpDetail()
                    ?: e.message
                    ?: "No fue posible validar el empleado"
            } finally {
                validatingTarget = null
            }
        }
    }

    fun validateSupervisor(rawQr: String, managerFallback: Boolean = false) {
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
                val asignacion = if (managerFallback) {
                    repository.getAsignacionesActivas(participante.id_participante)
                        .firstOrNull {
                            allowsOfflineManager(
                                normalizedQr = qr,
                                participantId = participante.id_participante,
                                cargo = it.cargo,
                                active = true,
                                startDate = null,
                                endDate = it.fecha_final,
                                today = SimpleDateFormat(
                                    "yyyy-MM-dd",
                                    Locale.ROOT
                                ).format(Date())
                            )
                        }
                        ?: throw IllegalStateException(
                            "El participante no tiene un cargo activo de gerente"
                        )
                } else {
                    repository.getAsignacionParaRol(
                        participante.id_participante,
                        supervisor = true
                    )
                }
                supervisor = ParticipanteValidado(participante, asignacion)
                supervisorFallbackRequired = false
            } catch (e: Exception) {
                supervisorError = e.httpDetail()
                    ?: e.message
                    ?: "No fue posible validar el supervisor"
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
                if (
                    citizenEventType == null &&
                    !areaMatchesAssignment(
                        areaConsultada.id_area,
                        empleadoActual.asignacion.id_area
                    )
                ) {
                    throw IllegalStateException(
                        "El área seleccionada no coincide con la asignación activa del empleado"
                    )
                }
                if (citizenEventType == null) {
                    repository.validarAsignacionArea(
                        empleadoActual.participante.id_participante,
                        areaConsultada.id_area
                    )
                }
                area = areaConsultada
            } catch (e: Exception) {
                areaError = e.httpDetail()
                    ?: e.message
                    ?: "No fue posible validar el área"
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

    val missingCreateRequirements = buildList {
        if (empleado == null) add("Validar empleado")
        if (supervisor == null) add("Validar supervisor")
        if (area == null) add("Validar área administrativa")
        if (
            citizenEventType == null &&
            area != null &&
            !areaMatchesAssignment(area?.id_area, empleado?.asignacion?.id_area)
        ) {
            add("El área debe coincidir con la asignación del empleado")
        }
        if (tipoAnotacion.toIntOrNull() == null) add("Seleccionar un tipo de anotación válido")
        if (citizenEventType != null && gpsLocation == null) add("Obtener ubicación GPS")
        if (
            citizenEventType?.requiresTextEvidence() == true &&
            observaciones.isBlank()
        ) {
            add("Ingresar evidencia de texto")
        }
        if (isSatellite && selectedSatelliteObject == null) {
            add("Seleccionar objeto monitoreado")
        }
        if (isSatellite && selectedTrackingType == null) {
            add("Seleccionar tipo de seguimiento")
        }
        if (validatingTarget != null || scanningTarget != null) add("Finalizar la validación QR en curso")
    }
    val canCreate = missingCreateRequirements.isEmpty()

    suspend fun saveCitizenTextEvidence(
        localId: Long,
        serverId: Int?,
        areaId: Int,
        location: LocationSnapshot
    ) {
        repository.saveEvidence(
            BitacoraEvidenceEntity(
                bitacoraLocalId = localId,
                bitacoraServerId = serverId,
                areaId = areaId,
                clientUuid = UUID.randomUUID().toString(),
                evidenceType = EvidenceType.TEXT,
                textContent = observaciones.trim(),
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                gpsTimestamp = location.timestamp,
                locationProvider = location.provider,
                gpsStatus = GpsStatus.READY,
                syncStatus = SyncStatus.PENDIENTE_CREAR
            )
        )
    }

    fun createDailyLog() {
        val empleadoValidado = empleado
        val supervisorValidado = supervisor
        val areaValidada = area
        val entrada = horaEntrada.toIntOrNull()
        val salida = horaSalida.toIntOrNull()
        val tipo = tipoAnotacion.toIntOrNull()

        if (empleadoValidado == null || supervisorValidado == null || areaValidada == null || !canCreate) {
            state = CreateBitacoraState.Error("Debe validar empleado, supervisor y área antes de crear la bitácora")
            return
        }

        if (tipoAnotacion.isNotBlank() && tipo == null) {
            state = CreateBitacoraState.Error("tipo_anotacion debe ser numerico")
            return
        }

        if (horaEntrada.isNotBlank() && entrada == null) {
            state = CreateBitacoraState.Error("La hora de entrada debe expresarse en minutos Unix")
            return
        }

        if (horaSalida.isNotBlank() && salida == null) {
            state = CreateBitacoraState.Error("La hora de salida debe expresarse en minutos Unix")
            return
        }

        state = CreateBitacoraState.Loading
        scope.launch {
            state = try {
                val closeLocation = if (salida != null) {
                    locationProvider.getCurrentLocation()
                } else {
                    null
                }
                when (val result = repository.crearBitacoraDiaria(
                    BitacoraDiariaCreate(
                        id_empleado = empleadoValidado.participante.id_participante,
                        id_supervisor = supervisorValidado.participante.id_participante,
                        ts_in_min = entrada,
                        ts_out_min = salida,
                        tipo_anotacion = tipo,
                        observaciones = observaciones.ifBlank { null },
                        client_uuid = UUID.randomUUID().toString(),
                        qr_area = if (citizenEventType == null) qrArea.trim() else null,
                        id_objeto_monitoreo =
                            if (isSatellite) selectedSatelliteObject?.id_objeto_monitoreo else null,
                        origen_bitacora = if (isSatellite) "SATELITAL" else "MANUAL",
                        tipo_seguimiento_satelital =
                            if (isSatellite) selectedTrackingType else null
                    ),
                    openLocation = gpsLocation,
                    closeLocation = closeLocation
                )) {
                    is CreateBitacoraResult.Sincronizada -> {
                        if (citizenEventType != null && observaciones.isNotBlank()) {
                            saveCitizenTextEvidence(
                                result.localId,
                                result.bitacora.id_bitacora,
                                areaValidada.id_area,
                                requireNotNull(gpsLocation)
                            )
                        }
                        CreateBitacoraState.Success(
                            result.localId,
                            result.bitacora.id_bitacora,
                            areaValidada.id_area
                        )
                    }
                    is CreateBitacoraResult.Pendiente -> {
                        if (citizenEventType != null && observaciones.isNotBlank()) {
                            saveCitizenTextEvidence(
                                result.localId,
                                null,
                                areaValidada.id_area,
                                requireNotNull(gpsLocation)
                            )
                        }
                        CreateBitacoraState.Pending(
                            result.localId,
                            areaValidada.id_area,
                            result.message
                        )
                    }
                }
            } catch (e: Exception) {
                CreateBitacoraState.Error(e.message ?: "No fue posible crear la bitacora diaria")
            }
        }
    }

    fun startFaceIdentification(target: DailyLogQrTarget) {
        require(target != DailyLogQrTarget.AREA)
        faceSession = DailyLogFaceSession(
            target = target,
            mode = DAILY_LOG_FACE_MODE
        )
    }

    fun confirmFaceCandidate(target: DailyLogQrTarget, candidate: FaceRecognitionCandidate) {
        faceSession = null
        validatingTarget = target
        scope.launch {
            try {
                val participant = repository.getParticipanteByQr(candidate.participantCode)
                val assignments = repository.getAsignacionesActivas(candidate.participantId)
                val assignment = assignments.firstOrNull {
                    assignmentAllowsTarget(it.cargo, target)
                } ?: throw IllegalStateException(missingRoleMessage(target))
                val validated = ParticipanteValidado(participant, assignment)
                when (target) {
                    DailyLogQrTarget.EMPLEADO -> {
                        qrEmpleado = candidate.participantCode
                        empleado = validated
                        empleadoError = null
                        area = null
                        areaError = null
                        if (citizenEventType != null) {
                            resolveEmployeeSupervisor(validated)
                        }
                    }
                    DailyLogQrTarget.SUPERVISOR -> {
                        qrSupervisor = candidate.participantCode
                        supervisor = validated
                        supervisorError = null
                    }
                    DailyLogQrTarget.AREA -> Unit
                }
            } catch (error: Exception) {
                val message = error.httpDetail()
                    ?: error.message
                    ?: missingRoleMessage(target)
                if (target == DailyLogQrTarget.EMPLEADO) empleadoError = message
                else supervisorError = message
            } finally {
                validatingTarget = null
            }
        }
    }

    val qrScannerForTarget: @Composable (DailyLogQrTarget) -> Unit = { target ->
        if (scanningTarget == target) {
            QrScanner(
                prompt = "Apunte la cámara al QR de ${target.name.lowercase()}",
                errorMessage = "No fue posible leer el QR de ${target.name.lowercase()}.",
                onQrScanned = { rawQr ->
                    scanningTarget = null
                    when (target) {
                        DailyLogQrTarget.EMPLEADO -> {
                            qrEmpleado = rawQr
                            validateEmpleado(rawQr)
                        }
                        DailyLogQrTarget.SUPERVISOR -> {
                            qrSupervisor = rawQr
                            validateSupervisor(
                                rawQr,
                                managerFallback = citizenEventType != null &&
                                    supervisorFallbackRequired
                            )
                        }
                        DailyLogQrTarget.AREA -> {
                            qrArea = rawQr
                            validateArea(rawQr)
                        }
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
    }

    faceSession?.let { session ->
        val target = if (session.target == DailyLogQrTarget.EMPLEADO) {
            FaceIdentificationTarget.EMPLEADO
        } else {
            FaceIdentificationTarget.SUPERVISOR
        }
        key(session.target, session.mode, session.enrollmentIdentity?.participantId) {
            FaceTechnicalScreen(
                target = target,
                mode = session.mode,
                enrollmentIdentity = session.enrollmentIdentity,
                onConfirmed = { confirmFaceCandidate(session.target, it) },
                onEnrollmentComplete = { faceSession = null },
                onTestRecognition = {
                    faceSession = session.copy(
                        mode = FaceFlowMode.IDENTIFICATION,
                        enrollmentIdentity = null
                    )
                },
                onUseQr = {
                    faceSession = null
                    startScan(session.target)
                },
                onCancel = { faceSession = null }
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = citizenEventType?.let {
                "${it.label} · tipo_novedad ${it.idTipoNovedad}"
            } ?: "Crear Bitacora Diaria",
            style = MaterialTheme.typography.titleMedium
        )

        QrValidationSection(
            title = "Empleado",
            scanText = "Escanear empleado",
            qr = qrEmpleado,
            onQrChange = { qrEmpleado = it; empleado = null; empleadoError = null; area = null },
            onScan = { startScan(DailyLogQrTarget.EMPLEADO) },
            onValidate = { validateEmpleado(qrEmpleado) },
            loading = validatingTarget == DailyLogQrTarget.EMPLEADO,
            error = empleadoError,
            validated = empleado != null
        ) {
            qrScannerForTarget(DailyLogQrTarget.EMPLEADO)
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { startFaceIdentification(DailyLogQrTarget.EMPLEADO) }
            ) { Text("Reconocer rostro") }
            empleado?.let {
                Text("Empleado validado localmente", color = MaterialTheme.colorScheme.primary)
                Text("Nombre: ${it.participante.nombreCompleto()}")
                Text("Identificación: ${it.participante.identificacion_participante.orEmpty()}")
                Text("Área asignada: ${it.asignacion.area_descripcion ?: it.asignacion.id_area}")
            }
        }

        if (citizenEventType == null || supervisorFallbackRequired) {
            QrValidationSection(
                title = if (supervisorFallbackRequired) "Gerente de respaldo" else "Supervisor",
                scanText = if (supervisorFallbackRequired) "Escanear QR del gerente" else "Escanear supervisor",
                qr = qrSupervisor,
                onQrChange = { qrSupervisor = it; supervisor = null; supervisorError = null },
                onScan = { startScan(DailyLogQrTarget.SUPERVISOR) },
                onValidate = {
                    validateSupervisor(
                        qrSupervisor,
                        managerFallback = supervisorFallbackRequired
                    )
                },
                loading = validatingTarget == DailyLogQrTarget.SUPERVISOR,
                error = supervisorError,
                validated = supervisor != null
            ) {
                qrScannerForTarget(DailyLogQrTarget.SUPERVISOR)
                if (!supervisorFallbackRequired) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { startFaceIdentification(DailyLogQrTarget.SUPERVISOR) }
                    ) { Text("Reconocer rostro") }
                }
                supervisor?.let {
                    Text("Supervisor validado localmente", color = MaterialTheme.colorScheme.primary)
                    Text("Nombre: ${it.participante.nombreCompleto()}")
                    Text("Identificación: ${it.participante.identificacion_participante.orEmpty()}")
                }
            }
        } else {
            supervisor?.let {
                Text("Supervisor asignado", style = MaterialTheme.typography.titleMedium)
                Text("Nombre: ${it.participante.nombreCompleto()}")
                Text("Identificación: ${it.participante.identificacion_participante.orEmpty()}")
            } ?: Text("El supervisor se completa al identificar al empleado")
        }

        Text("Área administrativa", style = MaterialTheme.typography.titleMedium)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { areaMenuExpanded = true }
            ) {
                Text(
                    area?.let { "1) Selección de áreas: ${it.descripcion}" }
                        ?: "1) Selección de áreas"
                )
            }
            DropdownMenu(
                expanded = areaMenuExpanded,
                onDismissRequest = { areaMenuExpanded = false }
            ) {
                availableAreas.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text("${option.area.descripcion}\n${option.qr}")
                        },
                        onClick = {
                            qrArea = option.qr
                            areaMenuExpanded = false
                            if (citizenEventType == null) {
                                validateArea(option.qr)
                            } else {
                                area = option.area
                                areaError = null
                            }
                        }
                    )
                }
            }
        }
        if (availableAreas.isEmpty()) {
            Text(
                "No hay áreas administrativas en el catálogo local",
                color = MaterialTheme.colorScheme.error
            )
        }
        QrValidationSection(
            title = "2) Lectura de área mediante QR",
            scanText = "Leer QR AREA_ADMINISTRATIVA",
            qr = qrArea,
            onQrChange = { qrArea = it; area = null; areaError = null },
            onScan = { startScan(DailyLogQrTarget.AREA) },
            onValidate = { validateArea(qrArea) },
            loading = validatingTarget == DailyLogQrTarget.AREA,
            error = areaError,
            validated = area != null
        ) {
            qrScannerForTarget(DailyLogQrTarget.AREA)
            area?.let {
                Text("Área validada localmente", color = MaterialTheme.colorScheme.primary)
                Text("Descripción: ${it.descripcion}")
            }
        }

        Text("Datos de la bitácora", style = MaterialTheme.typography.titleMedium)

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            tonalElevation = if (gpsLocation != null) 3.dp else 1.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Ubicación GPS obligatoria", style = MaterialTheme.typography.titleMedium)
                gpsLocation?.let {
                    Text("GPS disponible", color = MaterialTheme.colorScheme.primary)
                    Text("Latitud: ${it.latitude}")
                    Text("Longitud: ${it.longitude}")
                    Text("Precisión: ${it.accuracy} m")
                    Text("Proveedor: ${it.provider}")
                } ?: Text("GPS no disponible")
                if (gpsLoading) {
                    CircularProgressIndicator()
                    Text("Obteniendo ubicación actual…")
                }
                gpsError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !gpsLoading,
                    onClick = {
                        if (locationProvider.hasPermission()) obtainGps()
                        else locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    }
                ) {
                    Text(if (gpsLocation == null) "Obtener GPS" else "Actualizar GPS")
                }
                if (!locationProvider.isLocationEnabled()) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        }
                    ) {
                        Text("Abrir configuración de ubicación")
                    }
                }
            }
        }

        if (citizenEventType == null) OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = horaEntrada,
            onValueChange = { horaEntrada = it },
            label = { Text("Hora de entrada (minutos Unix, opcional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        if (citizenEventType == null) OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = horaSalida,
            onValueChange = { horaSalida = it },
            label = { Text("Hora de salida (minutos Unix, opcional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        if (citizenEventType == null) OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = tipoAnotacion,
            onValueChange = { tipoAnotacion = it },
            label = { Text("tipo_anotacion") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        if (isSatellite) {
            Text("Seguimiento satelital", style = MaterialTheme.typography.titleMedium)
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { satelliteObjectMenuExpanded = true }
                ) {
                    Text(
                        selectedSatelliteObject?.let {
                            "${it.nombre} · ${it.tipo_objeto}"
                        } ?: "Seleccionar objeto monitoreado"
                    )
                }
                DropdownMenu(
                    expanded = satelliteObjectMenuExpanded,
                    onDismissRequest = { satelliteObjectMenuExpanded = false }
                ) {
                    satelliteObjects.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${option.nombre}\n" +
                                        "${option.departamento_provincia.orEmpty()} · " +
                                        option.municipio_localidad.orEmpty()
                                )
                            },
                            onClick = {
                                selectedSatelliteObject = option
                                satelliteObjectMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { trackingTypeMenuExpanded = true }
                ) {
                    Text(selectedTrackingType ?: "Seleccionar tipo de seguimiento")
                }
                DropdownMenu(
                    expanded = trackingTypeMenuExpanded,
                    onDismissRequest = { trackingTypeMenuExpanded = false }
                ) {
                    satelliteTrackingTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = {
                                selectedTrackingType = type
                                trackingTypeMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = observaciones,
            onValueChange = { observaciones = it },
            label = {
                Text(
                    when {
                        citizenEventType == null -> "observaciones"
                        citizenEventType.requiresTextEvidence() ->
                            "Evidencia de texto obligatoria"
                        else -> "Evidencia de texto (opcional)"
                    }
                )
            },
            minLines = 3
        )

        Text(
            "Evidencia, duración y orden no aplican a la bitácora diaria; se mantienen en el flujo de evidencia.",
            style = MaterialTheme.typography.bodySmall
        )

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = canCreate && state !is CreateBitacoraState.Loading,
            onClick = { createDailyLog() }
        ) {
            Text(SAVE_DAILY_LOG_LABEL)
        }

        if (!canCreate) {
            Text(
                text = "Falta: ${missingCreateRequirements.joinToString("; ")}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        when (val currentState = state) {
            CreateBitacoraState.Idle -> Text("Estado: pendiente")
            CreateBitacoraState.Loading -> {
                CircularProgressIndicator()
                Text("Estado: creando bitacora diaria")
            }
            is CreateBitacoraState.Success -> {
                Text(
                    text = "Bitácora guardada localmente",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Estado: sincronizada")
                Text("id_bitacora: ${currentState.idBitacora}")
                Text("ID local: ${currentState.localId}")
                EvidencePanel(
                    repository,
                    currentState.localId,
                    currentState.idBitacora,
                    currentState.areaId,
                    showQuickAudioCapture = citizenEventType?.allowsAudioEvidence() == true
                )
            }
            is CreateBitacoraState.Pending -> {
                Text(
                    text = "Bitácora guardada localmente",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(currentState.message)
                Text("local_id: ${currentState.localId}")
                Text("estado: PENDIENTE")
                EvidencePanel(
                    repository,
                    currentState.localId,
                    null,
                    currentState.areaId,
                    showQuickAudioCapture = citizenEventType?.allowsAudioEvidence() == true
                )
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
                SyncState.Error(e.message ?: "No fue posible ejecutar la sincronización")
            }
        }
    }

    fun refreshCatalogs() {
        state = SyncState.Loading
        scope.launch {
            val result = repository.syncReferenceCatalogs()
            state = if (result.success) {
                SyncState.Ready(repository.getSyncSummary(), null)
            } else {
                SyncState.Error(result.error ?: "No fue posible actualizar los catálogos")
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

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            enabled = state !is SyncState.Loading,
            onClick = { refreshCatalogs() }
        ) {
            Text("Actualizar catálogos")
        }

        when (val currentState = state) {
            SyncState.Idle -> Text("Estado: pendiente")
            SyncState.Loading -> {
                CircularProgressIndicator()
                Text("Estado: trabajando")
            }
            is SyncState.Ready -> {
                Text("total pendientes: ${currentState.summary.pendientes}")
                Text("bitácoras pendientes: ${currentState.summary.bitacorasPendientes}")
                Text("evidencias pendientes: ${currentState.summary.evidenciasPendientes}")
                Text("enrolamientos pendientes: ${currentState.summary.enrolamientosPendientes}")
                Text("total sincronizados: ${currentState.summary.sincronizados}")
                Text("total con error: ${currentState.summary.errores}")
                Text("participantes locales: ${currentState.summary.catalogParticipants}")
                Text("áreas locales: ${currentState.summary.catalogAreas}")
                Text("relaciones empleado-área: ${currentState.summary.catalogAssignments}")
                Text(
                    "última actualización de catálogos: " +
                        (currentState.summary.catalogLastSyncMillis?.let {
                            java.text.DateFormat.getDateTimeInstance()
                                .format(java.util.Date(it))
                        } ?: "nunca descargado")
                )
                currentState.summary.catalogLastError?.let {
                    Text("estado de catálogos: $it", color = MaterialTheme.colorScheme.error)
                }

                currentState.lastRun?.let {
                    Spacer(Modifier.height(4.dp))
                    Text("Ultima sincronizacion")
                    Text("revisados: ${it.revisados}")
                    Text("sincronizados: ${it.sincronizados}")
                    Text("errores: ${it.errores}")
                    Text("errores reintentables: ${it.erroresReintentables}")
                    it.mensajes.forEach { message ->
                        Text(message, color = MaterialTheme.colorScheme.error)
                    }
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
private fun QrValidationSection(
    title: String,
    scanText: String,
    qr: String,
    onQrChange: (String) -> Unit,
    onScan: () -> Unit,
    onValidate: () -> Unit,
    loading: Boolean,
    error: String?,
    validated: Boolean,
    result: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = if (validated) 3.dp else 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = qr,
                onValueChange = onQrChange,
                label = { Text("QR $title (ingreso manual)") },
                singleLine = true
            )
            Button(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = onScan) {
                Text(scanText)
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !loading, onClick = onValidate) {
                Text("Validar QR manual")
            }
            if (loading) {
                CircularProgressIndicator()
                Text("Validando $title…")
            }
            result()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun ParticipanteOut.nombreCompleto(): String =
    listOfNotNull(nombre?.takeIf { it.isNotBlank() }, apellido?.takeIf { it.isNotBlank() })
        .joinToString(" ")
        .ifBlank { "Sin nombre registrado" }

private fun Throwable.httpDetail(): String? {
    if (this !is HttpException) return null
    val body = response()?.errorBody()?.string().orEmpty()
    return runCatching { JSONObject(body).optString("detail").takeIf { it.isNotBlank() } }
        .getOrNull()
}

@Composable
private fun QrScanner(
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
    Text("Buscando código QR…", color = MaterialTheme.colorScheme.primary)

    AndroidView(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp),
        factory = { viewContext ->
            val previewView = PreviewView(viewContext).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(viewContext)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
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
                                errorMessage = errorMessage,
                                mainExecutor = ContextCompat.getMainExecutor(viewContext)
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
                    Log.e(TAG_QR_SCANNER, "No fue posible abrir la camara", e)
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
    errorMessage: String,
    mainExecutor: java.util.concurrent.Executor
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
        .addOnSuccessListener(mainExecutor) { barcodes ->
            val qrValue = barcodes
                .asSequence()
                .mapNotNull { it.rawValue?.trim() }
                .firstOrNull { it.isNotEmpty() }
            if (qrValue != null && hasScanned.compareAndSet(false, true)) {
                Log.i(TAG_QR_SCANNER, "Código QR detectado")
                onQrScanned(qrValue)
            }
        }
        .addOnFailureListener(mainExecutor) { e ->
            Log.e(TAG_QR_SCANNER, "Error leyendo QR", e)
            if (hasScanned.compareAndSet(false, true)) {
                onError(errorMessage)
            }
        }
        .addOnCompleteListener(mainExecutor) {
            isProcessing.set(false)
            imageProxy.close()
        }
}
