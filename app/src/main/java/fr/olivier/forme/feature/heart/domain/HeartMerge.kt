package fr.olivier.forme.feature.heart.domain

import java.time.Duration
import java.time.Instant

/** A calorie record from Health Connect: energy spent between [start] and [end], written by app [origin]. */
data class KcalInterval(val start: Instant, val end: Instant, val kcal: Double, val origin: String? = null)

/** A heart rate measurement from Health Connect, written by app [origin]. */
data class SourcedSample(val time: Instant, val bpm: Int, val origin: String)

object HeartMerge {
    /** Package of Mi Fitness, the Xiaomi watch app writing to Health Connect. */
    const val WATCH_APP = "com.xiaomi.wearable"

    /**
     * Several apps may write to Health Connect (watch app, Fitbit, the phone's own step counter…): mixing them would
     * count the same effort twice. Keeps one source: the app that wrote the heart rate of the session (the most
     * samples), else the watch app, else the only calorie source if there is just one; null = none usable.
     */
    fun pickOrigin(heartRateOrigins: List<String>, kcalOrigins: Set<String>): String? =
        heartRateOrigins.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: WATCH_APP.takeIf { it in kcalOrigins }
            ?: kcalOrigins.singleOrNull()

    /**
     * Fills the gaps of the live (Bluetooth) curve with Health Connect values, slot by slot; live values always win.
     * Both series must be on the same grid. Returns the merged series and its source.
     */
    fun merge(live: HeartSeries?, healthConnect: HeartSeries): Pair<HeartSeries, HeartSource> {
        if (live == null || live.bpm.none { it > 0 }) return healthConnect to HeartSource.HEALTH_CONNECT
        require(live.startedAt == healthConnect.startedAt && live.intervalSec == healthConnect.intervalSec) { "different grids" }
        var filled = 0
        val size = maxOf(live.bpm.size, healthConnect.bpm.size)
        val merged = List(size) { i ->
            val l = live.bpm.getOrElse(i) { 0 }
            if (l > 0) {
                l
            } else {
                healthConnect.bpm.getOrElse(i) { 0 }.also { if (it > 0) filled++ }
            }
        }
        return live.copy(bpm = merged) to (if (filled > 0) HeartSource.MIXED else HeartSource.BLE)
    }

    /** Calories spent within [from, to]: each record counts for the share of its duration inside the window. */
    fun kcalWithin(records: List<KcalInterval>, from: Instant, to: Instant): Double = records.sumOf { r ->
        val total = Duration.between(r.start, r.end).toMillis()
        if (total <= 0) {
            // Instantaneous record: all of it or nothing.
            if (!r.start.isBefore(from) && !r.start.isAfter(to)) r.kcal else 0.0
        } else {
            val inside = Duration.between(maxOf(r.start, from), minOf(r.end, to)).toMillis()
            if (inside <= 0) 0.0 else r.kcal * inside / total
        }
    }
}
