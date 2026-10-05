package fr.olivier.forme.feature.heart.domain

import java.time.Instant
import kotlin.math.roundToInt

object SetHeart {
    /** The effort happens just before a set is validated: the peak is looked for in that window. */
    const val BEFORE_SEC = 60L
    const val AFTER_SEC = 10L

    /** Highest heart rate around a set validated at [completedAt]; null without measurements there. */
    fun peak(series: HeartSeries, completedAt: Instant): Int? =
        series.valuesBetween(completedAt.minusSeconds(BEFORE_SEC), completedAt.plusSeconds(AFTER_SEC)).maxOrNull()
}

/** Time spent at zone 3 or above ("working" intensity), from the five zone durations. */
fun highZoneSeconds(zoneSeconds: List<Int>): Int = zoneSeconds.drop(2).sum()

/** One session's heart rate as needed for the weekly summary. */
data class SessionHeartLine(val avgBpm: Int, val measuredSeconds: Int, val zoneSeconds: List<Int>, val kcal: Double?)

data class HeartWeek(
    val sessions: Int,
    /** Average over all measured time (sessions weighted by their duration). */
    val avgBpm: Int?,
    val highZoneSeconds: Int,
    val kcal: Double,
) {
    companion object {
        fun of(lines: List<SessionHeartLine>): HeartWeek {
            val measured = lines.sumOf { it.measuredSeconds }
            return HeartWeek(
                sessions = lines.size,
                avgBpm = if (measured > 0) (lines.sumOf { it.avgBpm.toLong() * it.measuredSeconds }.toDouble() / measured).roundToInt() else null,
                highZoneSeconds = lines.sumOf { highZoneSeconds(it.zoneSeconds) },
                kcal = lines.sumOf { it.kcal ?: 0.0 },
            )
        }
    }
}
