package fr.olivier.forme.feature.sport.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** A working set (warm-ups excluded) of a finished real session. */
data class TrainedSet(val date: LocalDate, val muscleGroup: MuscleGroup, val type: ExerciseType)

data class WeekStats(
    val weekStart: LocalDate,
    /** Working sets per primary muscle group; cardio is not counted. */
    val setsByGroup: Map<MuscleGroup, Int>,
    val sessions: Int,
)

/** Weekly training volume and regularity (weeks run Monday → Sunday). */
object WeeklyStats {
    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun week(sets: List<TrainedSet>, sessionDates: List<LocalDate>, weekStart: LocalDate): WeekStats {
        val range = weekStart until weekStart.plusDays(7)
        val byGroup = sets
            .filter { it.date in range && it.type != ExerciseType.CARDIO }
            .groupingBy { it.muscleGroup }
            .eachCount()
        return WeekStats(weekStart, byGroup, sessionDates.count { it in range })
    }

    /**
     * Consecutive weeks meeting the sessions target, ending with the current week if it is already met,
     * otherwise with the previous week (the current week is still in progress).
     */
    fun streak(sessionDates: List<LocalDate>, target: Int, today: LocalDate): Int {
        if (target <= 0) return 0
        val perWeek = sessionDates.groupingBy { weekStart(it) }.eachCount()
        val current = weekStart(today)
        var week = if ((perWeek[current] ?: 0) >= target) current else current.minusWeeks(1)
        var count = 0
        while ((perWeek[week] ?: 0) >= target) {
            count++
            week = week.minusWeeks(1)
        }
        return count
    }

    private infix fun LocalDate.until(end: LocalDate) = object : ClosedRange<LocalDate> {
        override val start = this@until
        override val endInclusive: LocalDate = end.minusDays(1)
    }
}
