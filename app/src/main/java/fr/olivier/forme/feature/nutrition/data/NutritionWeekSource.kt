package fr.olivier.forme.feature.nutrition.data

import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.nutrition.domain.DayIntake
import fr.olivier.forme.feature.nutrition.domain.EnergyMath
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.nutrition.domain.NutritionWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import fr.olivier.forme.feature.hydration.data.HydrationRepository
import fr.olivier.forme.feature.hydration.domain.HydrationMath
import javax.inject.Inject
import javax.inject.Singleton

/** Nutrition summary of a week (Week tab, AI analysis). */
@Singleton
class NutritionWeekSource @Inject constructor(
    private val nutrition: NutritionRepository,
    private val heart: HeartRepository,
    private val weights: BodyWeightRepository,
    private val clock: Clock,
    private val hydration: HydrationRepository,
) {
    /** Protein target from the current weight; calorie target of each day = rest-day target + its sessions (net). */
    fun observe(weekStart: LocalDate): Flow<NutritionWeek> = combine(
        observeFood(weekStart),
        hydration.observeDaysWithGoal(weekStart, weekStart.plusDays(6)),
    ) { food, water ->
        // V2.29: the water of the week, each day against its own goal (2 L without one).
        food.copy(
            waterDays = water.size,
            avgWaterMl = water.takeIf { it.isNotEmpty() }?.map { it.countedMl }?.average()?.let { kotlin.math.round(it).toInt() },
            waterGoalDays = water.count { it.countedMl >= (it.goalMl ?: HydrationMath.NO_WEIGHT_GOAL_ML) },
        )
    }

    private fun observeFood(weekStart: LocalDate): Flow<NutritionWeek> = combine(
        nutrition.observeRange(weekStart, weekStart.plusDays(6)),
        nutrition.profile,
        weights.observeAll().map { list -> list.maxByOrNull { it.date }?.weightKg },
        nutrition.creatineDates(),
        heart.observeRange(weekStart, weekStart.plusDays(7)),
    ) { entries, profile, weight, creatine, hearts ->
        val intakes = entries.groupBy { it.date }.map { (date, day) ->
            DayIntake(date, day.sumOf { it.proteinG }, day.sumOf { it.kcal ?: 0.0 }, day.any { it.isWhey })
        }
        val bmr = profile.bmrPerDay(weight, weekStart.year)
        val sessionKcal = hearts.groupBy { it.seriesStart.atZone(clock.zone).toLocalDate() }
            .mapValues { (_, list) -> list.netKcal(bmr) ?: 0.0 }
        val base = profile.restDayKcal(weight, weekStart.year)
        NutritionWeek.of(
            weekStart = weekStart,
            today = LocalDate.now(clock),
            intakes = intakes,
            creatineDates = creatine,
            proteinTargetG = profile.proteinTargetG(weight),
            kcalTargetOf = base?.let { b -> { d: LocalDate -> EnergyMath.target(b, sessionKcal[d])!!.totalKcal } },
        )
    }
}
