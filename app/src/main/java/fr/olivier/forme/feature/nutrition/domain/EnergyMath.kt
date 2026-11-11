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
    const val DAILY_LIFE_FACTOR = 1.4 // = ActivityLevel.MODERATE, the default

    /** Basal metabolic rate, Mifflin-St Jeor (kcal/day). */
    fun bmr(weightKg: Double, heightCm: Int, age: Int, sex: Sex): Double {
        val base = 10 * weightKg + 6.25 * heightCm - 5 * age
        return if (sex == Sex.MALE) base + 5 else base - 161
    }

    /** Rest-day target advised for [goal], rounded to 10 kcal; null when a measure is missing. */
    fun advisedBase(
        weightKg: Double?,
        heightCm: Int?,
        age: Int?,
        sex: Sex?,
        goal: NutritionGoal?,
        activity: ActivityLevel? = null,
    ): Int? {
        if (weightKg == null || heightCm == null || age == null || sex == null || goal == null) return null
        val factor = (activity ?: ActivityLevel.MODERATE).factor
        val kcal = bmr(weightKg, heightCm, age, sex) * factor * (1 + goal.adjustment)
        return (kcal / 10).roundToInt() * 10
    }

    /** Below this age (V2.25), no calorie target nor supplement advice: a health professional is the right adviser. */
    const val MIN_AGE_FOR_TARGETS = 16

    fun isMinor(birthYear: Int?, currentYear: Int): Boolean =
        birthYear != null && currentYear - birthYear < MIN_AGE_FOR_TARGETS

    /** A personalised target under the basal metabolism deserves a (neutral) warning. */
    fun belowBmr(targetKcal: Int?, bmr: Double?): Boolean = targetKcal != null && bmr != null && targetKcal < bmr

    /** The day's target: the personalised or advised rest-day value plus the sessions' calories. */
    fun target(baseKcal: Int?, sessionKcal: Double?): KcalTarget? =
        baseKcal?.let { KcalTarget(it, (sessionKcal ?: 0.0).roundToInt()) }

    /** Rough basal metabolic rate (kcal/day) when the height is unknown: ~24 kcal per kg. */
    const val BMR_PER_KG_FALLBACK = 24.0

    /**
     * Part of a session's calories to add to the rest-day target. The heart-rate estimate (Keytel) is a gross
     * expenditure that includes the basal metabolism, already in the rest-day target: it is removed over the
     * measured time. The watch's active calories are already net.
     */
    fun netSessionKcal(kcal: Double, fromWatch: Boolean, measuredSeconds: Int, bmrPerDay: Double?): Double =
        if (fromWatch || bmrPerDay == null) kcal else (kcal - bmrPerDay * measuredSeconds / 86_400.0).coerceAtLeast(0.0)

    /** A day is "on target" within ±10 % of its calorie target. */
    fun onTarget(eatenKcal: Double, target: Int): Boolean = target > 0 && kotlin.math.abs(eatenKcal - target) <= target * 0.10

    /** Below ~0.6 g of fat per kg, hormonal health suffers: the floor of the fat target. */
    const val MIN_FAT_G_PER_KG = 0.6

    /**
     * Fat and carb targets of the day, as sports dietitians build them: fat = the goal's share of the calories
     * (never below [MIN_FAT_G_PER_KG]), carbs = what is left after protein and fat (4 / 9 / 4 kcal per g).
     * The sessions' calories thus go to carbs. Null without a calorie or protein target.
     */
    fun macroTargets(kcalTarget: Int?, proteinTargetG: Double?, weightKg: Double?, goal: NutritionGoal?): MacroTargets? {
        if (kcalTarget == null || proteinTargetG == null || kcalTarget <= 0) return null
        val share = goal?.fatShare ?: NutritionGoal.MAINTAIN.fatShare
        val fat = maxOf(kcalTarget * share / 9, (weightKg ?: 0.0) * MIN_FAT_G_PER_KG)
        val carbs = ((kcalTarget - proteinTargetG * 4 - fat * 9) / 4).coerceAtLeast(0.0)
        return MacroTargets(fatG = fat.roundToInt(), carbsG = carbs.roundToInt())
    }
}

data class MacroTargets(val fatG: Int, val carbsG: Int)
