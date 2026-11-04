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
import fr.olivier.forme.feature.nutrition.domain.DishIngredient
import fr.olivier.forme.feature.nutrition.domain.Per100
import fr.olivier.forme.feature.nutrition.data.DishEntity
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
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
            db, db.profileDao(), db.ciqualDao(), db.foodDao(), db.mealDao(), db.supplementDao(), db.sessionDao(),
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

    /**
     * V2.21: a dish is a food whose values per 100 g come from its ingredients (of the cooked dish when weighed),
     * with one serving as its portion; it keeps them when an ingredient's food is deleted, and goes with its food.
     */
    @Test
    fun aDishIsAFoodComputedFromItsIngredients() = runTest {
        val rice = DishIngredient("Riz", 100.0, Per100(7.0, 350.0, 77.0, 1.0))
        val chicken = DishIngredient("Poulet", 150.0, Per100(30.0, 140.0, 0.0, 2.0))
        val id = repo.saveDish(0, " Bol riz poulet ", listOf(rice, chicken), cookedWeightG = 500.0, servings = 2)

        val food = repo.food(id)!!
        assertEquals(FoodSource.DISH, food.source)
        assertEquals("Bol riz poulet", food.name)
        assertEquals((7.0 + 45.0) / 5, food.proteinPer100g, 1e-9) // 52 g in 500 g cooked
        assertEquals((350.0 + 210.0) / 5, food.kcalPer100g!!, 1e-9)
        assertEquals(250.0, food.defaultPortionG!!, 1e-9)
        val dish = repo.dish(id)!!
        assertEquals(listOf(rice, chicken), dish.ingredients)
        assertEquals(DishEntity(id, 500.0, 2), dish.dish)

        // Edited: without cooked weight, the raw ingredients (250 g) are the reference.
        repo.saveDish(id, "Bol riz poulet", listOf(rice, chicken), cookedWeightG = null, servings = 1)
        assertEquals(52.0 / 2.5, repo.food(id)!!.proteinPer100g, 1e-9)
        assertEquals(250.0, repo.food(id)!!.defaultPortionG!!, 1e-9)
        assertEquals(1, db.foodDao().getAll().size)

        repo.deleteFood(id)
        assertNull(repo.dish(id))
        assertEquals(0, db.dishDao().getAllIngredients().size)
    }

    /** V2.21: "Enregistrer comme plat": the meal's foods, whey left apart, with their values per 100 g. */
    @Test
    fun aMealBecomesTheIngredientsOfADish() = runTest {
        val skyr = repo.food(repo.saveFood(FoodEntity(source = FoodSource.CUSTOM, name = "Skyr", proteinPer100g = 10.0, kcalPer100g = 60.0)))!!
        repo.addFood(skyr, day, MealType.BREAKFAST, 150.0)
        repo.addFood(skyr, day, MealType.LUNCH, 100.0)
        db.mealDao().insert(
            MealEntryEntity(
                date = day, mealType = MealType.BREAKFAST, foodId = null, name = "Whey", quantityG = 30.0, proteinG = 24.0,
                kcal = 114.0, carbsG = null, fatG = null, isWhey = true, eatenAt = clock.instant(),
            ),
        )
        val ingredients = repo.mealAsIngredients(day, MealType.BREAKFAST)
        assertEquals(listOf("Skyr"), ingredients.map { it.name })
        assertEquals(150.0, ingredients.single().grams, 0.0)
        assertEquals(10.0, ingredients.single().per100.proteinG, 1e-9)
        assertEquals(60.0, ingredients.single().per100.kcal!!, 1e-9)
    }
}
