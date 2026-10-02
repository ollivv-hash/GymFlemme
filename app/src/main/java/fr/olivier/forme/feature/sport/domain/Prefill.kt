package fr.olivier.forme.feature.sport.domain

/** Targets of an exercise in the running session. */
data class SetTarget(val repsMin: Int?, val repsMax: Int?, val durationSec: Int?)

/**
 * Values proposed for the next set:
 * 1. the last set already done in this session (the user may have adjusted the load);
 * 2. otherwise the same set of the last time (or its last set if it had fewer sets);
 * 3. otherwise the target (reps max, duration) with no load.
 * RIR is never pre-filled.
 */
object Prefill {
    fun nextSet(doneThisSession: List<WorkSet>, lastTime: List<WorkSet>, target: SetTarget): WorkSet {
        val base = doneThisSession.lastOrNull()
            ?: lastTime.getOrNull(doneThisSession.size)
            ?: lastTime.lastOrNull()
        return if (base != null) {
            base.copy(rir = null)
        } else {
            WorkSet(loadKg = null, reps = target.repsMax ?: target.repsMin, durationSec = target.durationSec)
        }
    }

    /** Applies a progression suggestion to a draft (only when the user taps the badge). */
    fun apply(draft: WorkSet, suggestion: Suggestion): WorkSet = when (suggestion) {
        is Suggestion.Load -> draft.copy(loadKg = suggestion.loadKg)
        is Suggestion.Reps -> draft.copy(reps = suggestion.reps)
        is Suggestion.Duration -> draft.copy(durationSec = suggestion.durationSec)
    }

    /** Pace per 500 m in seconds, from duration and distance. */
    fun pacePer500m(durationSec: Int, distanceM: Int?): Int? =
        distanceM?.takeIf { it > 0 && durationSec > 0 }?.let { (durationSec * 500.0 / it).toInt() }
}
