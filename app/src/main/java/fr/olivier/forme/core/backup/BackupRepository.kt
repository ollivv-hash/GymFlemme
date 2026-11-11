package fr.olivier.forme.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.core.db.FormeDatabase
import fr.olivier.forme.core.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val sessions: Int, val exercises: Int, val safetyBackup: File?)

@Singleton
class BackupRepository @Inject constructor(
    private val db: FormeDatabase,
    @ApplicationContext private val context: Context,
    private val clock: Clock,
    private val settings: SettingsRepository,
) {
    suspend fun snapshot(): BackupFile {
        val appearance = AppearanceDto.of(settings.appearance.first())
        val exercises = db.exerciseDao()
        val templates = db.templateDao()
        val sessions = db.sessionDao()
        return db.withTransaction {
            BackupFile(
                schemaVersion = BackupCodec.CURRENT_VERSION,
                exportedAt = clock.millis(),
                sport = SportBackup(
                    exercises = exercises.getAll().sortedBy { it.id }.map { it.toDto() },
                    templates = templates.getAll().sortedBy { it.id }.map { it.toDto() },
                    templateExercises = templates.getAllLines().sortedBy { it.id }.map { it.toDto() },
                    sessions = sessions.getAllSessions().sortedBy { it.id }.map { it.toDto() },
                    sessionExercises = sessions.getAllSessionExercises().sortedBy { it.id }.map { it.toDto() },
                    sets = sessions.getAllSets().sortedBy { it.id }.map { it.toDto() },
                    cardio = sessions.getAllCardio().sortedBy { it.id }.map { it.toDto() },
                    heart = db.sessionHeartDao().getAll().sortedBy { it.sessionId }.map { it.toDto() },
                ),
                bodyWeight = db.bodyWeightDao().getAll().sortedBy { it.id }.map { it.toDto() },
                nutrition = NutritionBackup(
                    profile = db.profileDao().get()?.toDto(),
                    foods = db.foodDao().getAll().sortedBy { it.id }.map { it.toDto() },
                    meals = db.mealDao().getAll().sortedBy { it.id }.map { it.toDto() },
                    supplements = db.supplementDao().getAll().sortedBy { it.id }.map { it.toDto() },
                    supplementIntakes = db.supplementDao().getAllIntakes().sortedBy { it.id }.map { it.toDto() },
                    dishes = db.dishDao().getAll().sortedBy { it.foodId }.map { it.toDto() },
                    dishIngredients = db.dishDao().getAllIngredients().sortedBy { it.id }.map { it.toDto() },
                ),
                badges = db.badgeDao().getAll().sortedWith(compareBy({ it.id }, { it.tier })).map { it.toDto() },
                challenges = db.badgeDao().getChallenges().map { it.toDto() },
                appearance = appearance,
            )
        }
    }

    /** Replaces the whole database content with [file], atomically (all or nothing). Ciqual is left untouched. */
    suspend fun restore(file: BackupFile) {
        db.withTransaction {
            db.sessionDao().deleteAllSessions() // cascades to session lines, sets, cardio and heart rate
            db.templateDao().deleteAll() // cascades to template lines
            db.exerciseDao().deleteAll()
            db.bodyWeightDao().deleteAll()
            db.mealDao().deleteAll()
            db.supplementDao().deleteAll() // cascades to the days taken
            db.foodDao().deleteAll() // cascades to dishes and their ingredients
            db.profileDao().deleteAll()
            db.badgeDao().deleteAll()
            db.badgeDao().deleteAllChallenges()

            val sport = file.sport
            db.exerciseDao().insertAll(sport.exercises.map { it.toEntity() })
            db.templateDao().insertAll(sport.templates.map { it.toEntity() })
            db.templateDao().insertLines(sport.templateExercises.map { it.toEntity() })
            db.sessionDao().insertSessions(sport.sessions.map { it.toEntity() })
            db.sessionDao().insertSessionExercises(sport.sessionExercises.map { it.toEntity() })
            db.sessionDao().insertSets(sport.sets.map { it.toEntity() })
            db.sessionDao().insertCardios(sport.cardio.map { it.toEntity() })
            db.sessionHeartDao().insertAll(sport.heart.map { it.toEntity() })
            db.bodyWeightDao().insertAll(file.bodyWeight.map { it.toEntity() })
            val nutrition = file.nutrition
            nutrition.profile?.let { db.profileDao().upsert(it.toEntity()) }
            db.foodDao().insertAll(nutrition.foods.map { it.toEntity() })
            db.mealDao().insertAll(nutrition.meals.map { it.toEntity() })
            db.dishDao().insertAll(nutrition.dishes.map { it.toEntity() })
            db.dishDao().insertIngredients(nutrition.dishIngredients.map { it.toEntity() })
            val (supplements, intakes) = nutrition.supplementsToRestore(file.schemaVersion)
            db.supplementDao().insertAll(supplements)
            db.supplementDao().insertIntakes(intakes)
            db.badgeDao().insertAll(file.badges.map { it.toEntity(file.schemaVersion) })
            db.badgeDao().insertChallenges(file.challenges.map { it.toEntity() })
        }
        file.appearance?.let { settings.setAppearance(it.toAppearance()) }
    }

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val text = BackupCodec.encode(snapshot())
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray()) }
            ?: error("Impossible d'écrire le fichier")
    }

    /** Validates the file, writes a safety backup of the current data, then replaces everything. */
    suspend fun importFrom(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
            ?: error("Impossible de lire le fichier")
        val file = BackupCodec.decode(text)
        val safety = writeSafetyBackup()
        restore(file)
        ImportResult(file.sport.sessions.count { !it.isSeed }, file.sport.exercises.size, safety)
    }

    /** Stored in Android/data/<package>/files/backups (reachable with `adb pull`); keeps the last 5. */
    private suspend fun writeSafetyBackup(): File {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, "backups").apply { mkdirs() }
        val stamp = LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val target = File(dir, "${SAFETY_PREFIX}$stamp.json")
        target.writeText(BackupCodec.encode(snapshot()))
        // Copies made under the app's former names (Forme, GymBro) are pruned with the new ones, oldest first.
        dir.listFiles { f -> f.name.startsWith(SAFETY_PREFIX) || OLD_SAFETY_PREFIXES.any { f.name.startsWith(it) } }
            ?.sortedByDescending { it.name.substringAfter("-avant-import-") }
            ?.drop(SAFETY_KEEP)
            ?.forEach { it.delete() }
        return target
    }

    fun suggestedFileName(): String = "gymflemme-sauvegarde-${LocalDateTime.now(clock).toLocalDate()}.json"

    private companion object {
        const val SAFETY_PREFIX = "gymflemme-avant-import-"
        val OLD_SAFETY_PREFIXES = listOf("forme-avant-import-", "gymbro-avant-import-")
        const val SAFETY_KEEP = 5
    }
}
