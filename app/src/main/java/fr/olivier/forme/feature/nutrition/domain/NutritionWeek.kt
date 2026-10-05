package fr.olivier.forme.feature.nutrition.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** What was eaten on one logged day. */
data class DayIntake(val date: LocalDate, val proteinG: Double, val kcal: Double, val whey: Boolean)

/** Nutrition of a week (Monday → Sunday, up to today for the current week). Averages cover logged days only. */
data class NutritionWeek(
    /** Days of the week already started (7 for a past week). */
    val elapsedDays: Int,
    val loggedDays: Int,
    val avgProteinG: Double?,
    /** Logged days reaching the protein target; null without a target (no weigh-in). */
    val proteinTargetDays: Int?,
    val avgKcal: Double?,
    /** Logged days within ±10 % of their calorie target; null without a calorie goal. */
    val kcalTargetDays: Int?,
    val wheyDays: Int,
    val creatineDays: Int,
) {
    companion object {
        fun of(
            weekStart: LocalDate,
            today: LocalDate,
            intakes: List<DayIntake>,
            creatineDates: Collection<LocalDate>,
            proteinTargetG: Double?,
            kcalTargetOf: ((LocalDate) -> Int)?,
        ): NutritionWeek {
            val end = weekStart.plusDays(6)
            val elapsed = (ChronoUnit.DAYS.between(weekStart, minOf(today, end)) + 1).toInt().coerceIn(0, 7)
            val days = intakes.filter { it.date in weekStart..end }
            return NutritionWeek(
                elapsedDays = elapsed,
                loggedDays = days.size,
                avgProteinG = days.takeIf { it.isNotEmpty() }?.map { it.proteinG }?.average(),
                proteinTargetDays = proteinTargetG?.let { t -> days.count { it.proteinG >= t } },
                avgKcal = days.takeIf { it.isNotEmpty() }?.map { it.kcal }?.average(),
                kcalTargetDays = kcalTargetOf?.let { f -> days.count { EnergyMath.onTarget(it.kcal, f(it.date)) } },
                wheyDays = days.count { it.whey },
                creatineDays = creatineDates.count { it in weekStart..end },
            )
        }
    }
}
