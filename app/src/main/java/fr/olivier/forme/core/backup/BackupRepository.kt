package fr.olivier.forme.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.core.db.FormeDatabase
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
) {
    suspend fun snapshot(): BackupFile {
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
                    creatine = db.creatineDao().getAll().sortedBy { it.id }.map { it.toDto() },
                ),
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
            db.creatineDao().deleteAll()
            db.foodDao().deleteAll()
            db.profileDao().deleteAll()

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
            db.creatineDao().insertAll(nutrition.creatine.map { it.toEntity() })
        }
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
        val target = File(dir, "forme-avant-import-$stamp.json")
        target.writeText(BackupCodec.encode(snapshot()))
        dir.listFiles { f -> f.name.startsWith("forme-avant-import-") }
            ?.sortedByDescending { it.name }
            ?.drop(SAFETY_KEEP)
            ?.forEach { it.delete() }
        return target
    }

    fun suggestedFileName(): String = "forme-sauvegarde-${LocalDateTime.now(clock).toLocalDate()}.json"

    private companion object {
        const val SAFETY_KEEP = 5
    }
}
