package fr.olivier.forme.core.db

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the exported schema (app/schemas/.../1.json) against the entities: "migration 1 → 1".
 * Every future migration (1 → 2 for Nutrition) gets its own test here.
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
    fun schemaVersion1MatchesEntities() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, FormeDatabase.VERSION, true, *Migrations.ALL).close()
    }

    @Test
    fun databaseCreatedFromSchema1OpensWithTheRealBuilder() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO exercise (id, name, muscleGroup, type, perSide, loadIncrementKg, defaultTargetSets, archived) VALUES (1, 'Test', 'DOS', 'WEIGHTED', 0, 2.5, 3, 0)")
            close()
        }
        val db = Room.databaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FormeDatabase::class.java, dbName)
            .addMigrations(*Migrations.ALL)
            .allowMainThreadQueries()
            .build()
        assertEquals(1, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM exercise").use { it.moveToFirst(); it.getInt(0) })
        db.close()
    }
}
