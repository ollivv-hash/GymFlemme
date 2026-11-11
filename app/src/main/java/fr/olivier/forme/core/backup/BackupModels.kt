package fr.olivier.forme.core.backup

import fr.olivier.forme.feature.nutrition.domain.ActivityLevel
import fr.olivier.forme.feature.bodyweight.data.BodyWeightEntity
import fr.olivier.forme.feature.heart.data.SessionHeartEntity
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.heart.domain.KcalSource
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.DishEntity
import fr.olivier.forme.feature.nutrition.data.DishIngredientEntity
import fr.olivier.forme.feature.nutrition.data.FoodEntity
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.data.SupplementDefaults
import fr.olivier.forme.feature.nutrition.data.SupplementEntity
import fr.olivier.forme.feature.nutrition.data.SupplementIntakeEntity
import fr.olivier.forme.feature.nutrition.data.SupplementUnit
import fr.olivier.forme.feature.nutrition.domain.FoodSource
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionGoal
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
import fr.olivier.forme.feature.badges.data.BadgeEntity
import fr.olivier.forme.feature.badges.data.ChallengeEntity
import fr.olivier.forme.feature.badges.domain.BadgeMigration
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate

/**
 * Full backup file. [schemaVersion] is the backup format version (independent from the Room version):
 * v1 = sport + body weight, v2 adds the `nutrition` section (absent in v1 files → empty),
 * v3 adds supersets, replacements and exercise notes (optional fields, absent → null),
 * v4 adds session heart rate and age / sex / maximum heart rate, v5 adds height, nutrition goal and calorie target,
 * v6 adds the exercise library link (`libraryId`, absent → null), v7 the badges earned (absent → recomputed from
 * the history, silently), v8 their tier (V2.18; v7 badges are converted on restore), v9 the monthly challenges
 * (absent → none; the current month's is drawn again, identically), v10 the food supplements (V2.20; the creatine
 * of older files, in the profile and `creatine`, becomes the first supplement, the others are added switched off),
 * v11 the dishes of one's own (V2.21; absent → none), v12 the first name of the profile (V2.22; absent → none), v13 the appearance (V2.24: theme, accent, dynamic colours;
 * absent → the phone's settings are kept), v14 the activity level and the protein target in grams of the profile (V2.25;
 * absent → as before: moderate, g/kg), v15 how often each dish was added (V2.26; absent → 0).
 * Instants are epoch milliseconds, dates are ISO-8601 strings. The Ciqual and exercise library reference tables
 * (embedded in the APK) are not exported.
 */
@Serializable
data class BackupFile(
    val schemaVersion: Int,
    val exportedAt: Long,
    val app: String = "fr.olivier.forme",
    val sport: SportBackup,
    val bodyWeight: List<BodyWeightDto>,
    val nutrition: NutritionBackup = NutritionBackup(),
    /** V2.17 (backup v7): badges earned; absent in older files. */
    val badges: List<BadgeDto> = emptyList(),
    /** V2.19 (backup v9): the challenge of each month; absent in older files. */
    val challenges: List<ChallengeDto> = emptyList(),
    /** V2.24 (backup v13): Réglages → Apparence; absent in older files (the current one is kept). */
    val appearance: AppearanceDto? = null,
)

/** Names of [fr.olivier.forme.core.settings.ThemeMode] and [fr.olivier.forme.core.settings.AccentColor]. */
@Serializable
data class AppearanceDto(val mode: String, val accent: String, val dynamicColor: Boolean = false) {
    fun toAppearance() = fr.olivier.forme.core.settings.Appearance(
        mode = fr.olivier.forme.core.settings.ThemeMode.entries.firstOrNull { it.name == mode } ?: fr.olivier.forme.core.settings.ThemeMode.SYSTEM,
        accent = fr.olivier.forme.core.settings.AccentColor.entries.firstOrNull { it.name == accent } ?: fr.olivier.forme.core.settings.AccentColor.GREEN,
        dynamicColor = dynamicColor,
    )

    companion object {
        fun of(a: fr.olivier.forme.core.settings.Appearance) = AppearanceDto(a.mode.name, a.accent.name, a.dynamicColor)
    }
}

@Serializable
data class ChallengeDto(val month: String, val challenge: String, val completedAt: String? = null, val seen: Boolean)

/** [tier]: V2.18 (backup v8), 0 = no tier; files of format 7 hold the V2.17 badges, converted on restore. */
@Serializable
data class BadgeDto(val id: String, val earnedAt: String, val seen: Boolean, val tier: Int = 0)

