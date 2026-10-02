package fr.olivier.forme.feature.sport.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionRuleTest {
    private fun perf(vararg sets: WorkSet, targetSets: Int? = 3, repsMax: Int? = 10, duration: Int? = null) =
        PreviousPerformance(sets.toList(), targetSets, repsMax, duration)

    @Test
    fun `weighted - last set with RIR 3 suggests load plus increment`() {
        val previous = perf(WorkSet(20.0, 10, rir = 1), WorkSet(20.0, 10, rir = 2), WorkSet(20.0, 10, rir = 3))
        assertEquals(Suggestion.Load(22.5), ProgressionRule.suggest(ExerciseType.WEIGHTED, 2.5, previous))
    }

    @Test
    fun `weighted - uses the exercise increment`() {
        val previous = perf(WorkSet(6.0, 15, rir = 4), targetSets = 1)
        assertEquals(Suggestion.Load(7.0), ProgressionRule.suggest(ExerciseType.WEIGHTED, 1.0, previous))
    }

    @Test
    fun `weighted - last set with RIR below 3 suggests nothing even if reps reached`() {
        val previous = perf(WorkSet(20.0, 10), WorkSet(20.0, 10), WorkSet(20.0, 10, rir = 2))
        assertNull(ProgressionRule.suggest(ExerciseType.WEIGHTED, 2.5, previous))
    }

    @Test
    fun `weighted - no RIR and all sets at reps max suggests load`() {
        val previous = perf(WorkSet(15.0, 10), WorkSet(15.0, 10), WorkSet(15.0, 11))
        assertEquals(Suggestion.Load(17.5), ProgressionRule.suggest(ExerciseType.WEIGHTED, 2.5, previous))
    }

    @Test
    fun `weighted - no RIR and one set below reps max suggests nothing`() {
        val previous = perf(WorkSet(15.0, 10), WorkSet(15.0, 9), WorkSet(15.0, 10))
        assertNull(ProgressionRule.suggest(ExerciseType.WEIGHTED, 2.5, previous))
    }

    @Test
    fun `weighted - no RIR and fewer sets than target suggests nothing`() {
        val previous = perf(WorkSet(15.0, 10), WorkSet(15.0, 10))
        assertNull(ProgressionRule.suggest(ExerciseType.WEIGHTED, 2.5, previous))
    }

    @Test
    fun `weighted - abs machine progresses on load (rule by type)`() {
        val previous = perf(WorkSet(15.0, 15), WorkSet(15.0, 15), WorkSet(15.0, 15), repsMax = 15)
        assertEquals(Suggestion.Load(17.5), ProgressionRule.suggest(ExerciseType.WEIGHTED, 2.5, previous))
    }

    @Test
    fun `bodyweight - progression on reps`() {
        val previous = perf(WorkSet(reps = 12), WorkSet(reps = 12), WorkSet(reps = 12), repsMax = 12)
        assertEquals(Suggestion.Reps(13), ProgressionRule.suggest(ExerciseType.BODYWEIGHT, 2.5, previous))
    }

    @Test
    fun `timed - progression adds 5 seconds when every set reached the target`() {
        val previous = perf(WorkSet(durationSec = 25), WorkSet(durationSec = 25), WorkSet(durationSec = 30), repsMax = null, duration = 25)
        assertEquals(Suggestion.Duration(35), ProgressionRule.suggest(ExerciseType.TIMED, 2.5, previous))
    }

    @Test
    fun `cardio and missing history give no suggestion`() {
        assertNull(ProgressionRule.suggest(ExerciseType.CARDIO, 2.5, perf(WorkSet(durationSec = 300, rir = 5))))
        assertNull(ProgressionRule.suggest(ExerciseType.WEIGHTED, 2.5, null))
        assertNull(ProgressionRule.suggest(ExerciseType.WEIGHTED, 2.5, perf()))
    }
}
