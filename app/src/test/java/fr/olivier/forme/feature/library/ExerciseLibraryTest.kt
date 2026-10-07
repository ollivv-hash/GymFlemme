package fr.olivier.forme.feature.library

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.db.SeedData
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.context
import fr.olivier.forme.core.db.testDatabase
import fr.olivier.forme.feature.library.data.ExerciseLibraryLinks
import fr.olivier.forme.feature.library.data.ExerciseLibraryLoader
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/** V2.8: the embedded Open Exercise Illustrations library and the links of the user's exercises to it. */
@RunWith(AndroidJUnit4::class)
class ExerciseLibraryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db = testDatabase(TestClock())

    @After
    fun close() = db.close()

    private val rows by lazy { context().assets.open("exercise_library/exercises.json").use(ExerciseLibraryLoader::parse) }

    @Test
    fun everyExerciseAndVariantIsImportedWithAllItsImages() {
        assertEquals(415, rows.count { !it.isVariant })
        assertEquals(44, rows.count { it.isVariant })
        assertEquals(rows.size, rows.map { it.id }.toSet().size)
        val assets = context().assets
        val missing = rows.flatMap { row -> row.frameList.map(row::framePath) }.distinct().filter { path ->
            runCatching { assets.open(path).close() }.isFailure
        }
        assertEquals(emptyList<String>(), missing)
        assertTrue(rows.all { it.width > 0 && it.height > 0 && it.frameList.isNotEmpty() })
    }

    @Test
    fun fieldsAndVariantsKeepWhatTheLibrarySays() {
        val byId = rows.associateBy { it.id }
        val legPress = byId.getValue("leg-press")
        assertEquals(listOf("quads", "glutes"), legPress.primaryList)
        assertEquals(listOf("hamstrings", "core"), legPress.secondaryList)
        assertEquals(listOf("start", "end"), legPress.frameList)
        assertEquals("exercise_library/images/leg-press/start.webp", legPress.thumbnailPath)
        assertEquals(listOf("hold"), byId.getValue("plank").frameList)
        // A variant shows its original's images but keeps its own equipment, muscles and difference.
        val band = byId.getValue("band-good-morning")
        assertEquals("good-morning", band.imageId)
        assertEquals(listOf("band"), band.equipmentList)
        assertEquals("Resistance band", band.equipmentLabel)
        assertNotNull(band.difference)
        // …and its original's fields when it does not redefine them.
        val baithak = byId.getValue("baithak")
        assertEquals(byId.getValue("hindu-squat").primaryList, baithak.primaryList)
        assertTrue(rows.any { it.aliasList.size > 1 })
    }

    @Test
    fun theUsersExercisesLinkToExistingEntries() {
        val ids = rows.map { it.id }.toSet()
        assertEquals(23, ExerciseLibraryLinks.BY_NAME.size)
        assertEquals(emptyMap<String, String>(), ExerciseLibraryLinks.BY_NAME.filterValues { it !in ids })
        // New installs: every seeded exercise is linked, to an entry that exists.
        assertTrue(SeedData.exercises.all { it.libraryId != null && it.libraryId in ids })
    }

    @Test
    fun loaderFillsTheTableOncePerVersion() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") }
        val loader = ExerciseLibraryLoader(db, store, context())
        loader.ensureLoaded()
        assertEquals(459, db.libraryDao().count())
        loader.ensureLoaded() // same version: nothing reloaded, no duplicate
        assertEquals(459, db.libraryDao().count())
        assertEquals("Leg Press", db.libraryDao().get("leg-press")!!.name)
        // No exercise of the catalogue points to a missing entry.
        val ids = db.libraryDao().ids().toSet()
        assertTrue(db.exerciseDao().getAll().mapNotNull { it.libraryId }.all { it in ids })
    }
}
