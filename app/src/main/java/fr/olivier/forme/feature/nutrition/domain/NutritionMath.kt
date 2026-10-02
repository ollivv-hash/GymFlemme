package fr.olivier.forme.feature.nutrition.domain

import java.time.LocalDate
import kotlin.math.roundToInt

/** Nutritional values per 100 g; null = unknown. */
data class Per100(val proteinG: Double, val kcal: Double?, val carbsG: Double?, val fatG: Double?)

data class Macros(val proteinG: Double, val kcal: Double?, val carbsG: Double?, val fatG: Double?)

data class DayTotals(val proteinG: Double, val kcal: Double, val carbsG: Double, val fatG: Double, val entries: Int)

/** Share of energy from each macro (Atwater factors 4 / 4 / 9 kcal per g), in %. */
data class EnergySplit(val proteinPct: Int, val carbsPct: Int, val fatPct: Int)

object NutritionMath {
    fun forQuantity(per100: Per100, grams: Double): Macros {
        val f = grams / 100.0
        return Macros(per100.proteinG * f, per100.kcal?.times(f), per100.carbsG?.times(f), per100.fatG?.times(f))
    }

    fun dayTotals(entries: List<Macros>): DayTotals = DayTotals(
        proteinG = entries.sumOf { it.proteinG },
        kcal = entries.sumOf { it.kcal ?: 0.0 },
        carbsG = entries.sumOf { it.carbsG ?: 0.0 },
        fatG = entries.sumOf { it.fatG ?: 0.0 },
        entries = entries.size,
    )

    fun energySplit(totals: DayTotals): EnergySplit? {
        val p = totals.proteinG * 4
        val c = totals.carbsG * 4
        val f = totals.fatG * 9
        val sum = p + c + f
        if (sum <= 0) return null
        val pp = (p / sum * 100).roundToInt()
        val cp = (c / sum * 100).roundToInt()
        return EnergySplit(pp, cp, 100 - pp - cp)
    }

    /** Daily protein target = g/kg × last recorded body weight; null without any weigh-in. */
    fun proteinTarget(lastWeightKg: Double?, gPerKg: Double): Double? = lastWeightKg?.let { it * gPerKg }
}

/** Best moment for the single daily whey dose. */
enum class WheyMoment(val label: String) {
    DONE("Dose du jour prise"),
    AFTER_SESSION("Idéal : juste après ta séance"),
    REST_DAY("Jour de repos : au petit-déjeuner ou en collation"),
}

object WheyAdvice {
    fun moment(takenToday: Boolean, trainingToday: Boolean): WheyMoment = when {
        takenToday -> WheyMoment.DONE
        trainingToday -> WheyMoment.AFTER_SESSION
        else -> WheyMoment.REST_DAY
    }
}

object CreatineStreak {
    /**
     * Consecutive days with creatine, ending today — or yesterday when today is not taken yet,
     * so the streak is not shown as broken during the day.
     */
    fun days(takenDates: Collection<LocalDate>, today: LocalDate): Int {
        val set = takenDates.toSet()
        var day = if (today in set) today else today.minusDays(1)
        var count = 0
        while (day in set) {
            count++
            day = day.minusDays(1)
        }
        return count
    }
}