@Serializable
data class NutritionBackup(
    val profile: ProfileDto? = null,
    val foods: List<FoodDto> = emptyList(),
    val meals: List<MealEntryDto> = emptyList(),
    /** Until v9: the days creatine was taken (v10: [supplementIntakes]). */
    val creatine: List<CreatineDto> = emptyList(),
    /** V2.20 (backup v10). */
    val supplements: List<SupplementDto> = emptyList(),
    val supplementIntakes: List<SupplementIntakeDto> = emptyList(),
    /** V2.21 (backup v11): dishes of one's own (their food is in [foods]) and their ingredients. */
    val dishes: List<DishDto> = emptyList(),
    val dishIngredients: List<DishIngredientDto> = emptyList(),
)

@Serializable
data class DishDto(val foodId: Long, val cookedWeightG: Double? = null, val servings: Int = 1, val useCount: Int = 0)

@Serializable
data class DishIngredientDto(
    val id: Long,
    val dishId: Long,
    val position: Int,
    val name: String,
    val grams: Double,
    val kcalPer100g: Double? = null,
    val proteinPer100g: Double,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
)

@Serializable
data class SupplementDto(
    val id: Long,
    val name: String,
    val code: String? = null,
    val amount: Double,
    val unit: SupplementUnit,
    val active: Boolean,
    val sortOrder: Int,
    val reminderEnabled: Boolean,
    val reminderMinutes: Int,
)

@Serializable
data class SupplementIntakeDto(val id: Long, val supplementId: Long, val date: String, val amount: Double, val takenAt: Long)

@Serializable
data class ProfileDto(
    val proteinTargetGPerKg: Double,
    val wheyProteinPer100g: Double? = null,
    val wheyKcalPer100g: Double? = null,
    val wheyCarbsPer100g: Double? = null,
    val wheyFatPer100g: Double? = null,
    val wheyScoopG: Double,
    /** Until v9 (v10: the creatine supplement). */
    val creatineDoseG: Double? = null,
    val creatineReminderEnabled: Boolean? = null,
    val creatineReminderMinutes: Int? = null,
    val birthYear: Int? = null,
    val sex: Sex? = null,
    val hrMaxOverride: Int? = null,
    val heightCm: Int? = null,
    val nutritionGoal: NutritionGoal? = null,
    val kcalTargetOverride: Int? = null,
    /** V2.22 (backup v12). */
    val firstName: String? = null,
    /** V2.25 (backup v14). */
    val activityLevel: ActivityLevel? = null,
    val proteinTargetOverrideG: Int? = null,
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
    /** V2.1 (backup v4): heart rate of sessions; absent in older files. */
    val heart: List<SessionHeartDto> = emptyList(),
)

@Serializable
data class SessionHeartDto(
    val sessionId: Long,
    val source: HeartSource,
    val seriesStart: Long,
    val intervalSec: Int,
    val bpmSeries: String,
    val avgBpm: Int,
    val minBpm: Int,
    val maxBpm: Int,
    val hrMax: Int? = null,
    val zoneSeconds: List<Int>,
    val kcal: Double? = null,
    val kcalSource: KcalSource? = null,
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
    val nextNote: String? = null,
    /** V6: illustration from the exercise library. */
    val libraryId: String? = null,
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
    val supersetGroup: Int? = null,
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
    val supersetGroup: Int? = null,
    val replacedExerciseId: Long? = null,
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
    defaultDurationSec, settingsNote, loadNote, photoUri, archived, nextNote, libraryId,
)
fun ExerciseDto.toEntity() = ExerciseEntity(
    id, name, muscleGroup, type, perSide, loadIncrementKg, defaultTargetSets, defaultRepsMin, defaultRepsMax,
    defaultDurationSec, settingsNote, loadNote, photoUri, archived, nextNote, libraryId,
)

fun WorkoutTemplateEntity.toDto() = TemplateDto(id, name, code, orderInRotation, archived)
fun TemplateDto.toEntity() = WorkoutTemplateEntity(id, name, code, orderInRotation, archived)

