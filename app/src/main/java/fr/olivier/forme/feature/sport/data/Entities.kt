package fr.olivier.forme.feature.sport.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import fr.olivier.forme.feature.sport.domain.SessionExerciseStatus
import java.time.Instant

@Entity(tableName = "exercise")
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val muscleGroup: MuscleGroup,
    val type: ExerciseType,
    /** TIMED only: the duration applies to each side. */
    val perSide: Boolean = false,
    val loadIncrementKg: Double = 2.5,
    /** Default target used when the exercise is added to a template or a free session. */
    val defaultTargetSets: Int = 3,
    val defaultRepsMin: Int? = null,
    val defaultRepsMax: Int? = null,
    val defaultDurationSec: Int? = null,
    val settingsNote: String? = null,
    val loadNote: String? = null,
    /** Absolute path of a copy stored in the app private files. */
    val photoUri: String? = null,
    val archived: Boolean = false,
)

@Entity(tableName = "workout_template")
data class WorkoutTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val code: String,
    /** Null = not part of the A → B → C rotation. */
    val orderInRotation: Int? = null,
    val archived: Boolean = false,
)

@Entity(
    tableName = "template_exercise",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("templateId"), Index("exerciseId")],
)
data class TemplateExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetDurationSec: Int? = null,
    val optional: Boolean = false,
    /** Null = default rest from settings. */
    val restSec: Int? = null,
)

@Entity(
    tableName = "session",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("templateId"), Index("endedAt")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long?,
    /** Template name/code at the time of the session: history stays readable after renames. */
    val templateNameSnapshot: String?,
    val templateCodeSnapshot: String?,
    val startedAt: Instant,
    /** Null = session in progress. */
    val endedAt: Instant? = null,
    val feeling: Int? = null,
    val notes: String? = null,
    /** Bootstrap session created at first launch: hidden from history, ignored by progression. */
    val isSeed: Boolean = false,
)

/** Frozen copy of the template line taken when the session starts. */
@Entity(
    tableName = "session_exercise",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class SessionExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val status: SessionExerciseStatus = SessionExerciseStatus.PENDING,
    val targetSets: Int,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetDurationSec: Int? = null,
    val restSec: Int? = null,
    val optional: Boolean = false,
)

@Entity(
    tableName = "set_entry",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId", "completedAt")],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val setIndex: Int,
    val loadKg: Double? = null,
    val reps: Int? = null,
    /** TIMED: per side when the exercise is perSide. */
    val durationSec: Int? = null,
    /** Reps in reserve, 0..5. */
    val rir: Int? = null,
    val isWarmup: Boolean = false,
    val completedAt: Instant,
)

@Entity(
    tableName = "cardio_entry",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("sessionId"), Index("exerciseId")],
)
data class CardioEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: Long,
    val durationSec: Int,
    val distanceM: Int? = null,
    val pacePer500mSec: Int? = null,
    val caloriesDisplayed: Int? = null,
)
