package fr.olivier.forme.feature.sport.data

import androidx.room.Embedded
import androidx.room.Relation
import java.time.Instant

data class TemplateLineWithExercise(
    @Embedded val line: TemplateExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

data class SessionLineWithExercise(
    @Embedded val line: SessionExerciseEntity,
    @Relation(parentColumn = "exerciseId", entityColumn = "id")
    val exercise: ExerciseEntity,
)

data class WorkingSetRow(
    val startedAt: Instant,
    val muscleGroup: fr.olivier.forme.feature.sport.domain.MuscleGroup,
    val type: fr.olivier.forme.feature.sport.domain.ExerciseType,
    /** The exercise's library entry (V2.12: volume per muscle); null when not linked. */
    val libraryId: String? = null,
)

/** A planned line of a template with what the catch-up advice needs from its exercise (V2.13). */
data class PlannedLineRow(
    val templateId: Long,
    val targetSets: Int,
    val optional: Boolean,
    val type: fr.olivier.forme.feature.sport.domain.ExerciseType,
    val libraryId: String?,
)

/** One working set of a loaded exercise (plateau detection across exercises). */
data class WeightedSetRow(
    val exerciseId: Long,
    val loadIncrementKg: Double,
    val sessionId: Long,
    val startedAt: Instant,
    val loadKg: Double?,
    val reps: Int?,
)

/** One working set of an exercise, with the context of the session it belongs to. */
data class ExerciseSetRow(
    val sessionId: Long,
    val startedAt: Instant,
    val isSeed: Boolean,
    val setIndex: Int,
    val loadKg: Double?,
    val reps: Int?,
    val durationSec: Int?,
    val rir: Int?,
    val targetRepsMin: Int?,
)