fun TemplateExerciseEntity.toDto() = TemplateExerciseDto(
    id, templateId, exerciseId, position, targetSets, targetRepsMin, targetRepsMax, targetDurationSec, optional, restSec,
    supersetGroup,
)
fun TemplateExerciseDto.toEntity() = TemplateExerciseEntity(
    id, templateId, exerciseId, position, targetSets, targetRepsMin, targetRepsMax, targetDurationSec, optional, restSec,
    supersetGroup,
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
    supersetGroup, replacedExerciseId,
)
fun SessionExerciseDto.toEntity() = SessionExerciseEntity(
    id, sessionId, exerciseId, position, status, targetSets, targetRepsMin, targetRepsMax, targetDurationSec, restSec, optional,
    supersetGroup, replacedExerciseId,
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
    birthYear = birthYear, sex = sex, hrMaxOverride = hrMaxOverride, heightCm = heightCm, nutritionGoal = nutritionGoal,
    kcalTargetOverride = kcalTargetOverride,
    firstName = firstName,
    activityLevel = activityLevel,
    proteinTargetOverrideG = proteinTargetOverrideG,
)
fun ProfileDto.toEntity() = ProfileEntity(
    1, proteinTargetGPerKg, wheyProteinPer100g, wheyKcalPer100g, wheyCarbsPer100g, wheyFatPer100g, wheyScoopG,
    birthYear, sex, hrMaxOverride, heightCm, nutritionGoal, kcalTargetOverride, firstName, activityLevel, proteinTargetOverrideG,
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

fun BadgeEntity.toDto() = BadgeDto(id, earnedAt.toString(), seen, tier)
fun ChallengeEntity.toDto() = ChallengeDto(month, challenge, completedAt?.toString(), seen)
fun ChallengeDto.toEntity() = ChallengeEntity(month, challenge, completedAt?.let(LocalDate::parse), seen)

/** Format 7 badges (V2.17, without tier) are converted like the database (migration 7 → 8). */
fun BadgeDto.toEntity(schemaVersion: Int): BadgeEntity {
    val (newId, newTier) = if (schemaVersion < 8) BadgeMigration.fromV1(id) else id to tier
    return BadgeEntity(newId, newTier, LocalDate.parse(earnedAt), seen)
}

fun DishEntity.toDto() = DishDto(foodId, cookedWeightG, servings, useCount)
fun DishDto.toEntity() = DishEntity(foodId, cookedWeightG, servings, useCount)
fun DishIngredientEntity.toDto() = DishIngredientDto(id, dishId, position, name, grams, kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g)
fun DishIngredientDto.toEntity() = DishIngredientEntity(id, dishId, position, name, grams, kcalPer100g, proteinPer100g, carbsPer100g, fatPer100g)

fun SupplementEntity.toDto() = SupplementDto(id, name, code, amount, unit, active, sortOrder, reminderEnabled, reminderMinutes)
fun SupplementDto.toEntity() = SupplementEntity(id, name, code, amount, unit, active, sortOrder, reminderEnabled, reminderMinutes)
fun SupplementIntakeEntity.toDto() = SupplementIntakeDto(id, supplementId, date.toString(), amount, takenAt.toEpochMilli())
fun SupplementIntakeDto.toEntity() = SupplementIntakeEntity(id, supplementId, LocalDate.parse(date), amount, Instant.ofEpochMilli(takenAt))

/**
 * The supplements to restore. Files before v10 hold only creatine (dose and reminder in the profile, its days in
 * `creatine`): it becomes the first default supplement, like the database migration 9 → 10.
 */
fun NutritionBackup.supplementsToRestore(schemaVersion: Int): Pair<List<SupplementEntity>, List<SupplementIntakeEntity>> {
    if (schemaVersion >= 10) return supplements.map { it.toEntity() } to supplementIntakes.map { it.toEntity() }
    val defaults = SupplementDefaults.legacy.mapIndexed { index, it -> it.copy(id = index + 1L) }
    val creatine = defaults.first { it.isCreatine }.let { c ->
        c.copy(
            amount = profile?.creatineDoseG ?: c.amount,
            reminderEnabled = profile?.creatineReminderEnabled ?: c.reminderEnabled,
            reminderMinutes = profile?.creatineReminderMinutes ?: c.reminderMinutes,
        )
    }
    val intakes = this.creatine.map {
        SupplementIntakeEntity(supplementId = creatine.id, date = LocalDate.parse(it.date), amount = it.doseG, takenAt = Instant.ofEpochMilli(it.takenAt))
    }
    return defaults.map { if (it.isCreatine) creatine else it } to intakes
}

fun SessionHeartEntity.toDto() = SessionHeartDto(
    sessionId, source, seriesStart.toEpochMilli(), intervalSec, bpmSeries, avgBpm, minBpm, maxBpm, hrMax, zoneSeconds, kcal, kcalSource,
)

fun SessionHeartDto.toEntity(): SessionHeartEntity {
    val z = (zoneSeconds + List(5) { 0 }).take(5)
    return SessionHeartEntity(
        sessionId, source, Instant.ofEpochMilli(seriesStart), intervalSec, bpmSeries, avgBpm, minBpm, maxBpm, hrMax,
        z[0], z[1], z[2], z[3], z[4], kcal, kcalSource,
    )
}
