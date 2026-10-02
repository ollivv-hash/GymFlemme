package fr.olivier.forme.core.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.SessionEntity
import fr.olivier.forme.feature.sport.data.SessionExerciseEntity
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import fr.olivier.forme.feature.sport.data.TemplateExerciseEntity
import fr.olivier.forme.feature.sport.data.WorkoutTemplateEntity
import java.time.Clock

/**
 * Writes [SeedData] synchronously when the database file is created, so the very first query
 * already sees the catalogue (no race with the UI). Raw ContentValues are used because DAOs
 * are not available inside onCreate.
 */
class SeedCallback(private val clock: Clock) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        val now = clock.instant()
        db.beginTransaction()
        try {
            SeedData.exercises.forEach { db.insertOrThrow("exercise", it.toValues()) }
            SeedData.templates.forEach { db.insertOrThrow("workout_template", it.toValues()) }
            SeedData.templateLines.forEach { db.insertOrThrow("template_exercise", it.toValues()) }
            db.insertOrThrow("session", SeedData.seedSession(now).toValues())
            SeedData.seedSessionLines().forEach { db.insertOrThrow("session_exercise", it.toValues()) }
            SeedData.seedSets(now).forEach { db.insertOrThrow("set_entry", it.toValues()) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun SupportSQLiteDatabase.insertOrThrow(table: String, values: ContentValues) {
        check(insert(table, SQLiteDatabase.CONFLICT_ABORT, values) != -1L) { "Seed insert failed in $table" }
    }

    private fun ExerciseEntity.toValues() = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("muscleGroup", muscleGroup.name)
        put("type", type.name)
        put("perSide", perSide)
        put("loadIncrementKg", loadIncrementKg)
        put("defaultTargetSets", defaultTargetSets)
        put("defaultRepsMin", defaultRepsMin)
        put("defaultRepsMax", defaultRepsMax)
        put("defaultDurationSec", defaultDurationSec)
        put("settingsNote", settingsNote)
        put("loadNote", loadNote)
        put("photoUri", photoUri)
        put("archived", archived)
    }

    private fun WorkoutTemplateEntity.toValues() = ContentValues().apply {
        put("id", id)
        put("name", name)
        put("code", code)
        put("orderInRotation", orderInRotation)
        put("archived", archived)
    }

    private fun TemplateExerciseEntity.toValues() = ContentValues().apply {
        put("templateId", templateId)
        put("exerciseId", exerciseId)
        put("position", position)
        put("targetSets", targetSets)
        put("targetRepsMin", targetRepsMin)
        put("targetRepsMax", targetRepsMax)
        put("targetDurationSec", targetDurationSec)
        put("optional", optional)
        put("restSec", restSec)
    }

    private fun SessionEntity.toValues() = ContentValues().apply {
        put("id", id)
        put("templateId", templateId)
        put("templateNameSnapshot", templateNameSnapshot)
        put("templateCodeSnapshot", templateCodeSnapshot)
        put("startedAt", startedAt.toEpochMilli())
        put("endedAt", endedAt?.toEpochMilli())
        put("feeling", feeling)
        put("notes", notes)
        put("isSeed", isSeed)
    }

    private fun SessionExerciseEntity.toValues() = ContentValues().apply {
        put("sessionId", sessionId)
        put("exerciseId", exerciseId)
        put("position", position)
        put("status", status.name)
        put("targetSets", targetSets)
        put("targetRepsMin", targetRepsMin)
        put("targetRepsMax", targetRepsMax)
        put("targetDurationSec", targetDurationSec)
        put("restSec", restSec)
        put("optional", optional)
    }

    private fun SetEntryEntity.toValues() = ContentValues().apply {
        put("sessionId", sessionId)
        put("exerciseId", exerciseId)
        put("setIndex", setIndex)
        put("loadKg", loadKg)
        put("reps", reps)
        put("durationSec", durationSec)
        put("rir", rir)
        put("isWarmup", isWarmup)
        put("completedAt", completedAt.toEpochMilli())
    }
}
