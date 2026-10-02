package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeeklyStatsTest {
    private val monday = LocalDate.of(2026, 9, 28)

    @Test
    fun `week starts on monday`() {
        assertEquals(monday, WeeklyStats.weekStart(LocalDate.of(2026, 10, 4))) // Sunday
        assertEquals(monday, WeeklyStats.weekStart(monday))
    }

    @Test
    fun `sets are counted per primary group, cardio excluded, inside the week only`() {
        val sets = listOf(
            TrainedSet(monday, MuscleGroup.DOS, ExerciseType.WEIGHTED),
            TrainedSet(monday.plusDays(2), MuscleGroup.DOS, ExerciseType.WEIGHTED),
            TrainedSet(monday.plusDays(2), MuscleGroup.ABDOS, ExerciseType.TIMED),
            TrainedSet(monday.plusDays(2), MuscleGroup.CARDIO, ExerciseType.CARDIO),
            TrainedSet(monday.plusDays(7), MuscleGroup.DOS, ExerciseType.WEIGHTED), // next week
        )
        val stats = WeeklyStats.week(sets, listOf(monday, monday.plusDays(2), monday.plusDays(7)), monday)
        assertEquals(mapOf(MuscleGroup.DOS to 2, MuscleGroup.ABDOS to 1), stats.setsByGroup)
        assertEquals(2, stats.sessions)
    }

    @Test
    fun `streak counts weeks meeting the target and ignores the week in progress`() {
        val dates = listOf(
            monday.minusWeeks(2), monday.minusWeeks(2).plusDays(2), monday.minusWeeks(2).plusDays(4), // 3
            monday.minusWeeks(1), monday.minusWeeks(1).plusDays(2), monday.minusWeeks(1).plusDays(4), // 3
            monday, // current week: 1 so far
        )
        assertEquals(2, WeeklyStats.streak(dates, 3, monday.plusDays(1)))
        assertEquals(3, WeeklyStats.streak(dates + monday.plusDays(2) + monday.plusDays(3), 3, monday.plusDays(3)))
        assertEquals(0, WeeklyStats.streak(listOf(monday.minusWeeks(3)), 3, monday))
    }
}
