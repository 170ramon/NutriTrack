package com.example.nutritrack.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.*

class HealthConnectManager(private val context: Context) {

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
    )

    fun isAvailable() = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasAllPermissions() = client.permissionController.getGrantedPermissions().containsAll(permissions)

    private fun today() = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant() to Instant.now()

    /** Glori Fit Pro (u otro wearable) → si sincroniza con Health Connect, sus datos aparecen aquí */
    suspend fun stepsToday(): Long {
        val (s, e) = today()
        val r = client.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(s, e)))
        return r[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    suspend fun activeKcalToday(): Double {
        val (s, e) = today()
        val r = client.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), TimeRangeFilter.between(s, e)))
        return r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
    }

    suspend fun latestWeightKg(): Double? {
        val now = Instant.now()
        val res = client.readRecords(ReadRecordsRequest(WeightRecord::class,
            TimeRangeFilter.between(now.minus(Duration.ofDays(60)), now), ascendingOrder = false, pageSize = 1))
        return res.records.firstOrNull()?.weight?.inKilograms
    }

    /** Escribe una comida en Health Connect para que otras apps de salud la vean */
    suspend fun writeMeal(name: String, kcal: Double, proteinG: Double, carbsG: Double, fatG: Double) {
        val now = Instant.now()
        val off = ZoneId.systemDefault().rules.getOffset(now)
        client.insertRecords(listOf(NutritionRecord(
            startTime = now.minusSeconds(60), startZoneOffset = off, endTime = now, endZoneOffset = off,
            name = name, energy = Energy.kilocalories(kcal), protein = Mass.grams(proteinG),
            totalCarbohydrate = Mass.grams(carbsG), totalFat = Mass.grams(fatG), metadata = Metadata()
        )))
    }
}
