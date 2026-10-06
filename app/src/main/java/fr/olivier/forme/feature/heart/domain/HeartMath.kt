package fr.olivier.forme.feature.heart.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

enum class Sex(val label: String) { MALE("Homme"), FEMALE("Femme") }

/** Where a session's heart rate comes from. */
enum class HeartSource(val label: String) { BLE("montre en direct"), HEALTH_CONNECT("Health Connect"), MIXED("direct + Health Connect") }

/** Where a session's calories come from. */
enum class KcalSource(val label: String) { WATCH("mesurées par la montre"), ESTIMATE("estimation") }

object HeartZones {
    /** Lower bounds of zones 1..5, in % of the maximum heart rate. */
    val LOWER_PERCENT = intArrayOf(50, 60, 70, 80, 90)
    val LABELS = listOf("Z1 récupération", "Z2 endurance", "Z3 tempo", "Z4 seuil", "Z5 maximum")

    /** Tanaka: 208 − 0.7 × age (more accurate than 220 − age after 40). */
    fun hrMaxFromAge(age: Int): Int = (208 - 0.7 * age).roundToInt()

    fun age(birthYear: Int, currentYear: Int): Int = (currentYear - birthYear).coerceIn(10, 100)

    /** 0 = below zone 1 (under 50 %), else 1..5. */
    fun zoneOf(bpm: Int, hrMax: Int): Int {
        if (bpm <= 0 || hrMax <= 0) return 0
        val percent = bpm * 100.0 / hrMax
        return LOWER_PERCENT.indexOfLast { percent >= it } + 1
    }

    /** Heart rate range of [zone] (1..5): lower bound inclusive, upper bound exclusive. */
    fun bounds(zone: Int, hrMax: Int): IntRange {
        val low = (hrMax * LOWER_PERCENT[zone - 1] / 100.0).roundToInt()
        val high = if (zone == 5) hrMax else (hrMax * LOWER_PERCENT[zone] / 100.0).roundToInt() - 1
        return low..high
    }
}

/**
 * Heart rate of a session resampled on a fixed grid: one value per [intervalSec] seconds from [startedAt], each the
 * mean of the measurements in that slot; 0 marks a slot without data. Stored as "72,75,0,80" (about 3 KB per hour).
 */
data class HeartSeries(val startedAt: Instant, val intervalSec: Int, val bpm: List<Int>) {

    fun encode(): String = bpm.joinToString(",")

    fun timeOf(index: Int): Instant = startedAt.plusSeconds(index.toLong() * intervalSec)

    /** Values measured between [from] and [to] (inclusive), gaps excluded. */
    fun valuesBetween(from: Instant, to: Instant): List<Int> = bpm.indices
        .filter { i -> val t = timeOf(i); !t.isBefore(from) && !t.isAfter(to) }
        .map { bpm[it] }
        .filter { it > 0 }

    companion object {
        const val DEFAULT_INTERVAL_SEC = 5

        fun decode(startedAt: Instant, intervalSec: Int, text: String): HeartSeries =
            HeartSeries(startedAt, intervalSec, if (text.isBlank()) emptyList() else text.split(',').map { it.trim().toIntOrNull() ?: 0 })

        /** Resamples raw measurements taken between [from] and [to]; measurements outside the window are ignored. */
        fun fromSamples(
            samples: List<Pair<Instant, Int>>,
            from: Instant,
            to: Instant,
            intervalSec: Int = DEFAULT_INTERVAL_SEC,
        ): HeartSeries {
            val slots = ((Duration.between(from, to).seconds + intervalSec - 1) / intervalSec).toInt().coerceAtLeast(0)
            val sums = LongArray(slots)
            val counts = IntArray(slots)
            samples.forEach { (time, value) ->
                if (value <= 0 || time.isBefore(from) || !time.isBefore(to)) return@forEach
                val slot = (Duration.between(from, time).seconds / intervalSec).toInt()
                if (slot in 0 until slots) {
                    sums[slot] += value.toLong()
                    counts[slot]++
                }
            }
            return HeartSeries(from, intervalSec, List(slots) { if (counts[it] == 0) 0 else (sums[it].toDouble() / counts[it]).roundToInt() })
        }
    }
}

