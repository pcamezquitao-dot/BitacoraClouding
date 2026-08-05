package com.cactus.bitacora.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cactus.bitacora.AppEnvironment
import com.cactus.bitacora.AppScreen

private val DarkGreen = Color(0xFF1B5E20)
private val LightGreen = Color(0xFFE8F5E9)
private val InformationBlue = Color(0xFF1565C0)
private val LightBackground = Color(0xFFF5F7F5)
private val WarningYellow = Color(0xFFF9A825)

@Composable
fun BitacoraVisualTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = DarkGreen,
            onPrimary = Color.White,
            primaryContainer = LightGreen,
            onPrimaryContainer = Color(0xFF123514),
            secondary = Color(0xFF4CAF50),
            onSecondary = Color.White,
            tertiary = InformationBlue,
            onTertiary = Color.White,
            background = LightBackground,
            surface = Color.White,
            error = Color(0xFFB3261E),
            onError = Color.White
        ),
        typography = Typography(
            bodyLarge = TextStyle(fontSize = 17.sp),
            bodyMedium = TextStyle(fontSize = 16.sp),
            titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
            titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
            labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        ),
        content = content
    )
}

@Composable
internal fun MainHeader(
    environment: AppEnvironment,
    online: Boolean?,
    onChangeEnvironment: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Bitácora",
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Ambiente actual",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        environment.label,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Surface(
                    color = when (online) {
                        true -> LightGreen
                        false -> Color(0xFFFFF3CD)
                        null -> Color.White.copy(alpha = 0.9f)
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        when (online) {
                            true -> "● ONLINE"
                            false -> "● OFFLINE"
                            null -> "● Verificando"
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = when (online) {
                            true -> DarkGreen
                            false -> WarningYellow
                            null -> InformationBlue
                        },
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .semantics { contentDescription = "Cambiar ambiente" },
                onClick = onChangeEnvironment
            ) {
                Text("Cambiar ambiente", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

private data class BottomDestination(
    val screen: AppScreen,
    val symbol: String,
    val label: String
)

@Composable
internal fun MainBottomBar(
    currentScreen: AppScreen,
    onNavigate: (AppScreen) -> Unit
) {
    val destinations = listOf(
        BottomDestination(AppScreen.Health, "⌂", "Inicio"),
        BottomDestination(AppScreen.CreateDailyLog, "＋", "Crear"),
        BottomDestination(AppScreen.QueryDailyLog, "⌕", "Consultar"),
        BottomDestination(AppScreen.Sync, "↻", "Sincronizar"),
        BottomDestination(AppScreen.More, "•••", "Más")
    )
    NavigationBar(containerColor = Color.White) {
        destinations.forEach { destination ->
            NavigationBarItem(
                selected = currentScreen == destination.screen,
                onClick = { onNavigate(destination.screen) },
                icon = {
                    Text(
                        destination.symbol,
                        fontSize = 22.sp,
                        modifier = Modifier.semantics {
                            contentDescription = destination.label
                        }
                    )
                },
                label = {
                    Text(
                        destination.label,
                        fontSize = 9.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DarkGreen,
                    selectedTextColor = DarkGreen,
                    indicatorColor = LightGreen,
                    unselectedIconColor = Color(0xFF455A64),
                    unselectedTextColor = Color(0xFF455A64)
                )
            )
        }
    }
}
