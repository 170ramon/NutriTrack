package com.example.nutritrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
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
    val context = LocalContext.current
    val profile = remember { Profile(Sex.MALE, 30, 175.0, 80.0, Activity.MODERATE, Goal.LOSE_FAT) }
    val t = remember { CalorieCalculator.targets(profile) }
    var steps by remember { mutableStateOf<Long?>(null) }
    var burned by remember { mutableStateOf<Double?>(null) }
    var status by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }

    val launcher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { res ->
        status = "Permisos concedidos: ${res.size} de ${hc.permissions.size}"
        refresh++
    }

    LaunchedEffect(refresh) {
        try {
            if (hc.isAvailable() && hc.hasAllPermissions()) {
                steps = hc.stepsToday()
                burned = hc.activeKcalToday()
            }
        } catch (e: Exception) {
            status = "Error: ${e.message}"
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
            Button(onClick = {
                status = "Solicitando permisos…"
                try { launcher.launch(hc.permissions) }
                catch (e: Exception) { status = "Error al pedir permisos: ${e.message}" }
            }) { Text("Conectar Health Connect") }
            OutlinedButton(onClick = {
                try { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) }
                catch (e: Exception) { status = "No pude abrir los ajustes: ${e.message}" }
            }) { Text("Abrir ajustes de Health Connect") }
            Text("Pasos hoy: ${steps ?: "—"}")
            Text("Calorías activas hoy: ${burned?.toInt() ?: "—"} kcal")
        }
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.primary)
        Text(
            "Las cifras son estimaciones y no sustituyen el consejo de un profesional de la salud.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
