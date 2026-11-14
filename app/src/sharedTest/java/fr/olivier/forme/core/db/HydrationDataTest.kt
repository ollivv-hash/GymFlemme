package fr.olivier.forme.core.db

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.feature.bodyweight.data.BodyWeightEntity
import fr.olivier.forme.feature.hydration.data.HydrationRepository
import fr.olivier.forme.feature.hydration.data.HydrationSessionListener
import fr.olivier.forme.feature.hydration.data.HydrationSettingsRepository
import fr.olivier.forme.feature.hydration.data.IntakeSource
import fr.olivier.forme.feature.hydration.domain.GoalSettings
import fr.olivier.forme.feature.nutrition.data.FoodEntity
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.OpenFoodFactsClient
import fr.olivier.forme.feature.nutrition.domain.FoodSource
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HydrationDataTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** 2026-10-01 18:00 in Paris. */
    private val clock = TestClock()
    private val db = testDatabase(clock)
    private val today = LocalDate.of(2026, 10, 1)

    @After
    fun close() = db.close()

    private fun TestScope.settings() =
        HydrationSettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "water.preferences_pb") })

    private fun TestScope.repo(settings: HydrationSettingsRepository = settings()) = HydrationRepository(db, db.hydrationDao(), settings, clock)

    private fun sessions(hydration: HydrationRepository) =
        SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock, HydrationSessionListener(hydration))

    private suspend fun weigh(kg: Double, date: LocalDate = today) = db.bodyWeightDao().upsert(BodyWeightEntity(date = date, weightKg = kg))

    @Test
    fun theDefaultContainerAddsItsDrinkAndTheCoefficientIsCopied() = runTest {
        val hydration = repo()
        val added = hydration.addDefault(IntakeSource.HOME_TILE)!!
        assertEquals(750, added.volumeMl)
        val intake = hydration.intake(added.id)!!
        assertEquals(1.0, intake.coefficientApplied, 0.0)
        assertEquals(today, intake.date)
        assertEquals(IntakeSource.HOME_TILE, intake.source)

        // The beer counts 0; changing the coefficient of a drink afterwards never changes an intake.
        val beer = db.hydrationDao().getDrinkByCode("beer")!!
        val beerIntake = hydration.add(beer.id, 330, IntakeSource.WATER_SCREEN).id
        hydration.saveDrink(beer.copy(hydrationCoefficient = 0.5))
        assertEquals(0.0, hydration.intake(beerIntake)!!.coefficientApplied, 0.0)
        assertEquals(750, hydration.todayState().countedMl)
        val half = hydration.add(beer.id, 330, IntakeSource.WATER_SCREEN).id
        assertEquals(0.5, hydration.intake(half)!!.coefficientApplied, 0.0)
        assertEquals(915, hydration.todayState().countedMl)
    }

    @Test
    fun editingAnIntakeKeepsItsCoefficientUnlessTheDrinkChanges() = runTest {
        val hydration = repo()
        val tea = db.hydrationDao().getDrinkByCode("tea")!!
        val id = hydration.add(tea.id, 250, IntakeSource.WATER_SCREEN).id
        hydration.saveDrink(tea.copy(hydrationCoefficient = 0.8))
        hydration.update(id, tea.id, 300, clock.instant())
        assertEquals(1.0, hydration.intake(id)!!.coefficientApplied, 0.0)
        assertEquals(300, hydration.intake(id)!!.volumeMl)
        val wine = db.hydrationDao().getDrinkByCode("wine")!!
        hydration.update(id, wine.id, 300, clock.instant())
        assertEquals(0.0, hydration.intake(id)!!.coefficientApplied, 0.0)
        // Moved to the day before: counted there.
        hydration.update(id, tea.id, 300, clock.instant().minus(Duration.ofDays(1)))
        assertEquals(today.minusDays(1), hydration.intake(id)!!.date)
        assertEquals(0, hydration.todayState().countedMl)
    }

    @Test
    fun theDayIsTheLocalOne() = runTest {
        val hydration = repo()
        // 23:30 in Paris is 21:30 UTC: still the 1st.
        val late = Instant.parse("2026-10-01T21:30:00Z")
        val id = hydration.add(1, 250, IntakeSource.WATER_SCREEN, at = late).id
        assertEquals(today, hydration.intake(id)!!.date)
        // 00:10 in Paris on the 2nd.
        val next = hydration.add(1, 250, IntakeSource.WATER_SCREEN, at = Instant.parse("2026-10-01T22:10:00Z")).id
        assertEquals(today.plusDays(1), hydration.intake(next)!!.date)
    }

    @Test
    fun theGoalFollowsTheLastWeightAndIsFrozenForThePastDays() = runTest {
        val settings = settings()
        val hydration = repo(settings)
        assertEquals(2_000, hydration.todayState().goal.totalMl)
        weigh(82.0)
        assertEquals(2_900, hydration.todayState().goal.totalMl)
        hydration.add(1, 250, IntakeSource.WATER_SCREEN)
        assertEquals(2_900, db.hydrationDao().getWaterDay(today)!!.goalMl)

        // The next day, a new weight and a goal of one's own: yesterday keeps 2.9 L.
        clock.advance(Duration.ofDays(1))
        weigh(70.0, today.plusDays(1))
        hydration.setGoal(GoalSettings(customEnabled = true, customMl = 3_000))
        assertEquals(3_000, hydration.todayState().goal.totalMl)
        assertEquals(2_500, hydration.todayState().goal.computedMl)
        assertEquals(2_900, db.hydrationDao().getWaterDay(today)!!.goalMl)
        assertEquals(mapOf(today to 2_900, today.plusDays(1) to 3_000), hydration.goalsByDay())
    }

    @Test
    fun aSessionStartedGivesTheBonusOnceAndTheGoalReachedIsAnnouncedOnce() = runTest {
        val hydration = repo()
        weigh(82.0)
        val sessions = sessions(hydration)
        sessions.start(null)
        assertEquals(3_400, hydration.todayState().goal.totalMl)
        assertEquals(500, hydration.observeGoal().first().bonusMl)
        // A second session the same day: still one bonus.
        sessions.finish(db.sessionDao().getActiveSession()!!.id, null, null)
        clock.advance(Duration.ofMinutes(5))
        sessions.start(null)
        assertEquals(3_400, hydration.todayState().goal.totalMl)

        val first = hydration.add(1, 2_000, IntakeSource.WATER_SCREEN)
        assertFalse(first.goalJustReached)
        val second = hydration.add(1, 1_500, IntakeSource.WATER_SCREEN)
        assertTrue(second.goalJustReached)
        assertTrue(second.today.reached)
        assertFalse(hydration.add(1, 250, IntakeSource.WATER_SCREEN).goalJustReached)
    }

    @Test
    fun theBonusFollowsItsSettingOnTheDayItself() = runTest {
        val settings = settings()
        val hydration = repo(settings)
        weigh(82.0)
        sessions(hydration).start(null)
        hydration.setGoal(GoalSettings(bonusEnabled = true, bonusMl = 1_000))
        assertEquals(3_900, hydration.todayState().goal.totalMl)
        hydration.setGoal(GoalSettings(bonusEnabled = false))
        assertEquals(2_900, hydration.todayState().goal.totalMl)
        assertEquals(2_900, db.hydrationDao().getWaterDay(today)!!.goalMl)
    }

    @Test
    fun aSessionAbandonedWithoutAnySetTakesTheBonusBack() = runTest {
        val hydration = repo()
        weigh(82.0)
        val sessions = sessions(hydration)
        val id = sessions.start(null)
        sessions.abandon(id)
        assertNull(db.sessionDao().getSession(id))
        assertEquals(2_900, hydration.todayState().goal.totalMl)
    }

    @Test
    fun aSessionAbandonedAfterASetKeepsTheBonus() = runTest {
        val hydration = repo()
        weigh(82.0)
        val sessions = sessions(hydration)
        val id = sessions.start(null)
        db.sessionDao().insertSets(listOf(SetEntryEntity(sessionId = id, exerciseId = 2, setIndex = 0, loadKg = 20.0, reps = 10, completedAt = clock.instant())))
        sessions.abandon(id)
        assertEquals(3_400, hydration.todayState().goal.totalMl)
    }

    @Test
    fun aSessionAbandonedKeepsTheBonusOfAnotherSessionThatDay() = runTest {
        val hydration = repo()
        weigh(82.0)
        val sessions = sessions(hydration)
        sessions.finish(sessions.start(null), null, null)
        clock.advance(Duration.ofHours(1))
        sessions.abandon(sessions.start(null))
        assertEquals(3_400, hydration.todayState().goal.totalMl)
    }

    @Test
    fun deletingAnIntakeDeletesItsMealEntryAndTheOtherWayRound() = runTest {
        val hydration = repo()
        val juice = db.hydrationDao().getDrinkByCode("juice")!!
        suspend fun linkedIntake(): Long {
            val id = hydration.add(juice.id, 200, IntakeSource.WATER_SCREEN).id
            val meal = db.mealDao().insert(
                MealEntryEntity(date = today, mealType = MealType.DINNER, foodId = null, name = "Jus", quantityG = 200.0, proteinG = 1.0, kcal = 90.0, carbsG = 20.0, fatG = 0.0, eatenAt = clock.instant()),
            )
            db.hydrationDao().updateIntake(hydration.intake(id)!!.copy(linkedMealEntryId = meal))
            return id
        }
        val first = linkedIntake()
        hydration.delete(first)
        assertEquals(emptyList<MealEntryEntity>(), db.mealDao().getDay(today))

        val second = linkedIntake()
        db.mealDao().delete(hydration.intake(second)!!.linkedMealEntryId!!)
        assertNull(hydration.intake(second))
    }

    @Test
    fun onlyOneContainerIsTheDefault() = runTest {
        val hydration = repo()
        val glass = db.hydrationDao().getContainers().first { it.name == "Verre" }
        hydration.saveContainer(glass.copy(isDefault = true))
        assertEquals(listOf("Verre"), db.hydrationDao().getContainers().filter { it.isDefault }.map { it.name })
        assertEquals(250, hydration.addDefault(IntakeSource.HOME_TILE)!!.volumeMl)
    }

    private suspend fun juiceLinked(hydration: HydrationRepository): Long {
        val juice = db.hydrationDao().getDrinkByCode("juice")!!
        val food = db.foodDao().insert(FoodEntity(source = FoodSource.CUSTOM, name = "Jus d'orange", kcalPer100g = 45.0, proteinPer100g = 0.7, carbsPer100g = 10.0, fatPer100g = 0.2))
        assertTrue(hydration.needsFood(juice.id))
        hydration.linkFood(juice.id, food)
        assertFalse(hydration.needsFood(juice.id))
        return juice.id
    }

    @Test
    fun aLinkedCaloricDrinkAddsItsMealEntryInTheMealOfThatTime() = runTest {
        val hydration = repo()
        val juice = juiceLinked(hydration)
        val id = hydration.add(juice, 200, IntakeSource.WATER_SCREEN).id
        val meal = db.mealDao().get(hydration.intake(id)!!.linkedMealEntryId!!)!!
        assertEquals(MealType.SNACK, meal.mealType) // 18:00 in Paris
        assertEquals(today, meal.date)
        assertEquals(200.0, meal.quantityG, 0.0)
        assertEquals(90.0, meal.kcal!!, 1e-9)
        assertEquals(1.4, meal.proteinG, 1e-9)
        assertEquals("Jus d'orange", meal.name)
        // Water and an unlinked caloric drink: no meal entry.
        assertNull(hydration.intake(hydration.add(1, 250, IntakeSource.WATER_SCREEN).id)!!.linkedMealEntryId)
        val soda = db.hydrationDao().getDrinkByCode("soda")!!.id
        assertNull(hydration.intake(hydration.add(soda, 330, IntakeSource.WATER_SCREEN).id)!!.linkedMealEntryId)
    }

    @Test
    fun editingTheIntakeEditsItsMealEntryAndTheOtherWayRound() = runTest {
        val hydration = repo()
        val juice = juiceLinked(hydration)
        val id = hydration.add(juice, 200, IntakeSource.WATER_SCREEN).id
        val mealId = hydration.intake(id)!!.linkedMealEntryId!!

        // The intake: volume and time; the meal entry follows (same meal).
        hydration.update(id, juice, 300, clock.instant().minus(Duration.ofDays(1)))
        val moved = db.mealDao().get(mealId)!!
        assertEquals(300.0, moved.quantityG, 0.0)
        assertEquals(135.0, moved.kcal!!, 1e-9)
        assertEquals(today.minusDays(1), moved.date)

        // The meal entry: its quantity changes the intake.
        nutrition().updateQuantity(moved, 250.0)
        assertEquals(250, hydration.intake(id)!!.volumeMl)

        // Another drink, not caloric: the meal entry goes.
        hydration.update(id, 1, 250, clock.instant())
        assertNull(db.mealDao().get(mealId))
        assertNull(hydration.intake(id)!!.linkedMealEntryId)
        // Back to the juice: a new entry.
        hydration.update(id, juice, 250, clock.instant())
        assertEquals(250.0, db.mealDao().get(hydration.intake(id)!!.linkedMealEntryId!!)!!.quantityG, 0.0)
    }

    @Test
    fun undoingADrinkTakesItsMealEntryAndDeletingTheEntryTakesTheDrink() = runTest {
        val hydration = repo()
        val juice = juiceLinked(hydration)
        val first = hydration.add(juice, 200, IntakeSource.HOME_TILE).id
        hydration.delete(first) // "Annuler"
        assertEquals(emptyList<MealEntryEntity>(), db.mealDao().getDay(today))
        val second = hydration.add(juice, 200, IntakeSource.HOME_TILE).id
        nutrition().deleteEntry(hydration.intake(second)!!.linkedMealEntryId!!)
        assertNull(hydration.intake(second))
    }

    @Test
    fun notCountingTheCaloriesMakesTheDrinkNonCaloric() = runTest {
        val hydration = repo()
        val milk = db.hydrationDao().getDrinkByCode("milk")!!.id
        assertTrue(hydration.needsFood(milk))
        hydration.dontCountCalories(milk)
        assertFalse(hydration.needsFood(milk))
        assertFalse(hydration.drink(milk)!!.isCaloric)
        assertNull(hydration.intake(hydration.add(milk, 200, IntakeSource.WATER_SCREEN).id)!!.linkedMealEntryId)
    }

    private fun nutrition() = NutritionRepository(
        db, db.profileDao(), db.ciqualDao(), db.foodDao(), db.mealDao(), db.supplementDao(), db.sessionDao(), OpenFoodFactsClient(), clock,
    )
}
