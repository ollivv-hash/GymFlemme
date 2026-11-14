package fr.olivier.forme.feature.hydration.domain

import kotlin.math.roundToInt

/** Settings of the goal (Réglages › Hydratation). */
data class GoalSettings(
    val customEnabled: Boolean = false,
    val customMl: Int = HydrationMath.DEFAULT_CUSTOM_ML,
    val bonusEnabled: Boolean = true,
    val bonusMl: Int = HydrationMath.DEFAULT_BONUS_ML,
)

/**
 * Today's goal: [computedMl] from the weight (2 000 ml without one, [hasWeight] false), [customMl] when a goal of
 * one's own is on, [bonusMl] when a session was started today and the bonus is on.
 */
data class DailyGoal(val computedMl: Int, val hasWeight: Boolean, val customMl: Int?, val bonusMl: Int) {
    /** The goal before the session bonus. */
    val baseMl get() = customMl ?: computedMl
    val totalMl get() = baseMl + bonusMl
}

/** Indicative values only: the app says so (Réglages › Hydratation). */
object HydrationMath {
    const val ML_PER_KG = 35
    const val NO_WEIGHT_GOAL_ML = 2_000
    const val DEFAULT_CUSTOM_ML = 2_500
    const val DEFAULT_BONUS_ML = 500
    const val BONUS_MAX_ML = 1_500
    const val BONUS_STEP_ML = 250
    const val CUSTOM_MIN_ML = 500
    const val CUSTOM_MAX_ML = 6_000
    const val CUSTOM_STEP_ML = 100
    const val FREE_MIN_ML = 10
    const val FREE_MAX_ML = 2_000
    const val FREE_STEP_ML = 10

    /** Weight × 35 ml, rounded to the nearest 100 ml; 2 000 ml without a weight. */
    fun computedGoal(weightKg: Double?): Int {
        if (weightKg == null || weightKg <= 0) return NO_WEIGHT_GOAL_ML
        return (weightKg * ML_PER_KG / 100).roundToInt() * 100
    }

    fun goal(weightKg: Double?, settings: GoalSettings, sessionDay: Boolean): DailyGoal = DailyGoal(
        computedMl = computedGoal(weightKg),
        hasWeight = weightKg != null && weightKg > 0,
        customMl = if (settings.customEnabled) settings.customMl else null,
        bonusMl = if (sessionDay && settings.bonusEnabled) settings.bonusMl else 0,
    )

    /** Hydration counted for an intake. */
    fun counted(volumeMl: Int, coefficient: Double): Double = volumeMl * coefficient

    fun coerceBonus(ml: Int): Int = (ml.coerceIn(0, BONUS_MAX_ML) / BONUS_STEP_ML) * BONUS_STEP_ML
    fun coerceCustom(ml: Int): Int = ((ml.coerceIn(CUSTOM_MIN_ML, CUSTOM_MAX_ML) + CUSTOM_STEP_ML / 2) / CUSTOM_STEP_ML) * CUSTOM_STEP_ML
    fun coerceFree(ml: Int): Int = ((ml.coerceIn(FREE_MIN_ML, FREE_MAX_ML) + FREE_STEP_ML / 2) / FREE_STEP_ML).coerceAtMost(FREE_MAX_ML / FREE_STEP_ML) * FREE_STEP_ML

    /** Coefficient as set in the settings: 0 to 100 % by steps of 10. */
    fun coerceCoefficient(value: Double): Double = ((value.coerceIn(0.0, 1.0) * 10).roundToInt()) / 10.0

    /** "1,8 L", "750 ml" (below 1 L), "2 L". */
    fun liters(ml: Int): String = if (ml < 1_000) "$ml ml" else "${litersNumber(ml)} L"

    /** "1,8" (one decimal, dropped when whole). */
    fun litersNumber(ml: Int): String {
        val tenths = (ml / 100.0).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10},${tenths % 10}"
    }

    /** "1,8 litre", "2 litres" (TalkBack). */
    fun litersSpoken(ml: Int): String {
        val number = litersNumber(ml)
        return "$number litre" + if ((ml / 100.0).roundToInt() >= 20) "s" else ""
    }
}
