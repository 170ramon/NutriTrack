package com.example.nutritrack

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.example.nutritrack.data.*
import com.example.nutritrack.domain.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hc = HealthConnectManager(this)
        val store = Store(this)
        setContent { MaterialTheme { App(hc, store) } }
    }
}

private fun String.num(): Double? = replace(',', '.').toDoubleOrNull()

@Composable
fun Page(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp), content = content
)

@Composable
fun Choice(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier.fillMaxWidth()) {
    if (selected) Button(onClick = onClick, modifier = modifier) { Text(label) }
    else OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
}

@Composable
fun Bar(label: String, value: Double, goal: Double, unit: String) {
    Text("$label: ${value.toInt()} / ${goal.toInt()} $unit")
    LinearProgressIndicator(progress = { (value / goal).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
}

@Composable
fun App(hc: HealthConnectManager, store: Store) {
    var tab by remember { mutableStateOf(0) }
    var version by remember { mutableStateOf(0) }
    val bump: () -> Unit = { version++ }
    Scaffold(bottomBar = {
        NavigationBar {
            listOf("Hoy", "Agregar", "Historial", "Perfil").forEachIndexed { i, n ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = {}, label = { Text(n) })
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> TodayScreen(hc, store, version, bump)
                1 -> AddScreen(hc, store, bump)
                2 -> HistoryScreen(store, version)
                else -> ProfileScreen(store, bump)
            }
        }
    }
}

@Composable
fun TodayScreen(hc: HealthConnectManager, store: Store, version: Int, bump: () -> Unit) {
    val context = LocalContext.current
    val prof = remember(version) { store.profile() }
    val base = prof ?: DEFAULT_PROFILE
    val t = remember(version) { CalorieCalculator.targets(base) }
    val today = LocalDate.now()
    val meals = remember(version) { store.meals().filter { it.ts.toDate() == today }.sortedByDescending { it.ts } }
    val waterMl = remember(version) { store.water().filter { it.ts.toDate() == today }.sumOf { it.ml } }
    var steps by remember { mutableStateOf<Long?>(null) }
    var burned by remember { mutableStateOf<Double?>(null) }
    var status by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { res ->
        status = "Permisos concedidos: ${res.size} de ${hc.permissions.size}"
        refresh++
    }
    LaunchedEffect(refresh) {
        try {
            if (hc.isAvailable() && hc.hasAllPermissions()) { steps = hc.stepsToday(); burned = hc.activeKcalToday() }
        } catch (e: Exception) { status = "Error: ${e.message}" }
    }
    Page {
        Text("Hoy", style = MaterialTheme.typography.headlineSmall)
        if (prof == null) Text("Completa la pestaña Perfil para calcular tus metas reales.", color = MaterialTheme.colorScheme.error)
        t.warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Bar("Calorías", meals.sumOf { it.kcal }, t.kcal.toDouble(), "kcal")
        Bar("Proteína", meals.sumOf { it.p }, t.proteinG.toDouble(), "g")
        Bar("Carbohidratos", meals.sumOf { it.c }, t.carbsG.toDouble(), "g")
        Bar("Grasas", meals.sumOf { it.f }, t.fatG.toDouble(), "g")
        Bar("Agua", waterMl.toDouble(), base.weightKg * 35, "ml")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { store.addWater(250); bump() }) { Text("+250 ml") }
            OutlinedButton(onClick = { store.addWater(500); bump() }) { Text("+500 ml") }
            TextButton(onClick = { store.undoWater(); bump() }) { Text("Deshacer") }
        }
        HorizontalDivider()
        Text("Comidas de hoy", style = MaterialTheme.typography.titleMedium)
        if (meals.isEmpty()) Text("Aún no has registrado comidas hoy.")
        meals.forEach { m ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.name)
                    Text("${m.kcal.toInt()} kcal · P ${m.p.toInt()} · C ${m.c.toInt()} · G ${m.f.toInt()}", style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { store.deleteMeal(m.id); bump() }) { Text("Borrar") }
            }
        }
        HorizontalDivider()
        Text("Actividad (Health Connect)", style = MaterialTheme.typography.titleMedium)
        if (!hc.isAvailable()) Text("Instala o actualiza Health Connect desde Google Play.")
        else {
            Text("Pasos hoy: ${steps ?: "—"}  ·  Calorías activas: ${burned?.toInt() ?: "—"} kcal")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    try { launcher.launch(hc.permissions) } catch (e: Exception) { status = "Error: ${e.message}" }
                }) { Text("Conectar") }
                OutlinedButton(onClick = { refresh++ }) { Text("Actualizar") }
                OutlinedButton(onClick = {
                    try { context.startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) } catch (e: Exception) { status = "Error: ${e.message}" }
                }) { Text("Ajustes") }
            }
        }
        if (status.isNotEmpty()) Text(status, color = MaterialTheme.colorScheme.primary)
        Text("Las cifras son estimaciones y no sustituyen el consejo de un profesional de la salud.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AddScreen(hc: HealthConnectManager, store: Store, bump: () -> Unit) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Food?>(null) }
    var grams by remember { mutableStateOf("100") }
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }

    fun save(m: Meal) {
        store.addMeal(m); bump(); msg = "Guardado: ${m.name}"
        scope.launch {
            try { if (hc.isAvailable() && hc.hasAllPermissions()) hc.writeMeal(m.name, m.kcal, m.p, m.c, m.f) } catch (_: Exception) {}
        }
    }
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Page {
        Text("Agregar comida", style = MaterialTheme.typography.headlineSmall)
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
        OutlinedTextField(query, { query = it; selected = null }, label = { Text("Buscar alimento") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (query.isNotBlank() && selected == null) {
            FOODS.filter { it.name.contains(query, ignoreCase = true) }.take(6).forEach { food ->
                TextButton(onClick = { selected = food }) { Text(food.name) }
            }
        }
        selected?.let { food ->
            val g = grams.num() ?: 0.0
            val k = g / 100.0
            Text(food.name, style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(grams, { grams = it }, label = { Text("Cantidad (g o ml)") }, keyboardOptions = dec, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("${(food.kcal * k).toInt()} kcal · P ${(food.p * k).toInt()} · C ${(food.c * k).toInt()} · G ${(food.f * k).toInt()}")
            Button(enabled = g > 0, onClick = {
                val now = System.currentTimeMillis()
                save(Meal(now, now, "${food.name} ${g.toInt()} g", food.kcal * k, food.p * k, food.c * k, food.f * k))
                selected = null; query = ""
            }) { Text("Agregar a hoy") }
        }
        HorizontalDivider()
        Text("O ingresa los valores a mano", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(kcal, { kcal = it }, label = { Text("Calorías (kcal)") }, keyboardOptions = dec, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(p, { p = it }, label = { Text("Proteína (g)") }, keyboardOptions = dec, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(c, { c = it }, label = { Text("Carbohidratos (g)") }, keyboardOptions = dec, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(f, { f = it }, label = { Text("Grasas (g)") }, keyboardOptions = dec, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(enabled = (kcal.num() ?: 0.0) > 0, onClick = {
            val now = System.currentTimeMillis()
            save(Meal(now, now, name.ifBlank { "Comida" }, kcal.num() ?: 0.0, p.num() ?: 0.0, c.num() ?: 0.0, f.num() ?: 0.0))
            name = ""; kcal = ""; p = ""; c = ""; f = ""
        }) { Text("Guardar") }
    }
}

@Composable
fun HistoryScreen(store: Store, version: Int) {
    val meals = remember(version) { store.meals() }
    val water = remember(version) { store.water() }
    val goal = remember(version) { CalorieCalculator.targets(store.profile() ?: DEFAULT_PROFILE).kcal }
    val days = (meals.map { it.ts.toDate() } + water.map { it.ts.toDate() }).distinct().sortedDescending().take(60)
    val fmt = DateTimeFormatter.ofPattern("EEEE dd/MM/yyyy", Locale("es"))
    Page {
        Text("Historial", style = MaterialTheme.typography.headlineSmall)
        if (days.isEmpty()) Text("Aún no hay registros.")
        days.forEach { d ->
            val dm = meals.filter { it.ts.toDate() == d }
            val w = water.filter { it.ts.toDate() == d }.sumOf { it.ml }
            Text(d.format(fmt), style = MaterialTheme.typography.titleMedium)
            Text("${dm.sumOf { it.kcal }.toInt()} / $goal kcal · P ${dm.sumOf { it.p }.toInt()} · C ${dm.sumOf { it.c }.toInt()} · G ${dm.sumOf { it.f }.toInt()} · Agua $w ml")
            dm.sortedBy { it.ts }.forEach { Text("• ${it.name} (${it.kcal.toInt()} kcal)", style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider()
        }
    }
}

@Composable
fun ProfileScreen(store: Store, bump: () -> Unit) {
    val saved = remember { store.profile() ?: DEFAULT_PROFILE }
    var sex by remember { mutableStateOf(saved.sex) }
    var age by remember { mutableStateOf(saved.age.toString()) }
    var height by remember { mutableStateOf(saved.heightCm.toString()) }
    var weight by remember { mutableStateOf(saved.weightKg.toString()) }
    var act by remember { mutableStateOf(saved.activity) }
    var goal by remember { mutableStateOf(saved.goal) }
    var msg by remember { mutableStateOf("") }
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val actNames = mapOf(Activity.SEDENTARY to "Sedentario", Activity.LIGHT to "Ligero (1–3 días/sem)",
        Activity.MODERATE to "Moderado (3–5 días/sem)", Activity.HIGH to "Alto (6–7 días/sem)")
    val goalNames = mapOf(Goal.LOSE_FAT to "Bajar grasa", Goal.LOSE_WEIGHT to "Bajar de peso", Goal.MAINTAIN to "Mantener peso",
        Goal.GAIN_WEIGHT to "Subir de peso", Goal.GAIN_MUSCLE to "Ganar masa muscular")
    Page {
        Text("Perfil y objetivo", style = MaterialTheme.typography.headlineSmall)
        Text("Solo para personas mayores de 18 años.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Choice("Hombre", sex == Sex.MALE, { sex = Sex.MALE }, Modifier.weight(1f))
            Choice("Mujer", sex == Sex.FEMALE, { sex = Sex.FEMALE }, Modifier.weight(1f))
        }
        OutlinedTextField(age, { age = it }, label = { Text("Edad") }, keyboardOptions = dec, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(height, { height = it }, label = { Text("Estatura (cm)") }, keyboardOptions = dec, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(weight, { weight = it }, label = { Text("Peso (kg)") }, keyboardOptions = dec, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("Nivel de actividad", style = MaterialTheme.typography.titleMedium)
        actNames.forEach { (k, v) -> Choice(v, act == k, { act = k }) }
        Text("Objetivo", style = MaterialTheme.typography.titleMedium)
        goalNames.forEach { (k, v) -> Choice(v, goal == k, { goal = k }) }
        Button(onClick = {
            val a = age.num()?.toInt(); val h = height.num(); val w = weight.num()
            if (a == null || a < 18 || a > 100) msg = "La edad debe estar entre 18 y 100."
            else if (h == null || h < 120 || h > 230) msg = "Revisa la estatura (120–230 cm)."
            else if (w == null || w < 30 || w > 300) msg = "Revisa el peso (30–300 kg)."
            else {
                val pr = Profile(sex, a, h, w, act, goal)
                store.saveProfile(pr); bump()
                val t = CalorieCalculator.targets(pr)
                msg = "Guardado. Meta: ${t.kcal} kcal · P ${t.proteinG} g · C ${t.carbsG} g · G ${t.fatG} g" + (t.warning?.let { "\n$it" } ?: "")
            }
        }) { Text("Guardar perfil") }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.primary)
        Text("Actualiza tu peso cada semana para que las metas se mantengan al día.", style = MaterialTheme.typography.bodySmall)
    }
}
