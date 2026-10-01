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
