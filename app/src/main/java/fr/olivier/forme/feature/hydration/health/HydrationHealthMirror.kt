package fr.olivier.forme.feature.hydration.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Volume
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.core.di.ApplicationScope
import fr.olivier.forme.feature.hydration.data.HydrationSettingsRepository
import fr.olivier.forme.feature.hydration.data.WaterIntakeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Told when an intake is written, changed or deleted (V2.29), to mirror it in Health Connect. None by default: the
 * repositories built by hand (tests) mirror nothing.
 */
interface IntakeMirror {
    fun upsert(intake: WaterIntakeEntity) {}
    fun delete(intakeId: Long) {}

    companion object {
        val NONE = object : IntakeMirror {}
    }
}

/**
 * Writes each intake to Health Connect as a HydrationRecord (V2.29), only when "Enregistrer l'hydratation dans Health
 * Connect" is on and the write permission is granted: the volume counted (volume × coefficient), not the raw volume.
 * The record's client id is "water-{id}": a change replaces it (newer version), a deletion removes it. Reading from
 * Health Connect is not done. Off the caller's path (application scope); a failure is logged, never shown.
 */
@Singleton
class HydrationHealthMirror @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: HydrationSettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : IntakeMirror {

    fun available(): Boolean = HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun granted(): Boolean = available() &&
        WRITE_PERMISSION in HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()

    private suspend fun enabled(): Boolean = settings.current().healthConnectWrite && granted()

    override fun upsert(intake: WaterIntakeEntity) {
        scope.launch {
            runCatching {
                if (!enabled()) return@runCatching
                val client = HealthConnectClient.getOrCreate(context)
                val record = record(intake)
                if (record == null) {
                    client.deleteRecords(HydrationRecord::class, emptyList(), listOf(clientId(intake.id)))
                } else {
                    client.insertRecords(listOf(record)) // same client id, newer version: replaced
                }
            }.onFailure { Log.e("Forme", "Health Connect hydration write failed", it) }
        }
    }

    override fun delete(intakeId: Long) {
        scope.launch {
            runCatching {
                if (!enabled()) return@runCatching
                HealthConnectClient.getOrCreate(context).deleteRecords(HydrationRecord::class, emptyList(), listOf(clientId(intakeId)))
            }.onFailure { Log.e("Forme", "Health Connect hydration delete failed", it) }
        }
    }

    companion object {
        val WRITE_PERMISSION: String = HealthPermission.getWritePermission(HydrationRecord::class)

        fun clientId(intakeId: Long) = "water-$intakeId"

        /**
         * The record of an intake: the volume counted over one minute from its time (Health Connect wants an
         * interval). Null when nothing is counted (a 0 % drink): nothing to write.
         */
        fun record(intake: WaterIntakeEntity, zone: ZoneId = ZoneId.systemDefault(), version: Long = System.currentTimeMillis()): HydrationRecord? {
            val counted = intake.volumeMl * intake.coefficientApplied
            if (counted <= 0.0) return null
            val offset = zone.rules.getOffset(intake.timestamp)
            return HydrationRecord(
                startTime = intake.timestamp,
                startZoneOffset = offset,
                endTime = intake.timestamp.plus(Duration.ofMinutes(1)),
                endZoneOffset = offset,
                volume = Volume.milliliters(counted),
                metadata = Metadata.manualEntry(clientRecordId = clientId(intake.id), clientRecordVersion = version),
            )
        }
    }
}
