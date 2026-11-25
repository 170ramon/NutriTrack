package com.example.nutritrack

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.nutritrack.data.*
import com.example.nutritrack.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AiSection(store: Store, onSave: (Meal) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<AiFood>>(emptyList()) }
    var notes by remember { mutableStateOf("") }
    var scale by remember { mutableStateOf(1.0) }
    val photo = remember { File(ctx.cacheDir, "images").also { it.mkdirs() }.let { File(it, "photo.jpg") } }

    fun ask(block: suspend (String) -> AiResult) {
        val key = store.apiKey()
        if (key.isBlank()) { err = "Primero pega tu clave de API en la pestaña Perfil."; return }
        busy = true; err = ""
        scope.launch {
            try {
                val r = block(key)
                items = r.items; notes = r.notes; scale = 1.0
                if (r.items.isEmpty()) err = "No pude identificar alimentos."
            } catch (e: Exception) { err = e.message ?: "Error desconocido" }
            busy = false
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) ask { k -> AiClient.analyzePhoto(k, photo) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) ask { k ->
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openInputStream(uri)?.use { input -> photo.outputStream().use { input.copyTo(it) } }
            }
            AiClient.analyzePhoto(k, photo)
        }
    }

    Text("Búsqueda con IA", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(text, { text = it }, label = { Text("Ej: 2 tacos al pastor, 200 g arroz con pollo") },
        modifier = Modifier.fillMaxWidth())
    Button(enabled = !busy && text.isNotBlank(), onClick = { ask { k -> AiClient.analyzeText(k, text) } }) { Text("Buscar con IA") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled = !busy, onClick = {
            camera.launch(FileProvider.getUriForFile(ctx, "com.example.nutritrack.fileprovider", photo))
        }) { Text("Tomar foto") }
        OutlinedButton(enabled = !busy, onClick = { gallery.launch("image/*") }) { Text("Desde galería") }
    }
    if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Analizando…") }
    if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
    if (items.isNotEmpty()) {
        Text("Resultado estimado", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Porción:")
            listOf(0.5, 1.0, 1.5, 2.0).forEach { s -> Choice("×$s", scale == s, { scale = s }, Modifier.weight(1f)) }
        }
        items.forEach { it0 ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${it0.name} (~${(it0.grams * scale).toInt()} g)")
                    Text("${(it0.kcal * scale).toInt()} kcal · P ${(it0.p * scale).toInt()} · C ${(it0.c * scale).toInt()} · G ${(it0.f * scale).toInt()}",
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { items = items.filter { x -> x !== it0 } }) { Text("Quitar") }
            }
        }
        if (notes.isNotBlank()) Text(notes, style = MaterialTheme.typography.bodySmall)
        Button(onClick = {
            val now = System.currentTimeMillis()
            items.forEachIndexed { i, x ->
                onSave(Meal(now + i, now + i, "${x.name} ~${(x.grams * scale).toInt()} g (IA)",
                    x.kcal * scale, x.p * scale, x.c * scale, x.f * scale))
            }
            items = emptyList(); text = ""
        }) { Text("Agregar todo a hoy") }
        Text("Son estimaciones: ajusta la porción si hace falta.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ApiKeyField(store: Store) {
    var key by remember { mutableStateOf(store.apiKey()) }
    var saved by remember { mutableStateOf(false) }
    Text("Clave de API de Anthropic (para la IA)", style = MaterialTheme.typography.titleMedium)
    Text("Se guarda solo en este teléfono. No la subas a GitHub ni la compartas. Cada consulta con IA tiene un costo en tu cuenta de Anthropic.",
        style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(key, { key = it; saved = false }, label = { Text("Clave (sk-ant-...)") },
        visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(onClick = { store.saveApiKey(key.trim()); saved = true }) { Text("Guardar clave") }
    if (saved) Text("Clave guardada.", color = MaterialTheme.colorScheme.primary)
}

private data class DayStat(val date: LocalDate, val kcal: Double, val p: Double, val c: Double, val f: Double, val water: Int, val logged: Boolean)

private fun goalLabel(g: Goal) = when (g) {
    Goal.LOSE_FAT -> "bajar grasa"; Goal.LOSE_WEIGHT -> "bajar de peso"; Goal.MAINTAIN -> "mantener peso"
    Goal.GAIN_WEIGHT -> "subir de peso"; Goal.GAIN_MUSCLE -> "ganar masa muscular"
}

@Composable
fun ProgressScreen(store: Store, version: Int) {
    val scope = rememberCoroutineScope()
    val prof = remember(version) { store.profile() ?: DEFAULT_PROFILE }
    val t = remember(version) { CalorieCalculator.targets(prof) }
    val meals = remember(version) { store.meals() }
    val water = remember(version) { store.water() }
    val today = LocalDate.now()
    val stats = (6 downTo 0).map { n ->
        val d = today.minusDays(n.toLong())
        val dm = meals.filter { it.ts.toDate() == d }
        DayStat(d, dm.sumOf { it.kcal }, dm.sumOf { it.p }, dm.sumOf { it.c }, dm.sumOf { it.f },
            water.filter { it.ts.toDate() == d }.sumOf { it.ml }, dm.isNotEmpty())
    }
    val logged = stats.filter { it.logged }
    val n = logged.size
    val avgK = if (n > 0) logged.sumOf { it.kcal } / n else 0.0
    val avgP = if (n > 0) logged.sumOf { it.p } / n else 0.0
    val avgW = if (n > 0) logged.sumOf { it.water } / n else 0
    val onTarget = logged.count { it.kcal in t.kcal * 0.9..t.kcal * 1.1 }
    val waterGoal = prof.weightKg * 35
    val fmt = DateTimeFormatter.ofPattern("EEE dd", Locale("es"))

    val tips = mutableListOf<String>()
    if (n < 4) tips += "Registra al menos 5 de los 7 días para que las estadísticas sean confiables."
    if (n > 0) {
        if (avgP < t.proteinG * 0.9) tips += "Tu proteína promedio (${avgP.toInt()} g) está bajo tu meta (${t.proteinG} g). Reparte la proteína en cada comida."
        val lose = prof.goal == Goal.LOSE_FAT || prof.goal == Goal.LOSE_WEIGHT
        val gain = prof.goal == Goal.GAIN_WEIGHT || prof.goal == Goal.GAIN_MUSCLE
        if (lose && avgK > t.kcal * 1.1) tips += "Tu promedio (${avgK.toInt()} kcal) supera tu meta (${t.kcal}). Revisa porciones, bebidas y aceites."
        if (gain && avgK < t.kcal * 0.9) tips += "Tu promedio (${avgK.toInt()} kcal) está bajo tu meta (${t.kcal}). Suma una comida o colación extra."
        if (avgW < waterGoal * 0.7) tips += "Tomas poca agua (${avgW} ml de ${waterGoal.toInt()} ml). Usa los botones de agua después de cada comida."
        if (tips.isEmpty()) tips += "Vas muy bien: mantén la constancia y pésate una vez por semana."
    }

    var advice by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf("") }

    Page {
        Text("Progreso (últimos 7 días)", style = MaterialTheme.typography.headlineSmall)
        Text("Objetivo: ${goalLabel(prof.goal)} · Meta ${t.kcal} kcal · P ${t.proteinG} g")
        stats.forEach { s ->
            Text("${s.date.format(fmt)}: ${if (s.logged) "${s.kcal.toInt()} kcal · P ${s.p.toInt()} g · agua ${s.water} ml" else "sin registro"}",
                style = MaterialTheme.typography.bodySmall)
            LinearProgressIndicator(progress = { (s.kcal / t.kcal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        HorizontalDivider()
        Text("Promedios (días con registro)", style = MaterialTheme.typography.titleMedium)
        Text("Calorías ${avgK.toInt()} kcal · Proteína ${avgP.toInt()} g (${"%.1f".format(Locale.US, avgP / prof.weightKg)} g/kg) · Agua $avgW ml")
        Text("Días dentro de tu meta (±10%): $onTarget de $n")
        HorizontalDivider()
        Text("Recomendaciones rápidas", style = MaterialTheme.typography.titleMedium)
        tips.forEach { Text("• $it") }
        HorizontalDivider()
        Text("Análisis con IA", style = MaterialTheme.typography.titleMedium)
        Button(enabled = !busy && n > 0, onClick = {
            val key = store.apiKey()
            if (key.isBlank()) { err = "Primero pega tu clave de API en la pestaña Perfil."; return@Button }
            busy = true; err = ""
            val summary = "Objetivo: ${goalLabel(prof.goal)}. ${prof.sex}, ${prof.age} años, ${prof.heightCm.toInt()} cm, ${prof.weightKg} kg, actividad ${prof.activity}. " +
                "Meta diaria: ${t.kcal} kcal, P ${t.proteinG} g, C ${t.carbsG} g, G ${t.fatG} g, agua ${waterGoal.toInt()} ml. Últimos 7 días: " +
                stats.joinToString("; ") { "${it.date}: " + if (it.logged) "${it.kcal.toInt()} kcal P${it.p.toInt()} C${it.c.toInt()} G${it.f.toInt()} agua ${it.water}" else "sin registro" }
            scope.launch {
                try { advice = AiClient.advice(key, summary) } catch (e: Exception) { err = e.message ?: "Error" }
                busy = false
            }
        }) { Text("Pedir análisis y recomendaciones") }
        if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Analizando…") }
        if (err.isNotEmpty()) Text(err, color = MaterialTheme.colorScheme.error)
        if (advice.isNotEmpty()) Text(advice)
        Text("Orientación general: no sustituye el consejo de un profesional de la salud.", style = MaterialTheme.typography.bodySmall)
    }
}
