package fr.olivier.forme.feature.sport.domain

/** A working set as seen by business rules (warm-ups are filtered out beforehand). */
data class WorkSet(
    val loadKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val rir: Int? = null,
)

/** Working sets of an exercise in the last real session containing it, with that session's targets. */
data class PreviousPerformance(
    val sets: List<WorkSet>,
    val targetSets: Int?,
    val targetRepsMax: Int?,
    val targetDurationSec: Int?,
)

sealed interface Suggestion {
    data class Load(val loadKg: Double) : Suggestion
    data class Reps(val reps: Int) : Suggestion
    data class Duration(val durationSec: Int) : Suggestion
}

/**
 * Progression rule of the programme, applied per exercise type:
 * - the last set of the previous session was validated with RIR ≥ 3, or
 * - no RIR on that set and every set reached the target (reps max, or duration) with at least the target number of sets
 * → WEIGHTED: load + increment; BODYWEIGHT: +1 rep; TIMED: +5 s; CARDIO: nothing.
 * The suggestion is only displayed, never applied without the user's validation.
 */
object ProgressionRule {
    const val RIR_THRESHOLD = 3
    const val TIMED_STEP_SEC = 5

    fun suggest(type: ExerciseType, loadIncrementKg: Double, previous: PreviousPerformance?): Suggestion? {
        if (previous == null || previous.sets.isEmpty() || type == ExerciseType.CARDIO) return null
        val last = previous.sets.last()
        val ready = last.rir?.let { it >= RIR_THRESHOLD } ?: allTargetsReached(type, previous)
        if (!ready) return null
        return when (type) {
            ExerciseType.WEIGHTED -> last.loadKg?.let { Suggestion.Load(it + loadIncrementKg) }
            ExerciseType.BODYWEIGHT -> last.reps?.let { Suggestion.Reps(it + 1) }
            ExerciseType.TIMED -> last.durationSec?.let { Suggestion.Duration(it + TIMED_STEP_SEC) }
            ExerciseType.CARDIO -> null
        }
    }

    private fun allTargetsReached(type: ExerciseType, previous: PreviousPerformance): Boolean {
        if (previous.targetSets != null && previous.sets.size < previous.targetSets) return false
        return when (type) {
            ExerciseType.TIMED -> {
                val target = previous.targetDurationSec ?: return false
                previous.sets.all { (it.durationSec ?: 0) >= target }
            }
            else -> {
                val target = previous.targetRepsMax ?: return false
                previous.sets.all { (it.reps ?: 0) >= target }
            }
        }
    }
}
