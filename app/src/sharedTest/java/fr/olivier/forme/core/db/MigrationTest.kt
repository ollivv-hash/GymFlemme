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
    fun migrate1To3() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, 3, true, *Migrations.ALL).close()
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
