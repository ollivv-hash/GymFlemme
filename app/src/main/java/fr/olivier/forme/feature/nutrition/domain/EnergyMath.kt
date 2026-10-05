package fr.olivier.forme.feature.nutrition.domain

import fr.olivier.forme.feature.heart.domain.Sex
import kotlin.math.roundToInt

/** Calorie target of a day: the rest-day target plus what the day's sessions burned. */
data class KcalTarget(val baseKcal: Int, val sessionKcal: Int) {
    val totalKcal: Int get() = baseKcal + sessionKcal
}

object EnergyMath {
    /**
     * Daily life without the workouts (desk job, walking): sessions are added separately from their measured
     * calories, so the classic "moderately active" factors (1.55+) would count them twice.
     */
    const val DAILY_LIFE_FACTOR = 1.4

    /** Basal metabolic rate, Mifflin-St Jeor (kcal/day). */
    fun bmr(weightKg: Double, heightCm: Int, age: Int, sex: Sex): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return if (sex == Sex.MALE) base + 5 else base - 161
    }

    /** Rest-day target advised for [goal], rounded to 10 kcal; null when a measure is missing. */
    fun advisedBase(weightKg: Double?, heightCm: Int?, age: Int?, sex: Sex?, goal: NutritionGoal?): Int? {
        if (weightKg == null || heightCm == null || age == null || sex == null || goal == null) return null
        val kcal = bmr(weightKg, heightCm, age, sex) * DAILY_LIFE_FACTOR * (1 + goal.adjustment)
        return (kcal / 10).roundToInt() * 10
    }

    /** The day's target: the personalised or advised rest-day value plus the sessions' calories. */
    fun target(baseKcal: Int?, sessionKcal: Double?): KcalTarget? =
        baseKcal?.let { KcalTarget(it, (sessionKcal ?: 0.0).roundToInt()) }

    /** A day is "on target" within ±10 % of its calorie target. */
    fun onTarget(eatenKcal: Double, target: Int): Boolean = target > 0 && kotlin.math.abs(eatenKcal - target) <= target * 0.10
}
