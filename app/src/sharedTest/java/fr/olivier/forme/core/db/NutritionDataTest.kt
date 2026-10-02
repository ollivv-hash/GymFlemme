package fr.olivier.forme.core.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.feature.nutrition.data.CiqualFoodEntity
import fr.olivier.forme.feature.nutrition.data.FoodChoice
import fr.olivier.forme.feature.nutrition.data.FoodEntity
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.OpenFoodFactsClient
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.domain.FoodSource
import fr.olivier.forme.feature.nutrition.domain.MealType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class NutritionDataTest {
    private lateinit var clock: TestClock
    private lateinit var db: FormeDatabase
    private lateinit var repo: NutritionRepository
    private val day = LocalDate.of(2026, 10, 1)

    @Before
    fun setUp() {
        clock = TestClock()
        db = testDatabase(clock)
        repo = NutritionRepository(
            db, db.profileDao(), db.ciqualDao(), db.foodDao(), db.mealDao(), db.creatineDao(), db.sessionDao(),
            OpenFoodFactsClient(), clock,
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun profileDefaultsTo18GramsPerKg() = runTest {
        assertEquals(1.8, repo.profile.first().proteinTargetGPerKg, 0.0)
        repo.saveProfile(ProfileEntity(proteinTargetGPerKg = 2.0))
        assertEquals(2.0, repo.currentProfile().proteinTargetGPerKg, 0.0)
    }

    @Test
    fun mealKeepsItsSnapshotWhenTheFoodChangesOrIsDeleted() = runTest {
        val id = repo.saveFood(FoodEntity(source = FoodSource.CUSTOM, name = "Skyr", proteinPer100g = 10.0, kcalPer100g = 60.0))
        val food = repo.food(id)!!
        repo.addFood(food, day, MealType.BREAKFAST, 150.0)
        repo.saveFood(repo.food(id)!!.copy(proteinPer100g = 99.0))
        repo.deleteFood(id)
        val entry = repo.observeDay(day).first().single()
        assertEquals(15.0, entry.proteinG, 1e-9)
        assertEquals(90.0, entry.kcal!!, 1e-9)
        assertNull(entry.foodId)

        repo.updateQuantity(entry, 300.0)
        assertEquals(30.0, repo.observeDay(day).first().single().proteinG, 1e-9)
    }

    @Test
    fun ciqualFoodIsAdoptedOnceAndAppearsInRecents() = runTest {
        db.ciqualDao().insertAll(listOf(CiqualFoodEntity(36018, "Poulet, filet sans peau grillé/poêlé", "viandes", 141.0, 30.1, 0.0, 2.0)))
        val choice = repo.search("poulet").single() as FoodChoice.Ciqual
        val food = repo.resolve(choice)
        repo.addFood(food, day, MealType.LUNCH, 150.0)
        assertEquals(food.id, repo.resolve(choice).id) // not adopted twice
        assertEquals(listOf("Poulet, filet sans peau grillé/poêlé"), repo.recents().first().map { it.name })
        // Once adopted, the search returns "my" food instead of the Ciqual reference.
        assertTrue(repo.search("poulet").single() is FoodChoice.Mine)
        assertEquals(45.15, repo.observeDay(day).first().single().proteinG, 1e-9)
    }

    @Test
    fun scannedProductIsServedFromTheLocalCache() = runTest {
        repo.saveFood(FoodEntity(source = FoodSource.OFF, sourceCode = "3017624010701", name = "Skyr", proteinPer100g = 10.5))
        val result = repo.lookupBarcode("3017624010701")
        assertTrue(result is NutritionRepository.BarcodeResult.Known)
    }

    @Test
    fun wheyDoseUsesProfileLabelValues() = runTest {
        assertNull(repo.addWheyDose(day, MealType.SNACK)) // not configured
        repo.saveProfile(ProfileEntity(wheyProteinPer100g = 80.0, wheyKcalPer100g = 380.0, wheyScoopG = 30.0))
        repo.addWheyDose(day, MealType.SNACK)
        val whey = repo.observeDay(day).first().single()
        assertTrue(whey.isWhey)
        assertEquals(24.0, whey.proteinG, 1e-9)
        assertEquals(114.0, whey.kcal!!, 1e-9)
    }

    @Test
    fun creatineOneEntryPerDay() = runTest {
        repo.setCreatineTaken(day, true)
        repo.setCreatineTaken(day, true)
        repo.setCreatineTaken(day.plusDays(1), true)
        assertEquals(listOf(day.plusDays(1), day), repo.creatineDates().first())
        repo.setCreatineTaken(day, false)
        assertEquals(listOf(day.plusDays(1)), repo.creatineDates().first())
    }
}
