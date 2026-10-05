package fr.olivier.forme.feature.heart.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class HeartMathTest {
    private val t0 = Instant.parse("2026-10-03T10:00:00Z")

    @Test
    fun tanakaAndZones() {
        assertEquals(176, HeartZones.hrMaxFromAge(46)) // 208 − 32.2
        assertEquals(0, HeartZones.zoneOf(80, 176)) // 45 %
        assertEquals(1, HeartZones.zoneOf(88, 176)) // exactly 50 %
        assertEquals(3, HeartZones.zoneOf(130, 176)) // 73.9 %
        assertEquals(5, HeartZones.zoneOf(176, 176))
        assertEquals(5, HeartZones.zoneOf(190, 176))
        assertEquals(141..157, HeartZones.bounds(4, 176))
        assertEquals(158..176, HeartZones.bounds(5, 176))
    }

    @Test
    fun resamplingAveragesSlotsAndMarksGaps() {
        val samples = listOf(
            t0.plusSeconds(0) to 70, t0.plusSeconds(2) to 74, // slot 0 → 72
            t0.plusSeconds(6) to 80, // slot 1
            // slot 2 empty
            t0.plusSeconds(16) to 90, // slot 3
            t0.plusSeconds(25) to 200, // at "to": excluded
            t0.minusSeconds(1) to 200, // before: excluded
        )
        val series = HeartSeries.fromSamples(samples, t0, t0.plusSeconds(20))
        assertEquals(listOf(72, 80, 0, 90), series.bpm)
        val back = HeartSeries.decode(t0, 5, series.encode())
        assertEquals(series, back)
        assertEquals(listOf(80, 90), series.valuesBetween(t0.plusSeconds(5), t0.plusSeconds(15)))
    }

    @Test
    fun summaryZonesAndCalories() {
        // 12 measured slots of 5 s (1 min): 6 at 110 bpm (62.5 % of 176 → Z2) and 6 at 150 bpm (85 % → Z4), plus gaps.
        val series = HeartSeries(t0, 5, List(6) { 110 } + listOf(0, 0) + List(6) { 150 })
        val profile = HeartProfile(age = 46, sex = Sex.MALE, weightKg = 80.0, hrMaxOverride = null)
        val s = HeartSummary.of(series, profile)!!
        assertEquals(130, s.avgBpm)
        assertEquals(110, s.minBpm)
        assertEquals(150, s.maxBpm)
        assertEquals(listOf(0, 30, 0, 30, 0), s.zoneSeconds)
        assertEquals(60, s.measuredSeconds)
        assertEquals(4, s.highestZoneHeld(minSeconds = 30))
        // Keytel male, 80 kg, 46 y: 110 bpm → 9.437 kcal/min, 150 bpm → 15.469 kcal/min; 30 s each.
        assertEquals((9.437 + 15.469) / 2, s.estimatedKcal!!, 0.01)
        assertNull(HrMaxCheck.suggestion(s))
    }

    @Test
    fun missingProfileDataDisablesZonesAndEstimate() {
        val series = HeartSeries(t0, 5, listOf(120, 130))
        val s = HeartSummary.of(series, HeartProfile(age = null, sex = null, weightKg = 80.0, hrMaxOverride = null))!!
        assertNull(s.hrMax)
        assertNull(s.estimatedKcal)
        assertEquals(listOf(0, 0, 0, 0, 0), s.zoneSeconds)
        assertNull(HeartSummary.of(HeartSeries(t0, 5, listOf(0, 0)), HeartProfile(46, Sex.MALE, 80.0, null)))
    }

    @Test
    fun overrideWinsAndHigherMaxIsSuggested() {
        val profile = HeartProfile(age = 46, sex = Sex.MALE, weightKg = 80.0, hrMaxOverride = 170)
        assertEquals(170, profile.hrMax)
        val s = HeartSummary.of(HeartSeries(t0, 5, listOf(150, 172, 160)), profile)!!
        assertEquals(172, HrMaxCheck.suggestion(s))
    }
}
