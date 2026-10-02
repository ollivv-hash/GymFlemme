package fr.olivier.forme.feature.bodyweight.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class MovingAverageTest {
    private val d0 = LocalDate.of(2026, 10, 1)

    @Test
    fun `daily weigh-ins average over the last 7 days`() {
        val points = (0L..9L).map { WeightPoint(d0.plusDays(it), 80.0 + it) }
        val avg = MovingAverage.sevenDays(points)
        assertEquals(80.0, avg[0].weightKg, 1e-9)
        assertEquals(81.5, avg[3].weightKg, 1e-9) // 80..83
        assertEquals(86.0, avg[9].weightKg, 1e-9) // 83..89
    }

    @Test
    fun `weekly weigh-ins only average themselves`() {
        val points = listOf(WeightPoint(d0, 80.0), WeightPoint(d0.plusDays(7), 79.0))
        assertEquals(listOf(80.0, 79.0), MovingAverage.sevenDays(points).map { it.weightKg })
    }

    @Test
    fun `window includes the day six days before`() {
        val points = listOf(WeightPoint(d0, 80.0), WeightPoint(d0.plusDays(6), 82.0))
        assertEquals(81.0, MovingAverage.sevenDays(points)[1].weightKg, 1e-9)
    }

    @Test
    fun `unsorted input is handled`() {
        val points = listOf(WeightPoint(d0.plusDays(1), 81.0), WeightPoint(d0, 79.0))
        assertEquals(listOf(79.0, 80.0), MovingAverage.sevenDays(points).map { it.weightKg })
    }
}
