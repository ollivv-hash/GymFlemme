package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** V2.12: weekly sets per library muscle, fractional for assisting muscles. */
class MuscleStatsTest {
    private val monday = LocalDate.of(2026, 10, 5)
    private val legPress = Muscles(primary = listOf("quads", "glutes"), secondary = listOf("hamstrings", "core"))

    private fun set(day: LocalDate, muscles: Muscles?, type: ExerciseType = ExerciseType.WEIGHTED) =
        TrainedSet(day, MuscleGroup.JAMBES, type) to muscles

    @Test
    fun mainMusclesCountOneAndAssistingOnesHalf() {
        val week = MuscleStats.week(List(3) { set(monday.plusDays(1), legPress) }, monday)
        assertEquals(MuscleVolume(direct = 3.0), week.byMuscle["quads"])
        assertEquals(MuscleVolume(direct = 3.0), week.byMuscle["glutes"])
        assertEquals(MuscleVolume(indirect = 1.5), week.byMuscle["hamstrings"])
        assertEquals(1.5, week.byMuscle.getValue("core").total, 0.0)
        assertEquals(0, week.unlinkedSets)
    }

    @Test
    fun directAndIndirectAddUpAcrossExercises() {
        val legCurl = Muscles(primary = listOf("hamstrings"), secondary = listOf("calves", "hamstrings"))
        val week = MuscleStats.week(List(2) { set(monday, legPress) } + List(3) { set(monday, legCurl) }, monday)
        // A muscle listed both as main and assisting counts once, as main.
        assertEquals(MuscleVolume(direct = 3.0, indirect = 1.0), week.byMuscle["hamstrings"])
        assertEquals(4.0, week.byMuscle.getValue("hamstrings").total, 0.0)
    }

    @Test
    fun otherWeeksCardioAndUnlinkedExercisesAreSetApart() {
        val week = MuscleStats.week(
            listOf(
                set(monday.minusDays(1), legPress), // previous Sunday
                set(monday.plusDays(7), legPress), // next Monday
                set(monday, Muscles(listOf("quads"), emptyList()), ExerciseType.CARDIO),
                set(monday.plusDays(2), null),
                set(monday.plusDays(2), null),
            ),
            monday,
        )
        assertNull(week.byMuscle["quads"])
        assertEquals(2, week.unlinkedSets)
    }

    @Test
    fun everyLibraryMuscleIsLargeOrSmall() {
        val all = MuscleStats.LARGE + MuscleStats.SMALL
        assertEquals(all.size, all.distinct().size)
        assertEquals(fr.olivier.forme.feature.library.domain.LibraryLabels.muscles.keys, all.toSet())
    }
}
