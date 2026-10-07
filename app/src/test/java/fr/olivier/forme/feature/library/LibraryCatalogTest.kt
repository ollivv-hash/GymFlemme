package fr.olivier.forme.feature.library

import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.db.context
import fr.olivier.forme.feature.library.data.ExerciseLibraryLoader
import fr.olivier.forme.feature.library.domain.LibraryCatalog
import fr.olivier.forme.feature.library.domain.LibraryFilter
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** V2.10 library screen: filters and the exercise created by "Ajouter à mon catalogue". */
@RunWith(AndroidJUnit4::class)
class LibraryCatalogTest {
    private val rows by lazy { context().assets.open("exercise_library/exercises.json").use(ExerciseLibraryLoader::parse) }
    private val byId by lazy { rows.associateBy { it.id } }

    @Test
    fun filtersCombineWithTheSearch() {
        val quadMachines = LibraryCatalog.filter(rows, LibraryFilter(muscle = "quads", place = "machines"))
        assertTrue(quadMachines.isNotEmpty())
        assertTrue(quadMachines.all { "quads" in it.primaryList && "machines" in it.suitableForList })
        assertTrue(quadMachines.any { it.id == "leg-press" })

        val bodyweight = LibraryCatalog.filter(rows, LibraryFilter(equipment = LibraryFilter.BODYWEIGHT))
        assertTrue(bodyweight.isNotEmpty() && bodyweight.all { it.equipmentList.isEmpty() })

        val curls = LibraryCatalog.filter(rows, LibraryFilter(query = "curl", muscle = "biceps", equipment = "dumbbell"))
        assertTrue(curls.any { it.id == "hammer-curls" })
        assertTrue(curls.all { "dumbbell" in it.equipmentList })
        assertEquals(rows.size, LibraryCatalog.filter(rows, LibraryFilter()).size)
        assertFalse(LibraryFilter(query = "x").active)
    }

    @Test
    fun filterChoicesComeFromTheLibrary() {
        val muscles = LibraryCatalog.muscles(rows)
        assertEquals("chest", muscles.first()) // French label order
        assertTrue(muscles.containsAll(rows.flatMap { it.primaryList }.distinct()))
        val equipment = LibraryCatalog.equipment(rows)
        assertEquals(LibraryFilter.BODYWEIGHT, equipment.first())
        assertEquals("Poids du corps", LibraryCatalog.equipmentLabel(equipment.first()))
        assertTrue(equipment.containsAll(rows.flatMap { it.equipmentList }.distinct()))
    }

    @Test
    fun addedExerciseIsLinkedAndGetsTargetsForItsType() {
        val legPress = LibraryCatalog.newExercise(byId.getValue("leg-press"))
        assertEquals("Leg Press", legPress.name)
        assertEquals(MuscleGroup.JAMBES, legPress.muscleGroup)
        assertEquals(ExerciseType.WEIGHTED, legPress.type)
        assertEquals("leg-press", legPress.libraryId)
        assertEquals(10, legPress.defaultRepsMin)
        assertEquals(0L, legPress.id) // inserted as a new exercise

        val rower = LibraryCatalog.newExercise(byId.getValue("rowing-machine"))
        assertEquals(ExerciseType.CARDIO, rower.type)
        assertEquals(MuscleGroup.CARDIO, rower.muscleGroup)
        assertEquals(1, rower.defaultTargetSets)
        assertEquals(20 * 60, rower.defaultDurationSec)

        val sidePlank = LibraryCatalog.newExercise(byId.getValue("knee-side-plank"))
        assertEquals(ExerciseType.TIMED, sidePlank.type)
        assertTrue(sidePlank.perSide) // unilateral hold: the duration applies to each side
    }
}
