package fr.olivier.forme.feature.sport.domain

import java.time.LocalDate
import kotlin.math.floor

/** Estimated one-rep max (Epley), only reliable for low and moderate reps. */
object OneRepMax {
    const val MAX_REPS = 12

    /** load × (1 + reps / 30); the load itself for a single; null beyond [MAX_REPS] reps or without load. */
    fun estimate(loadKg: Double?, reps: Int?): Double? {
        if (loadKg == null || loadKg <= 0 || reps == null || reps !in 1..MAX_REPS) return null
        return if (reps == 1) loadKg else loadKg * (1 + reps / 30.0)
    }

    fun best(sets: List<WorkSet>): Double? = sets.mapNotNull { estimate(it.loadKg, it.reps) }.maxOrNull()
}

/** An exercise whose estimated 1RM has not improved for [sessions] sessions. */
data class Plateau(
    val sessions: Int,
    val bestOneRepMaxKg: Double,
    /** Last top load minus 10 %, rounded down to the exercise's increment. */
    val resetLoadKg: Double?,
)

object PlateauDetector {
    /** Sessions in a row without beating the best estimated 1RM before a plateau is reported. */
    const val SESSIONS = 3
    const val RESET_RATIO = 0.9

    /**
     * [history] is chronological (oldest first). A session "progresses" when its best estimated 1RM beats every
     * session before it; a plateau is reported after [SESSIONS] sessions in a row that did not.
     */
    fun detect(history: List<SessionSets>, loadIncrementKg: Double): Plateau? {
        val values = history.mapNotNull { s -> OneRepMax.best(s.sets)?.let { s to it } }
        if (values.size < SESSIONS + 1) return null
        var best = values.first().second
        var lastRecord = 0
        values.forEachIndexed { i, (_, v) ->
            if (i > 0 && v > best + 1e-6) {
                best = v
                lastRecord = i
            }
        }
        val stalled = values.size - 1 - lastRecord
        if (stalled < SESSIONS) return null
        val lastTop = values.last().first.sets.mapNotNull { it.loadKg }.maxOrNull()
        return Plateau(stalled, best, lastTop?.let { roundDown(it * RESET_RATIO, loadIncrementKg) })
    }

    /** The three ways out of a plateau, as short sentences. */
    fun advice(plateau: Plateau): List<String> = listOfNotNull(
        "Change de plage de reps 2 à 3 semaines (ex. 8–10 → 12–15).",
        plateau.resetLoadKg?.let { "Repars à ${Fmt.kg(it)} (−10 %) et remonte d'un cran par séance." },
        "Passe à une variante (autre machine ou prise) avec « Remplacer ».",
    )

    private fun roundDown(kg: Double, step: Double): Double =
        if (step <= 0) kg else floor(kg / step + 1e-9) * step
}

/** Why a deload week is advised. */
data class DeloadAdvice(
    /** Complete training weeks in a row (≥ [DeloadAdvisor.STREAK_WEEKS] triggers the advice). */
    val weeksInARow: Int,
    /** Exercises currently on a plateau (≥ [DeloadAdvisor.PLATEAU_EXERCISES] triggers the advice). */
    val plateaus: Int,
) {
    val message: String
        get() {
            val reason = when {
                weeksInARow >= DeloadAdvisor.STREAK_WEEKS && plateaus >= DeloadAdvisor.PLATEAU_EXERCISES ->
                    "$weeksInARow semaines d'entraînement d'affilée et $plateaus exercices qui stagnent"
                weeksInARow >= DeloadAdvisor.STREAK_WEEKS -> "$weeksInARow semaines d'entraînement d'affilée"
                else -> "$plateaus exercices qui stagnent"
            }
            return "$reason : une semaine plus légère aide à récupérer et à repartir. " +
                "Garde tes charges, fais environ 40 % de séries en moins (2 au lieu de 3) et arrête-toi loin de l'échec."
        }
}

object DeloadAdvisor {
    const val STREAK_WEEKS = 6
    const val PLATEAU_EXERCISES = 3
    /** A week with fewer sets than this share of the previous weeks' average counts as a deload (it resets the streak). */
    const val DELOAD_RATIO = 0.6
    private const val AVERAGE_WEEKS = 4

    /**
     * Complete weeks (before [currentWeek]) in a row with at least one working set, stopping at a week without
     * training or at a lighter week (deload). [setsPerWeek] is keyed by week start (Monday).
     */
    fun trainingStreak(setsPerWeek: Map<LocalDate, Int>, currentWeek: LocalDate): Int {
        var week = currentWeek.minusWeeks(1)
        var streak = 0
        while (true) {
            val sets = setsPerWeek[week] ?: 0
            if (sets == 0) break
            val before = (1..AVERAGE_WEEKS).mapNotNull { setsPerWeek[week.minusWeeks(it.toLong())]?.takeIf { s -> s > 0 } }
            if (before.isNotEmpty() && sets < before.average() * DELOAD_RATIO) break
            streak++
            week = week.minusWeeks(1)
        }
        return streak
    }

    fun advise(setsPerWeek: Map<LocalDate, Int>, currentWeek: LocalDate, plateaus: Int): DeloadAdvice? {
        val streak = trainingStreak(setsPerWeek, currentWeek)
        return if (streak >= STREAK_WEEKS || plateaus >= PLATEAU_EXERCISES) DeloadAdvice(streak, plateaus) else null
    }
}
