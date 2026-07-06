package com.cactus.bitacora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.cactus.bitacora.data.Api
import com.cactus.bitacora.data.models.*

import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AppScreen() } }
    }
}

@Composable
fun AppScreen() {
    val scope = rememberCoroutineScope()
    val api = remember { Api.create() }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var qrSupervisor by remember { mutableStateOf("") }
    var login by remember { mutableStateOf<LoginResponse?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var empleados by remember { mutableStateOf<List<EmpleadoOut>>(emptyList()) }
    var tipos by remember { mutableStateOf<List<TipoNovedadOut>>(emptyList()) }
    var empleadoSel by remember { mutableStateOf<EmpleadoOut?>(null) }
    var tipoSel by remember { mutableStateOf<TipoNovedadOut?>(null) }
    var obs by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }

    // Acciones reusables
    fun cerrarTeclado() {
        focusManager.clearFocus()
        keyboard?.hide()
    }

    fun hacerLogout() {
        cerrarTeclado()
        login = null
        empleados = emptyList()
        tipos = emptyList()
        empleadoSel = null
        tipoSel = null
        obs = ""
        msg = null
        error = null
    }

    fun guardarNovedad() {
        val sup = login?.participante ?: return
        if (empleadoSel == null || tipoSel == null || obs.isBlank()) return

        cerrarTeclado()
        error = null
        msg = null
        scope.launch {
            try {
                val r = api.crearBitacora(
                    BitacoraCreate(
                        id_supervisor = sup.id_participante,
                        id_empleado = empleadoSel!!.id_participante,
                        tipo_novedad = tipoSel!!.tipo_novedad,
                        observaciones = obs
                    )
                )
                msg = "Guardado OK. id_bitacora=${r.id_bitacora}"
                obs = ""
            } catch (e: Exception) {
                error = e.message ?: "Error"
            }
        }
    }

    Scaffold(
        // ✅ Arreglo 3: botones pegados abajo (solo cuando ya está logueado)
        bottomBar = {
            if (login != null) {
                Surface(shadowElevation = 6.dp) {
                    Column(Modifier.padding(12.dp)) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = empleadoSel != null && tipoSel != null && obs.isNotBlank(),
                            onClick = { guardarNovedad() }
                        ) { Text("Guardar novedad (ONLINE)") }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { hacerLogout() }
                        ) { Text("Salir") }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "BITACORA_5 - Registro de Novedades",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (login == null) {
                item {
                    OutlinedTextField(
                        value = qrSupervisor,
                        onValueChange = { qrSupervisor = it },
                        label = { Text("QR supervisor (identificacion_participante)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { cerrarTeclado() }
                        )
                    )
                }

                item {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            cerrarTeclado()
                            error = null
                            msg = null
                            scope.launch {
                                try {
                                    val resp = api.loginQr(QRLoginRequest(qrSupervisor))
                                    if (!resp.es_supervisor) {
                                        error = "El participante NO es supervisor"
                                        return@launch
                                    }
                                    login = resp

                                    // Cargar listas
                                    tipos = api.getTiposNovedad()
                                    empleados = api.getEmpleados(resp.participante.id_participante)

                                    // Reset selección previa por seguridad
                                    empleadoSel = null
                                    tipoSel = null
                                    obs = ""
                                } catch (e: Exception) {
                                    error = e.message ?: "Error"
                                }
                            }
                        }
                    ) { Text("Login") }
                }

            } else {
                val sup = login!!.participante

                item {
                    Text("Supervisor: ${sup.nombre} ${sup.apellido} (id=${sup.id_participante})")
                }

                item { Divider() }

                item { Text("Empleado (seleccione)") }

                items(empleados.take(10)) { e ->
                    AssistChip(
                        onClick = {
                            cerrarTeclado()
                            empleadoSel = e
                        },
                        label = { Text("${e.nombre} ${e.apellido}") }
                    )
                }

                item {
                    Text(
                        "Empleado seleccionado: " +
                                (empleadoSel?.let { "${it.nombre} ${it.apellido}" } ?: "ninguno")
                    )
                }

                item { Divider() }

                item { Text("Tipo de novedad") }

                items(tipos.take(10)) { t ->
                    AssistChip(
                        onClick = {
                            cerrarTeclado()
                            tipoSel = t
                        },
                        label = { Text("${t.tipo_novedad} - ${t.descripcion_novedad}") }
                    )
                }

                item {
                    Text("Tipo seleccionado: " + (tipoSel?.let { "${it.tipo_novedad}" } ?: "ninguno"))
                }

                item { Divider() }

                // ✅ Arreglo 2: Teclado con "Listo" y al oprimirlo cierra y libera foco
                item {
                    OutlinedTextField(
                        value = obs,
                        onValueChange = { obs = it },
                        label = { Text("Observación") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { cerrarTeclado() }
                        )
                    )
                }

                // Botón opcional para cerrar teclado (por si el celular no muestra "Listo")
                item {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { cerrarTeclado() }
                    ) { Text("Cerrar teclado") }
                }
            }

            msg?.let {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
            }

            error?.let {
                item {
                    Spacer(Modifier.height(8.dp))
                    Text("ERROR: $it", color = MaterialTheme.colorScheme.error)
                }
            }

            item { Spacer(Modifier.height(80.dp)) } // espacio para que no tape el bottomBar
        }
    }
}
