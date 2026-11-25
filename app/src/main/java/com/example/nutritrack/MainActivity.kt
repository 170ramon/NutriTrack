package com.example.nutritrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.example.nutritrack.data.HealthConnectManager
import com.example.nutritrack.domain.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hc = HealthConnectManager(this)
        setContent { MaterialTheme { Home(hc) } }
    }
}

@Composable
fun Home(hc: HealthConnectManager) {
    // Perfil de ejemplo: luego se reemplaza por pantalla de onboarding + almacenamiento local
    val profile = remember { Profile(Sex.MALE, 30, 175.0, 80.0, Activity.MODERATE, Goal.LOSE_FAT) }
    val t = remember { CalorieCalculator.targets(profile) }
    var steps by remember { mutableStateOf<Long?>(null) }
    var burned by remember { mutableStateOf<Double?>(null) }
    var granted by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { res ->
        granted = res.containsAll(hc.permissions)
    }
    LaunchedEffect(granted) {
        if (hc.isAvailable() && (granted || hc.hasAllPermissions())) {
            steps = hc.stepsToday(); burned = hc.activeKcalToday()
        }
    }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Meta diaria", style = MaterialTheme.typography.headlineSmall)
        Text("${t.kcal} kcal · P ${t.proteinG} g · C ${t.carbsG} g · G ${t.fatG} g")
        t.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        HorizontalDivider()
        if (!hc.isAvailable()) {
            Text("Instala o actualiza Health Connect desde Google Play.")
        } else {
            Button(onClick = { launcher.launch(hc.permissions) }) { Text("Conectar Health Connect") }
            Text("Pasos hoy: ${steps ?: "—"}")
            Text("Calorías activas hoy: ${burned?.toInt() ?: "—"} kcal")
        }
        Text("Las cifras son estimaciones y no sustituyen el consejo de un profesional de la salud.",
            style = MaterialTheme.typography.bodySmall)
    }
}
