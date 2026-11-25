package com.example.nutritrack.domain

import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }
enum class Goal { LOSE_FAT, LOSE_WEIGHT, MAINTAIN, GAIN_WEIGHT, GAIN_MUSCLE }
enum class Activity(val factor: Double) { SEDENTARY(1.2), LIGHT(1.375), MODERATE(1.55), HIGH(1.725) }

data class Profile(val sex: Sex, val age: Int, val heightCm: Double, val weightKg: Double,
                   val activity: Activity, val goal: Goal)

data class Targets(val kcal: Int, val proteinG: Int, val carbsG: Int, val fatG: Int, val warning: String?)

object CalorieCalculator {
    // Mínimos de seguridad: nunca recomendar por debajo sin supervisión profesional
    private const val MIN_KCAL_MALE = 1500
    private const val MIN_KCAL_FEMALE = 1200
    private const val MAX_DEFICIT = 0.25 // máx. 25% de déficit

    /** Mifflin-St Jeor */
    fun bmr(p: Profile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        return if (p.sex == Sex.MALE) base + 5 else base - 161
    }

    fun targets(p: Profile): Targets {
        val tdee = bmr(p) * p.activity.factor
        val adj = when (p.goal) {
            Goal.LOSE_FAT -> -0.20
            Goal.LOSE_WEIGHT -> -0.15
            Goal.MAINTAIN -> 0.0
            Goal.GAIN_WEIGHT -> 0.10
            Goal.GAIN_MUSCLE -> 0.08
        }.coerceAtLeast(-MAX_DEFICIT)

        var kcal = tdee * (1 + adj)
        val floor = if (p.sex == Sex.MALE) MIN_KCAL_MALE else MIN_KCAL_FEMALE
        var warning: String? = null
        if (kcal < floor) {
            kcal = floor.toDouble()
            warning = "Tu meta fue ajustada al mínimo seguro ($floor kcal). Consulta a un profesional de la salud."
        }

        val proteinPerKg = when (p.goal) {
            Goal.LOSE_FAT, Goal.GAIN_MUSCLE -> 2.0
            else -> 1.6
        }
        val protein = p.weightKg * proteinPerKg
        val fat = kcal * 0.25 / 9
        val carbs = ((kcal - protein * 4 - fat * 9) / 4).coerceAtLeast(0.0)
        return Targets(kcal.roundToInt(), protein.roundToInt(), carbs.roundToInt(), fat.roundToInt(), warning)
    }
}
