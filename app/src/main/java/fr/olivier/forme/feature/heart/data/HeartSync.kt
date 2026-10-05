package fr.olivier.forme.feature.heart.data

import fr.olivier.forme.feature.heart.domain.HeartMerge
import fr.olivier.forme.feature.heart.domain.HeartSeries
import fr.olivier.forme.feature.heart.domain.KcalSource
import fr.olivier.forme.feature.sport.data.SessionDao
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

enum class SyncOutcome {
    /** Nothing more to expect: watch calories found, Health Connect unusable, or too late. */
    DONE,
    /** Mi Fitness may still write data: try again later. */
    RETRY,
}

/**
 * Completes a finished session with what the watch app wrote to Health Connect (V2.1 lot 3): heart rate fills the
 * gaps of the live curve (or makes the whole curve when nothing was received live), and the watch's active calories
 * replace the estimate. Mi Fitness syncs late, so this runs again until calories show up or [GIVE_UP_AFTER].
 */
@Singleton
class HeartSync @Inject constructor(
    private val healthConnect: HealthConnectSource,
    private val heart: HeartRepository,
    private val sessionDao: SessionDao,
    private val clock: Clock,
) {
    suspend fun sync(sessionId: Long): SyncOutcome {
        val session = sessionDao.getSession(sessionId) ?: return SyncOutcome.DONE
        val end = session.endedAt ?: return SyncOutcome.DONE
        // Past sessions keep their frozen summary: only recent ones are completed.
        if (clock.instant().isAfter(end.plus(GIVE_UP_AFTER))) return SyncOutcome.DONE
        val data = healthConnect.read(session.startedAt, end) ?: return SyncOutcome.DONE
        val existing = heart.get(sessionId)
        val watchKcal = HeartMerge.kcalWithin(data.activeKcal, session.startedAt, end).takeIf { it > 0 }
            ?: existing?.kcal?.takeIf { existing.kcalSource == KcalSource.WATCH }
        val fromHealthConnect = HeartSeries.fromSamples(data.samples, session.startedAt, end)
        val hasHeartRate = fromHealthConnect.bpm.any { it > 0 }
        when {
            hasHeartRate -> {
                val (merged, source) = HeartMerge.merge(existing?.series, fromHealthConnect)
                heart.saveSeries(sessionId, merged, source, watchKcal)
            }
            // Calories only: they need a curve to attach to (no heart rate at all means nothing to show).
            watchKcal != null && existing != null && existing.kcalSource != KcalSource.WATCH ->
                heart.saveSeries(sessionId, existing.series, existing.source, watchKcal)
        }
        return if (watchKcal != null) SyncOutcome.DONE else SyncOutcome.RETRY
    }

    companion object {
        val GIVE_UP_AFTER: Duration = Duration.ofHours(24)
    }
}
