package com.cactus.bitacora

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.cactus.bitacora.data.ApiConfig
import com.cactus.bitacora.data.BitacoraRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    BackendStatusScreen()
                }
            }
        }
    }
}

private sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Loading : ConnectionState
    data class Available(val status: String) : ConnectionState
    data class Unavailable(val message: String) : ConnectionState
}

@Composable
fun BackendStatusScreen() {
    val scope = rememberCoroutineScope()
    val repository = remember { BitacoraRepository() }
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

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "BitacoraClouding",
                style = MaterialTheme.typography.headlineSmall
            )

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
}
