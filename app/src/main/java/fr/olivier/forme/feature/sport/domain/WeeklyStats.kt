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

/** Weekly sets of one library muscle (V2.12): [direct] as a main muscle, [indirect] already halved. */
data class MuscleVolume(val direct: Double = 0.0, val indirect: Double = 0.0) {
    val total get() = direct + indirect
    operator fun plus(other: MuscleVolume) = MuscleVolume(direct + other.direct, indirect + other.indirect)
}

data class MuscleWeek(
    /** By library muscle key ("quads"), only muscles that were trained. */
    val byMuscle: Map<String, MuscleVolume>,
    /** Working sets (cardio aside) of exercises without an illustration: their muscles are unknown. */
    val unlinkedSets: Int,
)

/** The muscles an exercise of the library works (main, then assisting ones, adductors included). */
data class Muscles(val primary: List<String>, val secondary: List<String>)

/**
 * Weekly volume per library muscle with fractional sets: a working set counts 1 for each main muscle and 0.5 for
 * each assisting one, so that small muscles worked in passing are not overstated (V2.12).
 */
object MuscleStats {
    const val SECONDARY_SHARE = 0.5

    /** Muscles given a 10–20-type band; the others are shown without one (direct work rarely targets them). */
    val LARGE = listOf("chest", "lats", "back", "shoulders", "biceps", "triceps", "quads", "hamstrings", "glutes", "calves", "core")
    val SMALL = listOf("forearms", "traps", "lower_back", "obliques", "adductors", "tibialis", "neck")

    /** [sets] paired with the muscles of their exercise's library entry, null when the exercise is not linked. */
    fun week(sets: List<Pair<TrainedSet, Muscles?>>, weekStart: LocalDate): MuscleWeek {
        val end = weekStart.plusDays(7)
        val inWeek = sets.filter { (set, _) -> set.date >= weekStart && set.date < end && set.type != ExerciseType.CARDIO }
        val byMuscle = mutableMapOf<String, MuscleVolume>()
        fun add(muscle: String, v: MuscleVolume) { byMuscle[muscle] = (byMuscle[muscle] ?: MuscleVolume()) + v }
        inWeek.forEach { (_, muscles) ->
            if (muscles == null) return@forEach
            muscles.primary.forEach { add(it, MuscleVolume(direct = 1.0)) }
            (muscles.secondary.distinct() - muscles.primary.toSet()).forEach { add(it, MuscleVolume(indirect = SECONDARY_SHARE)) }
        }
        return MuscleWeek(byMuscle, inWeek.count { it.second == null })
    }
}
