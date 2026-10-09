package fr.olivier.forme.feature.badges.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/** The GymFlemme badges (V2.17): regularity and effort, never body weight. Ids are stored: never rename them. */
enum class Badge {
    NOT_DEAD,
    WELL_STARTED,
    REPEAT_OFFENDER,
    PILLAR,
    BEATS_THE_COUCH,
    EARLY_BIRD,
    REGULAR,
    FIRST_RECORD,
    ALL_ROUNDER,
    CREATINE_LOYAL,
}

/** What the badges are computed from: finished real sessions (start, local time) and so on. */
data class BadgeHistory(
    val sessions: List<LocalDateTime> = emptyList(),
    /** Days of the sessions where a record was beaten. */
    val recordDays: List<LocalDate> = emptyList(),
    /** Each exercise done (working set or cardio) and the day it was done. */
    val exercisesDone: List<Pair<Long, LocalDate>> = emptyList(),
    val creatineDays: List<LocalDate> = emptyList(),
)

/**
 * The day each badge was earned, recomputed from the whole history: the day its condition was first met. A badge
 * never goes away once stored, even if the history changes afterwards.
 */
object Badges {
    const val NOT_DEAD_SESSIONS = 1
    const val WELL_STARTED_SESSIONS = 5
    const val REPEAT_OFFENDER_SESSIONS = 10
    const val PILLAR_SESSIONS = 50
    const val COUCH_WEEKS = 4
    val EARLY_BEFORE: LocalTime = LocalTime.of(8, 0)
    const val REGULAR_SESSIONS_IN_A_WEEK = 3
    const val ALL_ROUNDER_EXERCISES = 10
    const val CREATINE_DAYS = 30

    private fun weekStart(day: LocalDate) = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun earned(history: BadgeHistory): Map<Badge, LocalDate> {
        val sessions = history.sessions.sorted()
        val days = sessions.map { it.toLocalDate() }
        val result = mutableMapOf<Badge, LocalDate>()
        fun nth(badge: Badge, n: Int) = days.getOrNull(n - 1)?.let { result[badge] = it }
        nth(Badge.NOT_DEAD, NOT_DEAD_SESSIONS)
        nth(Badge.WELL_STARTED, WELL_STARTED_SESSIONS)
        nth(Badge.REPEAT_OFFENDER, REPEAT_OFFENDER_SESSIONS)
        nth(Badge.PILLAR, PILLAR_SESSIONS)
        couch(days)?.let { result[Badge.BEATS_THE_COUCH] = it }
        sessions.firstOrNull { it.toLocalTime() < EARLY_BEFORE }?.let { result[Badge.EARLY_BIRD] = it.toLocalDate() }
        regular(days)?.let { result[Badge.REGULAR] = it }
        history.recordDays.minOrNull()?.let { result[Badge.FIRST_RECORD] = it }
        allRounder(history.exercisesDone)?.let { result[Badge.ALL_ROUNDER] = it }
        creatine(history.creatineDays)?.let { result[Badge.CREATINE_LOYAL] = it }
        return result
    }

    /** First session of the 4th week in a row with at least one session. */
    private fun couch(days: List<LocalDate>): LocalDate? {
        val firstByWeek = days.groupBy(::weekStart).mapValues { (_, d) -> d.min() }.toSortedMap()
        var run = 0
        var previous: LocalDate? = null
        for ((week, first) in firstByWeek) {
            run = if (previous != null && previous.plusWeeks(1) == week) run + 1 else 1
            previous = week
            if (run >= COUCH_WEEKS) return first
        }
        return null
    }

    /** Day of the 3rd session of the first week holding 3. */
    private fun regular(days: List<LocalDate>): LocalDate? =
        days.groupBy(::weekStart).values
            .mapNotNull { it.sorted().getOrNull(REGULAR_SESSIONS_IN_A_WEEK - 1) }
            .minOrNull()

    /** Day the 10th different exercise was first done. */
    private fun allRounder(done: List<Pair<Long, LocalDate>>): LocalDate? =
        done.groupBy({ it.first }, { it.second }).values.map { it.min() }.sorted().getOrNull(ALL_ROUNDER_EXERCISES - 1)

    /** 30th day of the first run of 30 days in a row. */
    private fun creatine(days: List<LocalDate>): LocalDate? {
        var run = 0
        var previous: LocalDate? = null
        for (day in days.distinct().sorted()) {
            run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
            previous = day
            if (run >= CREATINE_DAYS) return day
        }
        return null
    }
}
