package fr.olivier.forme.feature.widget

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.db.SeedData
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.context
import fr.olivier.forme.core.db.testDatabase
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.OpenFoodFactsClient
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionGoal
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.TemplateRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** V2.6 home-screen widget: its data (same rules as the app) and what it displays. */
@RunWith(AndroidJUnit4::class)
class WidgetTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = TestClock()
    private val db = testDatabase(clock)

    @After
    fun close() = db.close()

    @Test
    fun dataFollowsSessionsAndMeals() = runTest {
        val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
        val nutrition = NutritionRepository(
            db, db.profileDao(), db.ciqualDao(), db.foodDao(), db.mealDao(), db.supplementDao(), db.sessionDao(), OpenFoodFactsClient(), clock,
        )
        val weights = BodyWeightRepository(db.bodyWeightDao())
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") })
        val source = WidgetDataSource(
            sessions,
            TemplateRepository(db, db.templateDao(), db.sessionDao()),
            settings,
            nutrition,
            HeartRepository(db.sessionHeartDao(), db.sessionDao(), db.profileDao(), db.bodyWeightDao(), clock),
            weights,
            clock,
        )
        val today = LocalDate.now(clock)

        val empty = source.observe(today).first()
        assertNotNull(empty.sessionTitle) // the rotation proposes the seeded templates
        assertFalse(empty.sessionRunning)
        assertEquals(null, empty.kcalTarget) // no nutrition goal yet

        nutrition.saveProfile(ProfileEntity(birthYear = 1980, sex = Sex.MALE, heightCm = 178, nutritionGoal = NutritionGoal.CUT))
        weights.save(today, 80.0)
        db.mealDao().insert(
            MealEntryEntity(
                date = today, mealType = MealType.LUNCH, foodId = null, name = "Poulet", quantityG = 200.0, proteinG = 50.0,
                kcal = 400.0, carbsG = 0.0, fatG = 8.0, eatenAt = clock.instant(),
            ),
        )
        val id = sessions.start(SeedData.templateC.id)
        val running = source.observe(today).first()
        assertTrue(running.sessionRunning)
        assertEquals(SeedData.templateC.name, running.sessionTitle) // name only, without the code
        assertEquals(400.0, running.kcal, 0.0)
        assertEquals(50.0, running.proteinG, 0.0)
        assertEquals(144.0, running.proteinTargetG!!, 0.01) // 1.8 g/kg × 80 kg
        assertNotNull(running.kcalTarget)

        clock.advance(Duration.ofMinutes(45))
        sessions.finish(id, 3, null)
        val done = source.observe(today).first()
        assertFalse(done.sessionRunning)
        assertEquals(empty.weekDone + 1, done.weekDone)
    }

    @Test
    fun updateLabelShowsTheDayOnlyWhenNotToday() {
        val today = LocalDate.of(2026, 10, 7)
        assertEquals("Màj 10:35", updatedLabel(LocalDateTime.of(2026, 10, 7, 10, 35), today))
        assertEquals("Màj 6 oct. 22:06", updatedLabel(LocalDateTime.of(2026, 10, 6, 22, 6), today))
    }

    @Test
    fun contentShowsSessionWeekAndRings() = runGlanceAppWidgetUnitTest {
        setContext(context())
        provideComposable {
            WidgetContent(
                context(),
                WidgetData(
                    sessionTitle = "B · Dos",
                    sessionRunning = false,
                    weekDone = 2,
                    weekGoal = 3,
                    kcal = 1240.0,
                    kcalTarget = 2300,
                    proteinG = 95.0,
                    proteinTargetG = 160.0,
                ),
                LocalDateTime.of(LocalDate.now(), LocalTime.of(10, 35)),
            )
        }
        listOf("Màj 10:35", "Prochaine séance", "B · Dos", "Semaine : 2 / 3", "Démarrer", "1 240", "/ 2 300", "95", "/ 160")
            .forEach { onNode(hasText(it)).assertExists() }
    }

    @Test
    fun liveContentFollowsTheDataWhileTheWidgetSessionRuns() = runGlanceAppWidgetUnitTest {
        setContext(context())
        setState(androidx.datastore.preferences.core.emptyPreferences())
        val running = WidgetData("Jambes – 30 min", true, 2, 3, 1004.0, 1860, 33.0, 148.0)
        // Drawn while the session ran, then abandoned in the app: the widget, still composed, must follow the
        // database without a new provideGlance().
        provideComposable { LiveWidgetContent(context(), running) { flowOf(running.copy(sessionRunning = false)) } }
        awaitIdle()
        onNode(hasText("Démarrer")).assertExists()
        onNode(hasText("Reprendre")).assertDoesNotExist()
    }

    @Test
    fun onlyTheButtonStartsASessionTheRestOpensTheApp() = runGlanceAppWidgetUnitTest {
        setContext(context())
        provideComposable {
            WidgetContent(context(), WidgetData("Jambes – 30 min", false, 2, 3, 1004.0, 1860, 33.0, 148.0), LocalDateTime.now())
        }
        // Clickable: the card (home), the logo (home), the Démarrer button, the rings (nutrition) and ↻.
        // The session text column has no action of its own since 2.12.1: it opens the home screen like the card.
        onAllNodes(androidx.glance.testing.unit.hasClickAction()).assertCountEquals(5)
        onNode(hasText("Démarrer")).assert(androidx.glance.testing.unit.hasClickAction())
        onNode(hasText("Jambes – 30 min")).assert(androidx.glance.testing.unit.hasNoClickAction())
    }
}
