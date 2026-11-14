package fr.olivier.forme.feature.badges.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** The monthly challenges (V2.19). Ids are stored: never rename them. */
enum class Challenge {
    TWELVE_STRIKES,
    ROWER_OF_THE_MONTH,
    RECORD_HUNTER,
    NO_BLANK_WEEK,
    CURIOUS,
    PROTEIN_DATE,
    TRIPLE_SESSION,
    /** V2.29: the hydration goal reached 20 days in the month. */
    HYDRATED,
    ;

    companion object {
        fun byId(id: String): Challenge? = entries.firstOrNull { it.name == id }
    }
}

/** What decides which challenges may be drawn: a rower in the catalogue, meals last month, the rotation's size. */
data class ChallengeEligibility(val hasRower: Boolean, val mealsLastMonth: Boolean, val rotationSize: Int, val waterDaysLastMonth: Int = 0)

/** Progress of a challenge in its month: [value] out of [goal], and the day it was met (null while not met). */
data class ChallengeProgress(val value: Double, val goal: Double, val completedOn: LocalDate?)

object Challenges {
    fun eligible(e: ChallengeEligibility): List<Challenge> = Challenge.entries.filter {
        when (it) {
            Challenge.ROWER_OF_THE_MONTH -> e.hasRower
            Challenge.PROTEIN_DATE -> e.mealsLastMonth
            Challenge.TRIPLE_SESSION -> e.rotationSize >= BadgeConfig.TRIPLE_SESSION_MIN_TEMPLATES
            Challenge.HYDRATED -> e.waterDaysLastMonth >= BadgeConfig.HYDRATED_MIN_DAYS_BEFORE
            else -> true
        }
    }

    /**
     * The challenge of [month]: picked from [eligible] by a fixed hash of the year and month (the same after a
     * reinstall or a restore), never [previous] (last month's challenge) when another one is eligible.
     */
    fun draw(month: YearMonth, eligible: List<Challenge>, previous: Challenge?): Challenge? {
        val candidates = eligible.filter { it != previous }.ifEmpty { eligible }
        if (candidates.isEmpty()) return null
        return candidates[Math.floorMod(mix(month.year * 12L + month.monthValue), candidates.size.toLong()).toInt()]
    }

    /** SplitMix64 finalizer: spreads consecutive months over the candidates. */
    private fun mix(seed: Long): Long {
        var z = seed + -0x61c8864680b583ebL
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    fun progress(challenge: Challenge, month: YearMonth, h: BadgeHistory): ChallengeProgress {
        val goal = BadgeConfig.CHALLENGE_GOALS.getValue(challenge)
        fun inMonth(day: LocalDate) = YearMonth.from(day) == month
        val sessionDays = h.sessions.map { it.start.toLocalDate() }
        return when (challenge) {
            Challenge.TWELVE_STRIKES -> cumulative(sessionDays.filter(::inMonth).map { it to 1.0 }, goal)
            Challenge.ROWER_OF_THE_MONTH -> cumulative(h.rowedMetres.filter { inMonth(it.first) }.map { (d, m) -> d to m.toDouble() }, goal)
            Challenge.RECORD_HUNTER -> cumulative(h.recordDays.filter(::inMonth).map { it to 1.0 }, goal)
            Challenge.CURIOUS -> {
                val firstDone = h.exercisesDone.groupBy({ it.first }, { it.second }).values.map { it.min() }
                cumulative(firstDone.filter(::inMonth).map { it to 1.0 }, goal)
            }
            Challenge.PROTEIN_DATE -> cumulative(h.proteinDays.distinct().filter(::inMonth).map { it to 1.0 }, goal)
            Challenge.NO_BLANK_WEEK -> noBlankWeek(month, sessionDays)
            Challenge.TRIPLE_SESSION -> tripleSession(h, ::inMonth, goal)
            Challenge.HYDRATED -> cumulative(Badges.waterGoalDays(h).filter(::inMonth).map { it to 1.0 }, goal)
        }
    }

    private fun cumulative(events: List<Pair<LocalDate, Double>>, goal: Double): ChallengeProgress {
        var total = 0.0
        var met: LocalDate? = null
        for ((day, value) in events.sortedBy { it.first }) {
            total += value
            if (met == null && total >= goal) met = day
        }
        return ChallengeProgress(total, goal, met)
    }

    /** Every week whose Monday is in the month holds a session (possibly in the next month); met on the last one. */
    private fun noBlankWeek(month: YearMonth, sessionDays: List<LocalDate>): ChallengeProgress {
        val mondays = generateSequence(month.atDay(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))) { it.plusWeeks(1) }
            .takeWhile { YearMonth.from(it) == month }
            .toList()
        val firstSessions = mondays.map { monday -> sessionDays.filter { !it.isBefore(monday) && it.isBefore(monday.plusWeeks(1)) }.minOrNull() }
        val done = firstSessions.filterNotNull()
        val met = if (done.size == mondays.size) done.max() else null
        return ChallengeProgress(done.size.toDouble(), mondays.size.toDouble(), met)
    }

    /** Each template of the rotation done [goal] times in the month; met on the session completing the last one. */
    private fun tripleSession(h: BadgeHistory, inMonth: (LocalDate) -> Boolean, goal: Double): ChallengeProgress {
        if (h.rotation.isEmpty()) return ChallengeProgress(0.0, goal, null)
        val times = goal.toInt()
        val byTemplate = h.rotation.associateWith { template ->
            h.sessions.filter { it.templateId == template && inMonth(it.start.toLocalDate()) }.map { it.start.toLocalDate() }.sorted()
        }
        val value = byTemplate.values.sumOf { minOf(it.size, times) }.toDouble()
        val reached = byTemplate.values.map { it.getOrNull(times - 1) }
        val met = if (reached.all { it != null }) reached.maxOf { it!! } else null
        return ChallengeProgress(value, (times * h.rotation.size).toDouble(), met)
    }
}
