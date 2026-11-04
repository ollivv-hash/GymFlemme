package fr.olivier.forme.core.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates every exported schema against the entities and every migration path.
 * Schema files: app/schemas/fr.olivier.forme.core.db.FormeDatabase/<version>.json.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FormeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun schemaVersion1IsValid() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, 1, true).close()
    }

    @Test
    fun migrate1To2KeepsSportDataAndCreatesNutritionTables() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO exercise (id, name, muscleGroup, type, perSide, loadIncrementKg, defaultTargetSets, archived) VALUES (1, 'Test', 'DOS', 'WEIGHTED', 0, 2.5, 3, 0)")
            execSQL("INSERT INTO body_weight_entry (id, date, weightKg) VALUES (1, 20000, 81.5)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 2, true, Migrations.MIGRATION_1_2).apply {
            query("SELECT COUNT(*) FROM exercise").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            query("SELECT COUNT(*) FROM meal_entry").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            close()
        }
    }

    @Test
    fun migrate2To3AddsSupersetReplacementAndNoteColumns() {
        helper.createDatabase(dbName, 2).apply {
            execSQL("INSERT INTO exercise (id, name, muscleGroup, type, perSide, loadIncrementKg, defaultTargetSets, archived) VALUES (1, 'Test', 'DOS', 'WEIGHTED', 0, 2.5, 3, 0)")
            execSQL("INSERT INTO meal_entry (id, date, mealType, name, quantityG, proteinG, isWhey, eatenAt) VALUES (1, 20000, 'LUNCH', 'Skyr', 150, 15, 0, 0)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 3, true, Migrations.MIGRATION_2_3).apply {
            query("SELECT nextNote FROM exercise WHERE id = 1").use { it.moveToFirst(); assertTrue(it.isNull(0)) }
            query("SELECT COUNT(*) FROM meal_entry").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            close()
        }
    }

    @Test
    fun migrate3To4AddsHeartTableAndProfileColumns() {
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO profile (id, proteinTargetGPerKg, wheyScoopG, creatineDoseG, creatineReminderEnabled, creatineReminderMinutes) VALUES (1, 2.0, 30, 5, 1, 1200)")
            execSQL("INSERT INTO session (id, templateNameSnapshot, startedAt, isSeed) VALUES (7, 'C', 1000, 0)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 4, true, Migrations.MIGRATION_3_4).apply {
            query("SELECT proteinTargetGPerKg, birthYear, sex, hrMaxOverride FROM profile WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(2.0, it.getDouble(0), 0.0)
                assertTrue(it.isNull(1) && it.isNull(2) && it.isNull(3))
            }
            execSQL("INSERT INTO session_heart (sessionId, source, seriesStart, intervalSec, bpmSeries, avgBpm, minBpm, maxBpm, zone1Sec, zone2Sec, zone3Sec, zone4Sec, zone5Sec) VALUES (7, 'BLE', 1000, 5, '90,100', 95, 90, 100, 10, 0, 0, 0, 0)")
            execSQL("PRAGMA foreign_keys = ON")
            execSQL("DELETE FROM session WHERE id = 7")
            query("SELECT COUNT(*) FROM session_heart").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            close()
        }
    }

    @Test
    fun migrate4To5AddsCalorieTargetColumns() {
        helper.createDatabase(dbName, 4).apply {
            execSQL("INSERT INTO profile (id, proteinTargetGPerKg, wheyScoopG, creatineDoseG, creatineReminderEnabled, creatineReminderMinutes, birthYear) VALUES (1, 2.0, 30, 5, 1, 1200, 1980)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 5, true, Migrations.MIGRATION_4_5).apply {
            query("SELECT birthYear, heightCm, nutritionGoal, kcalTargetOverride FROM profile WHERE id = 1").use {
                it.moveToFirst()
                assertEquals(1980, it.getInt(0))
                assertTrue(it.isNull(1) && it.isNull(2) && it.isNull(3))
            }
            close()
        }
    }

    @Test
    fun migrate5To6LinksKnownExercisesToTheLibrary() {
        helper.createDatabase(dbName, 5).apply {
            listOf("Leg press", "Élévations latérales", "Lower back", "Mon exercice", "leg press").forEachIndexed { i, name ->
                execSQL(
                    "INSERT INTO exercise (id, name, muscleGroup, type, perSide, loadIncrementKg, defaultTargetSets, archived) " +
                        "VALUES (${i + 1}, '$name', 'JAMBES', 'WEIGHTED', 0, 2.5, 3, 0)",
                )
            }
            close()
        }
        helper.runMigrationsAndValidate(dbName, 6, true, Migrations.MIGRATION_5_6).apply {
            query("SELECT name, libraryId FROM exercise ORDER BY id").use {
                val links = buildList { while (it.moveToNext()) add(it.getString(0) to it.getString(1)) }
                assertEquals(
                    listOf(
                        "Leg press" to "seated-leg-press",
                        "Élévations latérales" to "lateral-raises",
                        "Lower back" to "back-extensions",
                        // Unknown, or not the exact name: left unlinked (the catalogue shows "sans illustration").
                        "Mon exercice" to null,
                        "leg press" to null,
                    ),
                    links,
                )
            }
            query("SELECT COUNT(*) FROM library_exercise").use {
                it.moveToFirst()
                assertEquals(0, it.getInt(0)) // filled from the APK at startup, not by the migration
            }
            close()
        }
    }

    @Test
    fun migrate6To7CreatesAnEmptyBadgeTable() {
        helper.createDatabase(dbName, 6).apply {
            execSQL("INSERT INTO body_weight_entry (id, date, weightKg) VALUES (1, 20000, 81.5)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 7, true, Migrations.MIGRATION_6_7).apply {
            query("SELECT COUNT(*) FROM badge").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            query("SELECT COUNT(*) FROM body_weight_entry").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            close()
        }
    }

    @Test
    fun migrate7To8ConvertsTheV1BadgesIntoTiersWithoutLoss() {
        helper.createDatabase(dbName, 7).apply {
            listOf("NOT_DEAD" to 0, "REPEAT_OFFENDER" to 1, "PILLAR" to 0, "BEATS_THE_COUCH" to 1, "EARLY_BIRD" to 1, "ALL_ROUNDER" to 1, "CREATINE_LOYAL" to 0)
                .forEachIndexed { i, (id, seen) -> execSQL("INSERT INTO badge (id, earnedAt, seen) VALUES ('$id', ${20_000 + i}, $seen)") }
            close()
        }
        helper.runMigrationsAndValidate(dbName, 8, true, Migrations.MIGRATION_7_8).apply {
            val rows = mutableListOf<String>()
            query("SELECT id, tier, earnedAt, seen FROM badge ORDER BY earnedAt").use { c ->
                while (c.moveToNext()) rows += "${c.getString(0)}/${c.getInt(1)}/${c.getLong(2)}/${c.getInt(3)}"
            }
            assertEquals(
                listOf(
                    "NOT_DEAD/0/20000/0",
                    "REPEAT_OFFENDER/1/20001/1",
                    "REPEAT_OFFENDER/2/20002/0", // Pilier de la salle → Récidiviste Argent
                    "BEATS_THE_COUCH/1/20003/1",
                    "EARLY_BIRD/0/20004/1",
                    "ALL_ROUNDER/1/20005/1",
                    "CREATINE_LOYAL/2/20006/0", // 30 days → Argent
                ),
                rows,
            )
            close()
        }
    }

    @Test
    fun migrate8To9CreatesAnEmptyChallengeTableAndKeepsTheBadges() {
        helper.createDatabase(dbName, 8).apply {
            execSQL("INSERT INTO badge (id, tier, earnedAt, seen) VALUES ('REPEAT_OFFENDER', 1, 20000, 1)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 9, true, Migrations.MIGRATION_8_9).apply {
            query("SELECT COUNT(*) FROM monthly_challenge").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
            query("SELECT COUNT(*) FROM badge").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
            close()
        }
    }

    /** V2.20: creatine becomes the first supplement, with its dose, reminder and days; the others start off. */
    @Test
    fun migrate9To10MovesCreatineIntoTheSupplements() {
        helper.createDatabase(dbName, 9).apply {
            execSQL(
                "INSERT INTO profile (id, proteinTargetGPerKg, wheyScoopG, creatineDoseG, creatineReminderEnabled, creatineReminderMinutes, birthYear) " +
                    "VALUES (1, 2.0, 30, 3.5, 0, 450, 1980)",
            )
            execSQL("INSERT INTO creatine_entry (date, doseG, takenAt) VALUES (20000, 5, 1728000000000), (20001, 3.5, 1728086400000)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 10, true, Migrations.MIGRATION_9_10).apply {
            query("SELECT name, amount, unit, active, reminderEnabled, reminderMinutes FROM supplement WHERE code = 'creatine'").use {
                it.moveToFirst()
                assertEquals("Créatine", it.getString(0))
                assertEquals(3.5, it.getDouble(1), 0.0)
                assertEquals("G", it.getString(2))
                assertEquals(1, it.getInt(3))
                assertEquals(0, it.getInt(4))
                assertEquals(450, it.getInt(5))
            }
            query("SELECT COUNT(*), SUM(active) FROM supplement").use {
                it.moveToFirst()
                assertEquals(7, it.getInt(0))
                assertEquals(1, it.getInt(1))
            }
            query(
                "SELECT i.date, i.amount FROM supplement_intake i JOIN supplement s ON s.id = i.supplementId " +
                    "WHERE s.code = 'creatine' ORDER BY i.date",
            ).use {
                assertEquals(2, it.count)
                it.moveToFirst(); assertEquals(20000, it.getInt(0)); assertEquals(5.0, it.getDouble(1), 0.0)
                it.moveToNext(); assertEquals(20001, it.getInt(0)); assertEquals(3.5, it.getDouble(1), 0.0)
            }
            query("SELECT proteinTargetGPerKg, birthYear FROM profile WHERE id = 1").use {
                it.moveToFirst(); assertEquals(2.0, it.getDouble(0), 0.0); assertEquals(1980, it.getInt(1))
            }
            close()
        }
    }

    @Test
    fun migrate9To10WithoutProfileUsesTheDefaults() {
        helper.createDatabase(dbName, 9).close()
        helper.runMigrationsAndValidate(dbName, 10, true, Migrations.MIGRATION_9_10).apply {
            query("SELECT amount, reminderEnabled, reminderMinutes FROM supplement WHERE code = 'creatine'").use {
                it.moveToFirst()
                assertEquals(5.0, it.getDouble(0), 0.0)
                assertEquals(1, it.getInt(1))
                assertEquals(1200, it.getInt(2))
            }
            close()
        }
    }

    @Test
    fun migrate1To10() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, 10, true, *Migrations.ALL).close()
    }

    @Test
    fun migratedDatabaseOpensWithTheRealBuilder() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO body_weight_entry (id, date, weightKg) VALUES (1, 20000, 81.5)")
            close()
        }
        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FormeDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            assertEquals(81.5, db.bodyWeightDao().getLatest()!!.weightKg, 0.0)
            assertNull(db.profileDao().get())
        }
        db.close()
    }
}
