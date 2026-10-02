package fr.olivier.forme.feature.bodyweight.domain

import java.time.LocalDate

data class WeightPoint(val date: LocalDate, val weightKg: Double)

object MovingAverage {
    const val WINDOW_DAYS = 7L

    /**
     * For each weigh-in date d, mean of the weigh-ins dated within [d - 6 days, d].
     * Works with any frequency (daily or weekly): a weekly weigh-in averages only itself.
     */
    fun sevenDays(points: List<WeightPoint>): List<WeightPoint> {
        val sorted = points.sortedBy { it.date }
        return sorted.map { point ->
            val from = point.date.minusDays(WINDOW_DAYS - 1)
            val window = sorted.filter { it.date in from..point.date }
            WeightPoint(point.date, window.map { it.weightKg }.average())
        }
    }
}
