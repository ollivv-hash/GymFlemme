package fr.olivier.forme.core.db

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import fr.olivier.forme.core.backup.BadgeDto
import fr.olivier.forme.feature.badges.data.BadgeEntity
import fr.olivier.forme.feature.badges.data.BadgeRepository
import fr.olivier.forme.feature.badges.data.ChallengeEntity
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.backup.BackupCodec
import fr.olivier.forme.core.backup.BackupRepository
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.hydration.data.DrinkEntity
import fr.olivier.forme.feature.hydration.data.HydrationDefaults
import fr.olivier.forme.feature.hydration.data.HydrationRepository
import fr.olivier.forme.feature.hydration.data.HydrationSettingsRepository
import fr.olivier.forme.feature.hydration.data.IntakeSource
import fr.olivier.forme.feature.hydration.data.ReminderSettings
import fr.olivier.forme.feature.hydration.domain.GoalSettings
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.FoodEntity
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.OpenFoodFactsClient
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.domain.FoodSource
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionGoal
import fr.olivier.forme.feature.sport.data.CardioEntryEntity
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import fr.olivier.forme.core.settings.AccentColor
import fr.olivier.forme.core.settings.Appearance
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.core.settings.ThemeMode
import fr.olivier.forme.feature.nutrition.domain.DishIngredient
import fr.olivier.forme.feature.nutrition.domain.Per100
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun kotlinx.coroutines.test.TestScope.waterSettings(name: String) =
        HydrationSettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "$name.preferences_pb") })

    @Test
    fun exportThenImportIntoAnotherDatabaseIsLossless() = runTest {
        val clock = TestClock()
        val source = testDatabase(clock)
        val sessions = SessionRepository(source, source.sessionDao(), source.templateDao(), source.exerciseDao(), clock)
        clock.advance(Duration.ofDays(1))
        val id = sessions.start(SeedData.templateC.id)
        sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = 0, loadKg = 17.5, reps = 9, rir = 2, completedAt = Instant.EPOCH))
        sessions.saveCardio(CardioEntryEntity(sessionId = id, exerciseId = SeedData.rower.id, durationSec = 300, distanceM = 1200, pacePer500mSec = 125, caloriesDisplayed = 55))
        clock.advance(Duration.ofMinutes(5))
        sessions.finish(id, 5, "Bonne séance")
        BodyWeightRepository(source.bodyWeightDao()).save(LocalDate.of(2026, 10, 2), 81.7)
        val nutrition = NutritionRepository(
            source, source.profileDao(), source.ciqualDao(), source.foodDao(), source.mealDao(), source.supplementDao(),
            source.sessionDao(), OpenFoodFactsClient(), clock,
        )
        nutrition.saveProfile(ProfileEntity(firstName = "Olivier", proteinTargetGPerKg = 2.0, wheyProteinPer100g = 80.0, birthYear = 1980, sex = Sex.MALE, hrMaxOverride = 181, heightCm = 178, nutritionGoal = NutritionGoal.BULK, kcalTargetOverride = 2700, activityLevel = fr.olivier.forme.feature.nutrition.domain.ActivityLevel.ACTIVE, proteinTargetOverrideG = 165))
        val session = source.sessionDao().getSession(id)!!
        HeartRepository(source.sessionHeartDao(), source.sessionDao(), source.profileDao(), source.bodyWeightDao(), clock)
            .saveSession(id, listOf(session.startedAt.plusSeconds(30) to 120, session.startedAt.plusSeconds(90) to 150), HeartSource.BLE, watchKcal = 42.0)
        val foodId = nutrition.saveFood(FoodEntity(source = FoodSource.OFF, sourceCode = "123", name = "Skyr", proteinPer100g = 10.5, favorite = true))
        nutrition.addFood(nutrition.food(foodId)!!, LocalDate.of(2026, 10, 2), MealType.EXTRA, 150.0)
        nutrition.addWheyDose(LocalDate.of(2026, 10, 2), MealType.SNACK)
        nutrition.setCreatineTaken(LocalDate.of(2026, 10, 2), true)
        // V2.21: a dish with its ingredients.
        nutrition.saveDish(0, "Bol riz poulet", listOf(DishIngredient("Riz", 100.0, Per100(7.0, 350.0, 77.0, 1.0))), cookedWeightG = 250.0, servings = 2)
        // V2.17: the first session earned "Pas mort".
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "badges.preferences_pb") }
        BadgeRepository(source.badgeDao(), store, clock).evaluate()
        // V2.19: the challenge of the month is drawn by the evaluation; a past one, met, is kept too.
        source.badgeDao().insertChallenges(listOf(ChallengeEntity("2026-09", "CURIOUS", LocalDate.of(2026, 9, 20), seen = true)))

        // V2.24 (backup v13): the appearance travels too, into a phone with other settings.
        val sourceSettings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "source.preferences_pb") })
        sourceSettings.setAppearance(Appearance(ThemeMode.LIGHT, AccentColor.ORANGE, dynamicColor = false))
        val targetSettings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "target.preferences_pb") })
        targetSettings.setAccent(AccentColor.LIME)

        // V2.29 (backup v16): hydration, its settings, a drink of one's own, the frozen goal of the day.
        val sourceWater = waterSettings("ws")
        sourceWater.setGoal(GoalSettings(customEnabled = true, customMl = 2800, bonusEnabled = true, bonusMl = 750))
        sourceWater.setReminders(ReminderSettings(intervalMinutes = 120, action1Ml = 300))
        val hydration = HydrationRepository(source, source.hydrationDao(), sourceWater, clock)
        val kombucha = hydration.saveDrink(DrinkEntity(name = "Kombucha", icon = "local_drink", hydrationCoefficient = 0.9, isCaloric = true, sortOrder = 0))
        hydration.add(kombucha, 330, IntakeSource.WATER_SCREEN)
        hydration.addDefault(IntakeSource.HOME_TILE)
        val exported = BackupRepository(source, context(), clock, sourceSettings, sourceWater).snapshot()
        val text = BackupCodec.encode(exported)

        // Target starts with its own (different) seed content, which must be fully replaced.
        val target = testDatabase(TestClock(Instant.parse("2027-01-01T00:00:00Z")))
        val targetWater = waterSettings("wt")
        val targetBackup = BackupRepository(target, context(), clock, targetSettings, targetWater)
        targetBackup.restore(BackupCodec.decode(text))
        val reimported = targetBackup.snapshot()

        assertEquals(2, exported.nutrition.meals.size)
        assertEquals(1, exported.sport.heart.size)
        assertEquals(NutritionGoal.BULK, exported.nutrition.profile!!.nutritionGoal)
        assertEquals(2700, exported.nutrition.profile!!.kcalTargetOverride)
        assertEquals(1980, exported.nutrition.profile!!.birthYear)
        assertEquals("Olivier", exported.nutrition.profile!!.firstName)
        assertEquals(fr.olivier.forme.feature.nutrition.domain.ActivityLevel.ACTIVE, exported.nutrition.profile!!.activityLevel)
        assertEquals(165, exported.nutrition.profile!!.proteinTargetOverrideG)
        assertEquals(listOf("NOT_DEAD" to 0), exported.badges.map { it.id to it.tier })
        assertEquals(listOf("2026-09", "2026-10"), exported.challenges.map { it.month })
        assertEquals(listOf(2), exported.nutrition.dishes.map { it.servings })
        assertEquals(listOf("Riz"), exported.nutrition.dishIngredients.map { it.name })
        assertEquals(Appearance(ThemeMode.LIGHT, AccentColor.ORANGE, false), targetSettings.appearance.first())
        assertEquals(11, exported.hydration!!.drinks.size)
        assertEquals(listOf(330, 750), exported.hydration!!.intakes.map { it.volumeMl })
        assertEquals(listOf(0.9, 1.0), exported.hydration!!.intakes.map { it.coefficientApplied })
        assertEquals(1, exported.hydration!!.days.size)
        assertEquals(GoalSettings(true, 2800, true, 750), targetWater.current().goal)
        assertEquals(120, targetWater.current().reminders.intervalMinutes)
        assertEquals(300, targetWater.current().reminders.action1Ml)
        assertEquals(exported, reimported)
        assertEquals(text, BackupCodec.encode(reimported))
        source.close()
        target.close()
    }

    @Test
    fun aBackupWithoutHydrationRestoresTheDrinksOfferedFromTheStart() = runTest {
        val clock = TestClock()
        val source = testDatabase(clock)
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "old.preferences_pb") })
        val v15 = BackupRepository(source, context(), clock, settings, waterSettings("old1")).snapshot().copy(schemaVersion = 15, hydration = null)
        val target = testDatabase(clock)
        val targetWater = waterSettings("old2")
        HydrationRepository(target, target.hydrationDao(), targetWater, clock).addDefault(IntakeSource.HOME_TILE)
        BackupRepository(target, context(), clock, settings, targetWater).restore(BackupCodec.decode(BackupCodec.encode(v15)))
        assertEquals(HydrationDefaults.drinks, target.hydrationDao().getDrinks())
        assertEquals(HydrationDefaults.containers, target.hydrationDao().getContainers())
        assertEquals(emptyList<Any>(), target.hydrationDao().getAllIntakes())
        source.close()
        target.close()
    }

    @Test
    fun badgesOfAFormat7BackupAreConvertedIntoTiers() = runTest {
        val clock = TestClock()
        val source = testDatabase(clock)
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") })
        val v7 = BackupRepository(source, context(), clock, settings, waterSettings("w1")).snapshot().copy(
            schemaVersion = 7,
            badges = listOf(BadgeDto("PILLAR", "2026-05-01", true), BadgeDto("CREATINE_LOYAL", "2026-06-01", false), BadgeDto("NOT_DEAD", "2026-01-01", true)),
        )
        val target = testDatabase(clock)
        BackupRepository(target, context(), clock, settings, waterSettings("w2")).restore(BackupCodec.decode(BackupCodec.encode(v7)))
        assertEquals(
            listOf(
                BadgeEntity("CREATINE_LOYAL", 2, LocalDate.of(2026, 6, 1), false),
                BadgeEntity("NOT_DEAD", 0, LocalDate.of(2026, 1, 1), true),
                BadgeEntity("REPEAT_OFFENDER", 2, LocalDate.of(2026, 5, 1), true),
            ),
            target.badgeDao().getAll().sortedBy { it.id },
        )
        source.close()
        target.close()
    }
}
