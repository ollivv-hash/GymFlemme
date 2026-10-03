package fr.olivier.forme.feature.heart.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.feature.heart.domain.KcalInterval
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass

enum class HealthConnectAvailability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }

/** Heart rate and active calories written by the watch app for a time window. */
data class HealthConnectData(val samples: List<Pair<Instant, Int>>, val activeKcal: List<KcalInterval>)

/** Read access to Health Connect, behind an interface so the sync logic can be tested without it. */
interface HealthConnectSource {
    /** Null when Health Connect is unavailable or heart rate reading is not allowed. */
    suspend fun read(from: Instant, to: Instant): HealthConnectData?
}

/** What Health Connect holds for a time window: used by the lot 0 test screen to judge data density and sources. */
data class HealthConnectProbe(
    val from: Instant,
    val to: Instant,
    val heartRateRecords: Int,
    val samples: List<Pair<Instant, Long>>,
    val heartRateOrigins: Set<String>,
    val activeKcal: Double,
    val activeRecords: Int,
    val totalKcal: Double,
    val totalRecords: Int,
    val calorieOrigins: Set<String>,
) {
    val medianIntervalSec: Long? = samples.map { it.first }.sorted().zipWithNext { a, b -> Duration.between(a, b).seconds }
        .sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
}

@Singleton
class HealthConnectReader @Inject constructor(@ApplicationContext private val context: Context) : HealthConnectSource {

    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
        HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND,
    )

    fun availability(): HealthConnectAvailability = when (HealthConnectClient.getSdkStatus(context)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
        else -> HealthConnectAvailability.UNAVAILABLE
    }

    private fun client() = HealthConnectClient.getOrCreate(context)

    suspend fun grantedPermissions(): Set<String> = client().permissionController.getGrantedPermissions()

    /** Background reading needs a recent Health Connect; reported so the test screen can show it. */
    fun backgroundReadSupported(): Boolean = runCatching {
        client().features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_IN_BACKGROUND) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
    }.getOrDefault(false)

    suspend fun probe(from: Instant, to: Instant): HealthConnectProbe {
        val granted = grantedPermissions()
        val hr = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) readAll(HeartRateRecord::class, from, to) else emptyList()
        val active = if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) readAll(ActiveCaloriesBurnedRecord::class, from, to) else emptyList()
        val total = if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted) readAll(TotalCaloriesBurnedRecord::class, from, to) else emptyList()
        return HealthConnectProbe(
            from = from,
            to = to,
            heartRateRecords = hr.size,
            samples = hr.flatMap { r -> r.samples.map { it.time to it.beatsPerMinute } }.sortedBy { it.first },
            heartRateOrigins = hr.map { it.metadata.dataOrigin.packageName }.toSet(),
            activeKcal = active.sumOf { it.energy.inKilocalories },
            activeRecords = active.size,
            totalKcal = total.sumOf { it.energy.inKilocalories },
            totalRecords = total.size,
            calorieOrigins = (active.map { it.metadata.dataOrigin.packageName } + total.map { it.metadata.dataOrigin.packageName }).toSet(),
        )
    }

    override suspend fun read(from: Instant, to: Instant): HealthConnectData? {
        if (availability() != HealthConnectAvailability.AVAILABLE) return null
        val granted = grantedPermissions()
        if (HealthPermission.getReadPermission(HeartRateRecord::class) !in granted) return null
        val samples = readAll(HeartRateRecord::class, from, to)
            .flatMap { r -> r.samples.map { it.time to it.beatsPerMinute.toInt() } }
            .sortedBy { it.first }
        val kcal = if (HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class) in granted) {
            readAll(ActiveCaloriesBurnedRecord::class, from, to).map { KcalInterval(it.startTime, it.endTime, it.energy.inKilocalories) }
        } else {
            emptyList()
        }
        return HealthConnectData(samples, kcal)
    }

    private suspend fun <T : Record> readAll(type: KClass<T>, from: Instant, to: Instant): List<T> {
        val out = mutableListOf<T>()
        var token: String? = null
        do {
            val response = client().readRecords(ReadRecordsRequest(type, TimeRangeFilter.between(from, to), pageToken = token))
            out += response.records
            token = response.pageToken
        } while (token != null)
        return out
    }
}
