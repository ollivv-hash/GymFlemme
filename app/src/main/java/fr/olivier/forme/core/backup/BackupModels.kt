package fr.olivier.forme.core.backup

import fr.olivier.forme.feature.bodyweight.data.BodyWeightEntity
import fr.olivier.forme.feature.sport.data.CardioEntryEntity
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.SessionEntity
import fr.olivier.forme.feature.sport.data.SessionExerciseEntity
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import fr.olivier.forme.feature.sport.data.TemplateExerciseEntity
import fr.olivier.forme.feature.sport.data.WorkoutTemplateEntity
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import fr.olivier.forme.feature.sport.domain.SessionExerciseStatus
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * Full backup file. [schemaVersion] is the backup format version (independent from the Room version);
 * V1.1 will add an optional `nutrition` section and keep reading version 1 files.
 * Instants are epoch milliseconds, dates are ISO-8601 strings.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: Long,
    val app: String = "fr.olivier.forme",
    val sport: SportBackup,
    val bodyWeight: List<BodyWeightDto>,
)

@Serializable
data class SportBackup(
    val exercises: List<ExerciseDto>,
    val templates: List<TemplateDto>,
    val templateExercises: List<TemplateExerciseDto>,
    val sessions: List<SessionDto>,
    val sessionExercises: List<SessionExerciseDto>,
    val sets: List<SetEntryDto>,
    val cardio: List<CardioEntryDto>,
)

@Serializable
data class ExerciseDto(
    val id: Long,
    val name: String,
    val muscleGroup: MuscleGroup,
    val type: ExerciseType,
    val perSide: Boolean,
    val loadIncrementKg: Double,
    val defaultTargetSets: Int,
    val defaultRepsMin: Int? = null,
    val defaultRepsMax: Int? = null,
    val defaultDurationSec: Int? = null,
    val settingsNote: String? = null,
    val loadNote: String? = null,
    val photoUri: String? = null,
    val archived: Boolean,
)

@Serializable
data class TemplateDto(val id: Long, val name: String, val code: String, val orderInRotation: Int? = null, val archived: Boolean)

@Serializable
data class TemplateExerciseDto(
    val id: Long,
    val templateId: Long,
    val exerciseId: Long,
    val position: Int,
    val targetSets: Int,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetDurationSec: Int? = null,
    val optional: Boolean,
    val restSec: Int? = null,
)

@Serializable
data class SessionDto(
    val id: Long,
    val templateId: Long? = null,
    val templateNameSnapshot: String? = null,
    val templateCodeSnapshot: String? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val feeling: Int? = null,
    val notes: String? = null,
    val isSeed: Boolean,
)

@Serializable
data class SessionExerciseDto(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val position: Int,
    val status: SessionExerciseStatus,
    val targetSets: Int,
    val targetRepsMin: Int? = null,
    val targetRepsMax: Int? = null,
    val targetDurationSec: Int? = null,
    val restSec: Int? = null,
    val optional: Boolean,
)

@Serializable
data class SetEntryDto(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val setIndex: Int,
    val loadKg: Double? = null,
    val reps: Int? = null,
    val durationSec: Int? = null,
    val rir: Int? = null,
    val isWarmup: Boolean,
    val completedAt: Long,
)

@Serializable
data class CardioEntryDto(
    val id: Long,
    val sessionId: Long,
    val exerciseId: Long,
    val durationSec: Int,
    val distanceM: Int? = null,
    val pacePer500mSec: Int? = null,
    val caloriesDisplayed: Int? = null,
)

@Serializable
data class BodyWeightDto(val id: Long, val date: String, val weightKg: Double)

// --- Mapping ------------------------------------------------------------------------------------

fun ExerciseEntity.toDto() = ExerciseDto(
    id, name, muscleGroup, type, perSide, loadIncrementKg, defaultTargetSets, defaultRepsMin, defaultRepsMax,
    defaultDurationSec, settingsNote, loadNote, photoUri, archived,
)
fun ExerciseDto.toEntity() = ExerciseEntity(
    id, name, muscleGroup, type, perSide, loadIncrementKg, defaultTargetSets, defaultRepsMin, defaultRepsMax,
    defaultDurationSec, settingsNote, loadNote, photoUri, archived,
)

fun WorkoutTemplateEntity.toDto() = TemplateDto(id, name, code, orderInRotation, archived)
fun TemplateDto.toEntity() = WorkoutTemplateEntity(id, name, code, orderInRotation, archived)

fun TemplateExerciseEntity.toDto() = TemplateExerciseDto(
    id, templateId, exerciseId, position, targetSets, targetRepsMin, targetRepsMax, targetDurationSec, optional, restSec,
)
fun TemplateExerciseDto.toEntity() = TemplateExerciseEntity(
    id, templateId, exerciseId, position, targetSets, targetRepsMin, targetRepsMax, targetDurationSec, optional, restSec,
)

fun SessionEntity.toDto() = SessionDto(
    id, templateId, templateNameSnapshot, templateCodeSnapshot, startedAt.toEpochMilli(), endedAt?.toEpochMilli(),
    feeling, notes, isSeed,
)
fun SessionDto.toEntity() = SessionEntity(
    id, templateId, templateNameSnapshot, templateCodeSnapshot, Instant.ofEpochMilli(startedAt),
    endedAt?.let(Instant::ofEpochMilli), feeling, notes, isSeed,
)

fun SessionExerciseEntity.toDto() = SessionExerciseDto(
    id, sessionId, exerciseId, position, status, targetSets, targetRepsMin, targetRepsMax, targetDurationSec, restSec, optional,
)
fun SessionExerciseDto.toEntity() = SessionExerciseEntity(
    id, sessionId, exerciseId, position, status, targetSets, targetRepsMin, targetRepsMax, targetDurationSec, restSec, optional,
)

fun SetEntryEntity.toDto() = SetEntryDto(
    id, sessionId, exerciseId, setIndex, loadKg, reps, durationSec, rir, isWarmup, completedAt.toEpochMilli(),
)
fun SetEntryDto.toEntity() = SetEntryEntity(
    id, sessionId, exerciseId, setIndex, loadKg, reps, durationSec, rir, isWarmup, Instant.ofEpochMilli(completedAt),
)

fun CardioEntryEntity.toDto() = CardioEntryDto(id, sessionId, exerciseId, durationSec, distanceM, pacePer500mSec, caloriesDisplayed)
fun CardioEntryDto.toEntity() = CardioEntryEntity(id, sessionId, exerciseId, durationSec, distanceM, pacePer500mSec, caloriesDisplayed)

fun BodyWeightEntity.toDto() = BodyWeightDto(id, date.toString(), weightKg)
fun BodyWeightDto.toEntity() = BodyWeightEntity(id, LocalDate.parse(date), weightKg)
