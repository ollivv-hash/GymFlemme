package fr.olivier.forme.feature.heart.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HeartMergeTest {
    private val t0 = Instant.parse("2026-10-03T10:00:00Z")

    @Test
    fun liveValuesWinAndGapsAreFilled() {
        val live = HeartSeries(t0, 5, listOf(100, 0, 0, 120))
        val hc = HeartSeries(t0, 5, listOf(90, 105, 0, 118))
        val (merged, source) = HeartMerge.merge(live, hc)
        assertEquals(listOf(100, 105, 0, 120), merged.bpm)
        assertEquals(HeartSource.MIXED, source)
    }

    @Test
    fun sourcesWhenOnlyOneSideHasData() {
        val hc = HeartSeries(t0, 5, listOf(90, 95))
        assertEquals(HeartSource.HEALTH_CONNECT, HeartMerge.merge(null, hc).second)
        assertEquals(HeartSource.HEALTH_CONNECT, HeartMerge.merge(HeartSeries(t0, 5, listOf(0, 0)), hc).second)
        assertEquals(HeartSource.BLE, HeartMerge.merge(HeartSeries(t0, 5, listOf(80, 81)), HeartSeries(t0, 5, listOf(0, 0))).second)
    }

    @Test
    fun caloriesAreProratedToTheWindow() {
        val records = listOf(
            KcalInterval(t0.minusSeconds(600), t0.plusSeconds(600), 20.0), // half inside → 10
            KcalInterval(t0.plusSeconds(600), t0.plusSeconds(1200), 30.0), // fully inside → 30
            KcalInterval(t0.plusSeconds(4000), t0.plusSeconds(5000), 50.0), // outside → 0
            KcalInterval(t0.plusSeconds(60), t0.plusSeconds(60), 4.0), // instant inside → 4
        )
        assertEquals(44.0, HeartMerge.kcalWithin(records, t0, t0.plusSeconds(3600)), 1e-9)
    }

    @Test
    fun originPicking() {
        val watch = HeartMerge.WATCH_APP
        assertEquals("a", HeartMerge.pickOrigin(listOf("a", "a", "b"), setOf(watch)))
        assertEquals(watch, HeartMerge.pickOrigin(emptyList(), setOf("com.fitbit.FitbitMobile", watch)))
        assertEquals("solo", HeartMerge.pickOrigin(emptyList(), setOf("solo")))
        assertEquals(null, HeartMerge.pickOrigin(emptyList(), setOf("x", "y")))
    }
}
