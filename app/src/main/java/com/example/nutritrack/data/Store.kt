package com.example.nutritrack.data

import android.content.Context
import com.example.nutritrack.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class Meal(val id: Long, val ts: Long, val name: String, val kcal: Double, val p: Double, val c: Double, val f: Double)
data class WaterLog(val ts: Long, val ml: Int)

val DEFAULT_PROFILE = Profile(Sex.MALE, 30, 175.0, 80.0, Activity.MODERATE, Goal.LOSE_FAT)

fun Long.toDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** Guarda todo en el propio teléfono (SharedPreferences + JSON). */
class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("nutritrack", Context.MODE_PRIVATE)

    fun meals(): List<Meal> {
        val a = JSONArray(sp.getString("meals", "[]"))
        return (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Meal(o.getLong("id"), o.getLong("ts"), o.getString("name"),
                o.getDouble("kcal"), o.getDouble("p"), o.getDouble("c"), o.getDouble("f"))
        }
    }
    private fun saveMeals(l: List<Meal>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("id", it.id).put("ts", it.ts).put("name", it.name)
            .put("kcal", it.kcal).put("p", it.p).put("c", it.c).put("f", it.f)) }
        sp.edit().putString("meals", a.toString()).apply()
    }
    fun addMeal(m: Meal) = saveMeals(meals() + m)
    fun deleteMeal(id: Long) = saveMeals(meals().filter { it.id != id })

    fun water(): List<WaterLog> {
        val a = JSONArray(sp.getString("water", "[]"))
        return (0 until a.length()).map { val o = a.getJSONObject(it); WaterLog(o.getLong("ts"), o.getInt("ml")) }
    }
    private fun saveWater(l: List<WaterLog>) {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("ts", it.ts).put("ml", it.ml)) }
        sp.edit().putString("water", a.toString()).apply()
    }
    fun addWater(ml: Int) = saveWater(water() + WaterLog(System.currentTimeMillis(), ml))
    fun undoWater() = saveWater(water().dropLast(1))

    fun apiKey(): String = sp.getString("apikey", "") ?: ""
    fun saveApiKey(k: String) = sp.edit().putString("apikey", k).apply()

    fun profile(): Profile? {
        val s = sp.getString("profile", null) ?: return null
        val o = JSONObject(s)
        return Profile(Sex.valueOf(o.getString("sex")), o.getInt("age"), o.getDouble("h"), o.getDouble("w"),
            Activity.valueOf(o.getString("act")), Goal.valueOf(o.getString("goal")))
    }
    fun saveProfile(p: Profile) {
        val o = JSONObject().put("sex", p.sex.name).put("age", p.age).put("h", p.heightCm)
            .put("w", p.weightKg).put("act", p.activity.name).put("goal", p.goal.name)
        sp.edit().putString("profile", o.toString()).apply()
    }
}