/** Personal data needed for zones and calorie estimates. */
data class HeartProfile(val age: Int?, val sex: Sex?, val weightKg: Double?, val hrMaxOverride: Int?) {
    /** The maximum heart rate used for zones: the user's value if set, else Tanaka from the age; null if unknown. */
    val hrMax: Int? = hrMaxOverride ?: age?.let(HeartZones::hrMaxFromAge)
}

data class HeartSummary(
    val avgBpm: Int,
    val minBpm: Int,
    val maxBpm: Int,
    /** Seconds spent in zones 1..5 (index 0 = zone 1). */
    val zoneSeconds: List<Int>,
    val hrMax: Int?,
    val estimatedKcal: Double?,
    val measuredSeconds: Int,
) {
    /** The highest zone reached for at least [minSeconds]; 0 if none. */
    fun highestZoneHeld(minSeconds: Int = 60): Int = zoneSeconds.indexOfLast { it >= minSeconds } + 1

    companion object {
        /** Null when the series holds no measurement. */
        fun of(series: HeartSeries, profile: HeartProfile): HeartSummary? {
            val values = series.bpm.filter { it > 0 }
            if (values.isEmpty()) return null
            val hrMax = profile.hrMax
            val zones = IntArray(5)
            if (hrMax != null) values.forEach { v -> HeartZones.zoneOf(v, hrMax).takeIf { it > 0 }?.let { zones[it - 1] += series.intervalSec } }
            return HeartSummary(
                avgBpm = values.average().roundToInt(),
                minBpm = values.min(),
                maxBpm = values.max(),
                zoneSeconds = zones.toList(),
                hrMax = hrMax,
                estimatedKcal = Calories.keytel(values, series.intervalSec, profile),
                measuredSeconds = values.size * series.intervalSec,
            )
        }
    }
}

object Calories {
    /**
     * Below this share of the heart-rate estimate, the watch's figure is not a workout measure: outside a workout,
     * Mi Fitness only writes a few active calories now and then (2 kcal for a 1-hour session was seen).
     */
    const val WATCH_MIN_SHARE = 0.5

    /** The watch's calories when plausible, else the estimate. */
    fun pick(watchKcal: Double?, estimatedKcal: Double?): Pair<Double, KcalSource>? = when {
        watchKcal != null && watchKcal > 0 && (estimatedKcal == null || watchKcal >= estimatedKcal * WATCH_MIN_SHARE) ->
            watchKcal to KcalSource.WATCH
        estimatedKcal != null -> estimatedKcal to KcalSource.ESTIMATE
        else -> null
    }

    /**
     * Keytel et al. (2005) energy expenditure from heart rate, weight, age and sex, summed over each slot.
     * Null when a needed profile value is missing. Valid for exercise heart rates; slots giving a negative rate count 0.
     */
    fun keytel(values: List<Int>, intervalSec: Int, profile: HeartProfile): Double? {
        val weight = profile.weightKg ?: return null
        val age = profile.age ?: return null
        val sex = profile.sex ?: return null
        val minutes = intervalSec / 60.0
        return values.sumOf { hr ->
            val kjPerMin = when (sex) {
                Sex.MALE -> -55.0969 + 0.6309 * hr + 0.1988 * weight + 0.2017 * age
                Sex.FEMALE -> -20.4022 + 0.4472 * hr - 0.1263 * weight + 0.074 * age
            }
            (kjPerMin / 4.184).coerceAtLeast(0.0) * minutes
        }
    }
}

object HrMaxCheck {
    /**
     * A new maximum to suggest when the session reached a higher heart rate than the one used for zones. The series
     * is already averaged over 5 s slots, so a single sensor spike cannot trigger it.
     */
    fun suggestion(summary: HeartSummary): Int? {
        val current = summary.hrMax ?: return null
        return summary.maxBpm.takeIf { it > current }
    }
}
