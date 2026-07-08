package com.cactus.bitacora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.data.ApiConfig
import com.cactus.bitacora.data.BitacoraRepository
import com.cactus.bitacora.data.CreateBitacoraResult
import com.cactus.bitacora.data.SyncRunResult
import com.cactus.bitacora.data.SyncSummary
import com.cactus.bitacora.data.models.BitacoraDiariaCreate
import com.cactus.bitacora.data.models.BitacoraDiariaOut
import kotlinx.coroutines.launch
import java.util.UUID

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
    Sync
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

            when (currentScreen) {
                AppScreen.Health -> BackendStatusScreen(repository)
                AppScreen.CreateDailyLog -> CrearBitacoraDiariaScreen(repository)
                AppScreen.QueryDailyLog -> ConsultarBitacoraScreen(repository)
                AppScreen.Sync -> SyncScreen(repository)
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
    val scope = rememberCoroutineScope()
    var idEmpleado by remember { mutableStateOf("") }
    var idSupervisor by remember { mutableStateOf("") }
    var tipoAnotacion by remember { mutableStateOf("") }
    var observaciones by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<CreateBitacoraState>(CreateBitacoraState.Idle) }

    fun createDailyLog() {
        val empleado = idEmpleado.toIntOrNull()
        val supervisor = idSupervisor.toIntOrNull()
        val tipo = tipoAnotacion.toIntOrNull()

        if (empleado == null) {
            state = CreateBitacoraState.Error("id_empleado debe ser numerico")
            return
        }

        if (idSupervisor.isNotBlank() && supervisor == null) {
            state = CreateBitacoraState.Error("id_supervisor debe ser numerico")
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
                        id_empleado = empleado,
                        id_supervisor = supervisor,
                        tipo_anotacion = tipo,
                        observaciones = observaciones.ifBlank { null },
                        client_uuid = UUID.randomUUID().toString()
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

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = idEmpleado,
            onValueChange = { idEmpleado = it },
            label = { Text("id_empleado") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = idSupervisor,
            onValueChange = { idSupervisor = it },
            label = { Text("id_supervisor") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

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
            enabled = state !is CreateBitacoraState.Loading,
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
