package fr.olivier.forme.feature.sport.domain

/** Working sets of one exercise in one session, with the session's minimum rep target. */
data class SessionSets(
    val sessionId: Long,
    val targetRepsMin: Int?,
    val sets: List<WorkSet>,
)

data class ExerciseRecords(val bestLoadKg: Double?, val bestVolumeKg: Double?)

data class NewRecords(val loadKg: Double?, val volumeKg: Double?) {
    val any get() = loadKg != null || volumeKg != null
}

object RecordsCalculator {
    /** Minimum reps for a load to count as a record when the session has no target (free session). */
    const val FREE_SESSION_MIN_REPS = 1

    /** Best load lifted for at least the session's minimum target reps. */
    fun bestLoad(session: SessionSets): Double? {
        val minReps = session.targetRepsMin ?: FREE_SESSION_MIN_REPS
        return session.sets
            .filter { it.loadKg != null && it.loadKg > 0 && (it.reps ?: 0) >= minReps }
            .maxOfOrNull { it.loadKg!! }
    }

    /** Σ load × reps of the session; null when nothing loaded was lifted. */
    fun volume(session: SessionSets): Double? =
        VolumeCalculator.loadVolume(session.sets).takeIf { it > 0 }

    fun records(history: List<SessionSets>): ExerciseRecords = ExerciseRecords(
        bestLoadKg = history.mapNotNull(::bestLoad).maxOrNull(),
        bestVolumeKg = history.mapNotNull(::volume).maxOrNull(),
    )

    /** Records strictly beaten by [current] compared with [previous] sessions (a first time is not a record). */
    fun newRecords(current: SessionSets, previous: List<SessionSets>): NewRecords {
        val before = records(previous)
        val load = bestLoad(current)?.takeIf { before.bestLoadKg != null && it > before.bestLoadKg }
        val volume = volume(current)?.takeIf { before.bestVolumeKg != null && it > before.bestVolumeKg }
        return NewRecords(load, volume)
    }
}
