package fr.olivier.forme.feature.heart.data

import fr.olivier.forme.feature.bodyweight.data.BodyWeightDao
import fr.olivier.forme.feature.heart.domain.HeartProfile
import fr.olivier.forme.feature.heart.domain.HeartSeries
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.heart.domain.HeartSummary
import fr.olivier.forme.feature.heart.domain.HeartZones
import fr.olivier.forme.feature.heart.domain.KcalSource
import fr.olivier.forme.feature.nutrition.data.ProfileDao
import fr.olivier.forme.feature.sport.data.SessionDao
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HeartRepository @Inject constructor(
    private val heartDao: SessionHeartDao,
    private val sessionDao: SessionDao,
    private val profileDao: ProfileDao,
    private val weightDao: BodyWeightDao,
    private val clock: Clock,
) {
    fun observe(sessionId: Long): Flow<SessionHeartEntity?> = heartDao.observe(sessionId)

    suspend fun get(sessionId: Long): SessionHeartEntity? = heartDao.get(sessionId)

    /** Age, sex, latest weight and maximum heart rate as currently set in the profile. */
    suspend fun heartProfile(): HeartProfile {
        val profile = profileDao.get()
        return HeartProfile(
            age = profile?.birthYear?.let { HeartZones.age(it, LocalDate.now(clock).year) },
            sex = profile?.sex,
            weightKg = weightDao.getLatest()?.weightKg,
            hrMaxOverride = profile?.hrMaxOverride,
        )
    }

    /**
     * Builds the session's curve from raw measurements (live Bluetooth and/or Health Connect), computes its summary
     * with the current profile and stores it. Calories measured by the watch ([watchKcal]) win over the estimate.
     * Returns null, storing nothing, when no measurement falls within the session.
     */
    suspend fun saveSession(
        sessionId: Long,
        samples: List<Pair<Instant, Int>>,
        source: HeartSource,
        watchKcal: Double? = null,
    ): SessionHeartEntity? {
        val session = sessionDao.getSession(sessionId) ?: return null
        val series = HeartSeries.fromSamples(samples, session.startedAt, session.endedAt ?: clock.instant())
        val summary = HeartSummary.of(series, heartProfile()) ?: return null
        val (kcal, kcalSource) = when {
            watchKcal != null && watchKcal > 0 -> watchKcal to KcalSource.WATCH
            summary.estimatedKcal != null -> summary.estimatedKcal to KcalSource.ESTIMATE
            else -> null to null
        }
        val z = summary.zoneSeconds
        val entity = SessionHeartEntity(
            sessionId = sessionId,
            source = source,
            seriesStart = series.startedAt,
            intervalSec = series.intervalSec,
            bpmSeries = series.encode(),
            avgBpm = summary.avgBpm,
            minBpm = summary.minBpm,
            maxBpm = summary.maxBpm,
            hrMax = summary.hrMax,
            zone1Sec = z[0],
            zone2Sec = z[1],
            zone3Sec = z[2],
            zone4Sec = z[3],
            zone5Sec = z[4],
            kcal = kcal,
            kcalSource = kcalSource,
        )
        heartDao.upsert(entity)
        return entity
    }

    /** Session calories of the day (for the nutrition balance). */
    fun observeDay(date: LocalDate): Flow<List<SessionHeartEntity>> {
        val zone = clock.zone
        return heartDao.observeStartedBetween(
            date.atStartOfDay(zone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }
}
