package fr.olivier.forme.core.backup

import fr.olivier.forme.feature.bodyweight.data.BodyWeightEntity
import fr.olivier.forme.feature.nutrition.data.CreatineEntryEntity
import fr.olivier.forme.feature.nutrition.data.FoodEntity
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.domain.FoodSource
import fr.olivier.forme.feature.nutrition.domain.MealType
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
 * Full backup file. [schemaVersion] is the backup format version (independent from the Room version):
 * v1 = sport + body weight, v2 adds the `nutrition` section (absent in v1 files → empty).
 * Instants are epoch milliseconds, dates are ISO-8601 strings. The Ciqual reference table is not exported.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: Long,
    val app: String = "fr.olivier.forme",
    val sport: SportBackup,
    val bodyWeight: List<BodyWeightDto>,
    val nutrition: NutritionBackup = NutritionBackup(),
)

@Serializable
data class NutritionBackup(
    val profile: ProfileDto? = null,
    val foods: List<FoodDto> = emptyList(),
    val meals: List<MealEntryDto> = emptyList(),
    val creatine: List<CreatineDto> = emptyList(),
)

@Serializable
data class ProfileDto(
    val proteinTargetGPerKg: Double,
    val wheyProteinPer100g: Double? = null,
    val wheyKcalPer100g: Double? = null,
    val wheyCarbsPer100g: Double? = null,
    val wheyFatPer100g: Double? = null,
    val wheyScoopG: Double,
    val creatineDoseG: Double,
    val creatineReminderEnabled: Boolean,
    val creatineReminderMinutes: Int,
)

@Serializable
data class FoodDto(
    val id: Long,
    val source: FoodSource,
    val sourceCode: String? = null,
    val name: String,
    val brand: String? = null,
    val kcalPer100g: Double? = null,
    val proteinPer100g: Double,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
    val defaultPortionG: Double? = null,
    val favorite: Boolean,
    val lastUsedAt: Long? = null,
)

@Serializable
data class MealEntryDto(
    val id: Long,
    val date: String,
    val mealType: MealType,
    val foodId: Long? = null,
    val name: String,
    val quantityG: Double,
    val proteinG: Double,
    val kcal: Double? = null,
    val carbsG: Double? = null,
    val fatG: Double? = null,
    val isWhey: Boolean,
    val eatenAt: Long,
)

@Serializable
data class CreatineDto(val id: Long, val date: String, val doseG: Double, val takenAt: Long)

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

fun ProfileEntity.toDto() = ProfileDto(
    proteinTargetGPerKg, wheyProteinPer100g, wheyKcalPer100g, wheyCarbsPer100g, wheyFatPer100g, wheyScoopG,
    creatineDoseG, creatineReminderEnabled, creatineReminderMinutes,
)
fun ProfileDto.toEntity() = ProfileEntity(
    1, proteinTargetGPerKg, wheyProteinPer100g, wheyKcalPer100g, wheyCarbsPer100g, wheyFatPer100g, wheyScoopG,
    creatineDoseG, creatineReminderEnabled, creatineReminderMinutes,
)

fun FoodEntity.toDto() = FoodDto(
    id, source, sourceCode, name, brand, kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g, defaultPortionG,
    favorite, lastUsedAt?.toEpochMilli(),
)
fun FoodDto.toEntity() = FoodEntity(
    id, source, sourceCode, name, brand, kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g, defaultPortionG,
    favorite, lastUsedAt?.let(Instant::ofEpochMilli),
)

fun MealEntryEntity.toDto() = MealEntryDto(
    id, date.toString(), mealType, foodId, name, quantityG, proteinG, kcal, carbsG, fatG, isWhey, eatenAt.toEpochMilli(),
)
fun MealEntryDto.toEntity() = MealEntryEntity(
    id, LocalDate.parse(date), mealType, foodId, name, quantityG, proteinG, kcal, carbsG, fatG, isWhey, Instant.ofEpochMilli(eatenAt),
)

fun CreatineEntryEntity.toDto() = CreatineDto(id, date.toString(), doseG, takenAt.toEpochMilli())
fun CreatineDto.toEntity() = CreatineEntryEntity(id, LocalDate.parse(date), doseG, Instant.ofEpochMilli(takenAt))
