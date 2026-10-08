package fr.olivier.forme.feature.nutrition.domain

import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Protein of one journal entry, as seen by the advice. */
data class MealProtein(val date: LocalDate, val mealType: MealType, val proteinG: Double, val isWhey: Boolean)

/** A meal not eaten yet today, with what it usually brings. */
data class UpcomingMeal(val type: MealType, val usualProteinG: Double)

data class ProteinPlan(
    /** What is left to reach the target; ≤ 0 when reached. */
    val missingG: Double,
    val upcoming: List<UpcomingMeal>,
    /** Whey doses advised on top of the upcoming meals (0..[ProteinAdvice.MAX_DOSES] minus doses taken). */
    val doses: Int,
    val proteinPerDoseG: Double?,
    /** Still missing after the upcoming meals and the advised doses (> 0 when the dose cap is reached). */
    val shortAfterG: Double,
) {
    val expectedFromMealsG: Double get() = upcoming.sumOf { it.usualProteinG }
    val reached: Boolean get() = missingG <= 0
}

/**
 * Daily protein advice: what is missing, what the meals still to come usually bring (average of the last
 * [LOOKBACK_DAYS] days), and how many whey doses fill the rest, at most [MAX_DOSES] a day.
 */
object ProteinAdvice {
    const val LOOKBACK_DAYS = 14L
    const val MAX_DOSES = 2
    /** A meal counts as a habit when logged on at least this many days of the look-back window. */
    const val MIN_OCCURRENCES = 3
    /** Gaps up to this many grams are not worth a dose. */
    const val TOLERANCE_G = 5.0

    /** After these times, a main meal not logged yet is considered skipped. */
    private val lastCall = mapOf(
        MealType.BREAKFAST to LocalTime.of(11, 0),
        MealType.LUNCH to LocalTime.of(15, 0),
        MealType.SNACK to LocalTime.of(18, 30),
        MealType.DINNER to LocalTime.of(22, 30),
    )

    /** Usual protein of each main meal over [history] (whey and extra snacks excluded, days with the meal only). */
    fun usualProtein(history: List<MealProtein>): Map<MealType, Double> =
        history.filter { !it.isWhey && it.mealType in lastCall }
            .groupBy { it.mealType }
            .mapNotNull { (type, entries) ->
                val perDay = entries.groupBy { it.date }.values.map { day -> day.sumOf { it.proteinG } }
                if (perDay.size < MIN_OCCURRENCES) null else type to perDay.average()
            }
            .toMap()

    /** Main meals with nothing logged yet today (whey aside) and whose time has not passed. */
    fun upcoming(today: List<MealProtein>, usual: Map<MealType, Double>, now: LocalTime): List<UpcomingMeal> {
        val eaten = today.filter { !it.isWhey }.map { it.mealType }.toSet()
        return lastCall.filter { (type, until) -> type !in eaten && now.isBefore(until) }
            .mapNotNull { (type, _) -> usual[type]?.let { UpcomingMeal(type, it) } }
    }

    fun plan(
        consumedG: Double,
        targetG: Double,
        upcoming: List<UpcomingMeal>,
        proteinPerDoseG: Double?,
        dosesTaken: Int,
    ): ProteinPlan {
        val missing = targetG - consumedG
        val gap = missing - upcoming.sumOf { it.usualProteinG }
        val allowed = (MAX_DOSES - dosesTaken).coerceAtLeast(0)
        val doses = when {
            missing <= 0 || gap <= TOLERANCE_G || proteinPerDoseG == null || proteinPerDoseG <= 0 -> 0
            else -> ceil((gap - TOLERANCE_G) / proteinPerDoseG).toInt().coerceAtMost(allowed)
        }
        val shortAfter = (gap - doses * (proteinPerDoseG ?: 0.0)).coerceAtLeast(0.0)
        return ProteinPlan(missing, upcoming, doses, proteinPerDoseG, if (shortAfter > TOLERANCE_G) shortAfter else 0.0)
    }

    /** One sentence for the journal, e.g. "Il te manque 62 g. Ton dîner habituel en apporte ~35 g → 1 dose de whey (24 g)." */
    fun message(plan: ProteinPlan): String {
        if (plan.reached) return "Objectif protéines atteint."
        val (meals, whey) = parts(plan)
        val sentence = buildString {
            append("Il te manque ${plan.missingG.roundToInt()} g")
            meals?.let { append(" : ").append(it) }
            when {
                whey != null -> append(" → ").append(whey)
                meals != null && plan.shortAfterG == 0.0 -> append(", ça devrait suffire")
            }
            append(".")
        }
        return listOfNotNull(sentence, rest(plan)).joinToString(" ")
    }

    /**
     * What follows the headline in the journal (V2.15: the headline, a quip, says how much is missing): the usual
     * meals still to come, the whey doses, what is left to find. Null when there is nothing to add.
     */
    fun detail(plan: ProteinPlan): String? {
        if (plan.reached) return null
        val (meals, whey) = parts(plan)
        val first = when {
            meals != null -> buildString {
                append(meals.replaceFirstChar { it.uppercase() })
                when {
                    whey != null -> append(" → ").append(whey)
                    plan.shortAfterG == 0.0 -> append(", ça devrait suffire")
                }
                append(".")
            }
            whey != null -> "À prévoir : $whey."
            else -> null
        }
        return listOfNotNull(first, rest(plan)).joinToString(" ").ifEmpty { null }
    }

    private fun parts(plan: ProteinPlan): Pair<String?, String?> {
        val meals = when (plan.upcoming.size) {
            0 -> null
            1 -> "ton ${plan.upcoming[0].type.label.lowercase()} habituel apporte ~${plan.expectedFromMealsG.roundToInt()} g"
            else -> "tes repas à venir (${plan.upcoming.joinToString(", ") { it.type.label.lowercase() }}) " +
                "apportent ~${plan.expectedFromMealsG.roundToInt()} g"
        }
        val whey = plan.doses.takeIf { it > 0 }?.let { n ->
            "$n dose${if (n > 1) "s" else ""} de whey (${(n * plan.proteinPerDoseG!!).roundToInt()} g)"
        }
        return meals to whey
    }

    private fun rest(plan: ProteinPlan): String? = plan.shortAfterG.takeIf { it > 0 }?.let {
        "Encore ~${it.roundToInt()} g à trouver : skyr, fromage blanc, œufs, viande ou poisson."
    }
}
