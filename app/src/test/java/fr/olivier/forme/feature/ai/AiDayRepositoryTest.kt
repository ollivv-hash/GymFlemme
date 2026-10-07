package fr.olivier.forme.feature.ai

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.db.SeedData
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.testDatabase
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.ai.data.AiDayRepository
import fr.olivier.forme.feature.library.data.LibraryRepository
import fr.olivier.forme.feature.ai.domain.AnalysisMarkdown
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.NutritionWeekSource
import fr.olivier.forme.feature.nutrition.data.OpenFoodFactsClient
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionGoal
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AiDayRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = TestClock()
    private val db = testDatabase(clock)

    @After
    fun close() = db.close()

    @Test
    fun dayWithTwoSessionsOfTheSameExercise() = runTest {
        val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
        val heart = HeartRepository(db.sessionHeartDao(), db.sessionDao(), db.profileDao(), db.bodyWeightDao(), clock)
        val nutrition = NutritionRepository(
            db, db.profileDao(), db.ciqualDao(), db.foodDao(), db.mealDao(), db.creatineDao(), db.sessionDao(), OpenFoodFactsClient(), clock,
        )
        val weights = BodyWeightRepository(db.bodyWeightDao())
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") })
        val repository = AiDayRepository(sessions, heart, nutrition, NutritionWeekSource(nutrition, heart, weights, clock), weights, settings, LibraryRepository(db.libraryDao()), clock)

        // The library, for the volume per muscle (V2.12): the seeded chest press is linked to it.
        db.libraryDao().insertAll(
            fr.olivier.forme.core.db.context().assets.open("exercise_library/exercises.json")
                .use(fr.olivier.forme.feature.library.data.ExerciseLibraryLoader::parse),
        )
        nutrition.saveProfile(ProfileEntity(birthYear = 1980, sex = Sex.MALE, heightCm = 178, nutritionGoal = NutritionGoal.CUT))
        weights.save(LocalDate.now(clock).minusWeeks(4), 82.0)
        // Last week, then today: chest press 42.5 → 45 kg.
        listOf(42.5, 45.0).forEach { load ->
            clock.advance(Duration.ofDays(7))
            val id = sessions.start(SeedData.templateC.id)
            sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = 0, loadKg = load, reps = 10, completedAt = clock.instant()))
            clock.advance(Duration.ofMinutes(40))
            sessions.finish(id, 3, null)
            heart.saveSession(id, listOf(clock.instant().minusSeconds(600) to 120, clock.instant().minusSeconds(60) to 140), HeartSource.BLE)
        }
        val today = LocalDate.now(clock)
        weights.save(today, 80.5)
        db.mealDao().insert(
            MealEntryEntity(
                date = today, mealType = MealType.DINNER, foodId = null, name = "Saumon", quantityG = 150.0, proteinG = 30.0,
                kcal = 300.0, carbsG = 0.0, fatG = 20.0, eatenAt = clock.instant(),
            ),
        )

        val md = AnalysisMarkdown.build(repository.day(today))
        listOf(
            "objectif : sèche".replaceFirstChar { it.uppercase() }.let { "Objectif : sèche" },
            "-1,5 kg en 4 semaines",
            "Chest press : 45 kg × 10 — dernière fois : 42,5 kg × 10",
            "Protéines : 30 g",
            "Dîner (30 g prot., 300 kcal) : Saumon 150 g",
            "Séances : 1 / 3",
            "Séries par muscle (muscle principal 1 par série, en appui ½ ; repère des grands muscles 10 à 20) : Pectoraux 1 ·",
            // History for charts.
            "## Historique pour graphiques",
            "### Chest press (dernières séances)",
            "| 42,5 kg × 10 | 56,7 |",
            "| 45 kg × 10 | 60 |",
            "### Poids (30 jours)",
            "| 80,5 | 80,5 |",
            "### Nutrition (7 jours)",
            "| 30 | 300 |",
            "### Séries par groupe musculaire (semaines)",
        ).forEach { assertTrue("missing: $it\n$md", md.contains(it)) }
    }
}
