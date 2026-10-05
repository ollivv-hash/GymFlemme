package fr.olivier.forme.feature.heart.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class HeartReportTest {
    private val t0 = Instant.parse("2026-10-05T12:00:00Z")

    @Test
    fun setPeakLooksAtTheMinuteBeforeValidation() {
        // One value per 5 s: 100 for 2 min, a 150 peak at 2:30, then 110.
        val bpm = List(24) { 100 } + listOf(130, 140, 145, 150, 120, 110) + List(12) { 110 }
        val series = HeartSeries(t0, 5, bpm)
        assertEquals(150, SetHeart.peak(series, t0.plusSeconds(140))) // set validated 5 s after the peak
        assertEquals(100, SetHeart.peak(series, t0.plusSeconds(60)))
        assertNull(SetHeart.peak(series, t0.plusSeconds(3600)))
    }

    @Test
    fun weekAggregatesWeightedByMeasuredTime() {
        val week = HeartWeek.of(
            listOf(
                SessionHeartLine(avgBpm = 120, measuredSeconds = 3600, zoneSeconds = listOf(0, 600, 1200, 600, 0), kcal = 400.0),
                SessionHeartLine(avgBpm = 90, measuredSeconds = 1200, zoneSeconds = listOf(1200, 0, 0, 0, 0), kcal = null),
            ),
        )
        assertEquals(2, week.sessions)
        assertEquals(113, week.avgBpm) // (120×3600 + 90×1200) / 4800 = 112.5
        assertEquals(1800, week.highZoneSeconds)
        assertEquals(400.0, week.kcal, 0.0)
        assertNull(HeartWeek.of(emptyList()).avgBpm)
    }
}
