package fr.olivier.forme.feature.sport.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Coming back (V2.16): a welcome after more than a week without a session, lighter loads offered after more than two
 * weeks, and a word when last week's sessions goal was missed. Never counts the missed days.
 */
object Comeback {
    /** More than this many days since the last session: "welcome back" on the home screen. */
    const val ABSENCE_DAYS = 7

    /** More than this many days: the first session back offers loads at [RESUME_SHARE]. */
    const val RESUME_DAYS = 14
    const val RESUME_SHARE = 0.8

    private fun daysBetween(last: LocalDate?, today: LocalDate): Long? = last?.let { ChronoUnit.DAYS.between(it, today) }

    /** [lastSession]: the most recent finished session (null without any): no welcome for a first session. */
    fun isBack(lastSession: LocalDate?, today: LocalDate): Boolean = (daysBetween(lastSession, today) ?: 0) > ABSENCE_DAYS

    /** [previousSession]: the session finished before the running one started. */
    fun offersLighterLoads(previousSession: LocalDate?, sessionDay: LocalDate): Boolean =
        (daysBetween(previousSession, sessionDay) ?: 0) > RESUME_DAYS

    /** [load] × [RESUME_SHARE], rounded to the exercise's nearest load step (never below one step when a load was set). */
    fun lighterLoad(load: Double, step: Double): Double {
        val target = load * RESUME_SHARE
        if (step <= 0) return target
        return (Math.round(target / step) * step).coerceAtLeast(minOf(step, load))
    }

    /**
     * Last week's sessions goal was missed, and none this week yet. Only for someone who already trained before last
     * week ([trainedBefore]), so that a newcomer is not told off.
     */
    fun missedLastWeek(sessionsLastWeek: Int, sessionsThisWeek: Int, goal: Int, trainedBefore: Boolean): Boolean =
        trainedBefore && sessionsThisWeek == 0 && sessionsLastWeek < goal
}
