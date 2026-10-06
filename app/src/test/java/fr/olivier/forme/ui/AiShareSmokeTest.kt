package fr.olivier.forme.ui

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.core.db.FormeDatabase
import fr.olivier.forme.core.db.Migrations
import fr.olivier.forme.core.db.SeedCallback
import fr.olivier.forme.core.db.SeedData
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.context
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionGoal
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** V2.4: "Analyse IA" builds the day's Markdown file and shares it to the chosen AI app. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AiShareSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)

    private fun installFakeClaude() {
        val component = ComponentName("com.anthropic.claude", "com.anthropic.claude.ShareActivity")
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = component.packageName
                name = component.className
                applicationInfo = ApplicationInfo().apply { packageName = component.packageName }
            }
        }
        val pm = shadowOf(app.packageManager)
        pm.addResolveInfoForIntent(Intent(Intent.ACTION_SEND).setType("text/plain"), info)
        pm.addActivityIfNotPresent(component)
        pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_SEND).apply { addDataType("text/plain") })
    }

    /** A real-looking day: profile, weigh-in, a finished session with heart rate, two meals. */
    private fun seedDay() {
        val today = LocalDate.now()
        val start = Instant.now().minus(Duration.ofMinutes(70))
        val clock = TestClock(start)
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(clock))
            .build()
        runBlocking {
            db.profileDao().upsert(
                ProfileEntity(
                    birthYear = today.year - 46, sex = Sex.MALE, heightCm = 178, nutritionGoal = NutritionGoal.BULK,
                    wheyProteinPer100g = 80.0, wheyKcalPer100g = 380.0,
                ),
            )
            BodyWeightRepository(db.bodyWeightDao()).save(today, 80.4)
            val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
            val id = sessions.start(SeedData.templateC.id)
            listOf(45.0 to 10, 45.0 to 9, 45.0 to 8).forEachIndexed { i, (load, reps) ->
                clock.now = clock.now.plusSeconds(150)
                sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = i, loadKg = load, reps = reps, rir = 2 - i, completedAt = clock.now))
            }
            clock.now = start.plus(Duration.ofMinutes(45))
            sessions.finish(id, 4, "Épaule droite un peu raide")
            val samples = (0 until 45 * 60 step 5).map { sec -> start.plusSeconds(sec.toLong()) to 110 + (sec / 60) % 25 }
            HeartRepository(db.sessionHeartDao(), db.sessionDao(), db.profileDao(), db.bodyWeightDao(), clock)
                .saveSession(id, samples, HeartSource.BLE)
            fun meal(type: MealType, name: String, g: Double, protein: Double, kcal: Double) = MealEntryEntity(
                date = today, mealType = type, foodId = null, name = name, quantityG = g, proteinG = protein,
                kcal = kcal, carbsG = null, fatG = null, eatenAt = Instant.now(),
            )
            db.mealDao().insert(meal(MealType.BREAKFAST, "Flocons d'avoine", 80.0, 10.0, 300.0))
            db.mealDao().insert(meal(MealType.LUNCH, "Blanc de poulet", 150.0, 45.0, 180.0))
        }
        db.close()
    }

    @Test
    fun homeButtonSharesTheDayFileToTheDefaultApp() {
        installFakeClaude()
        seedDay()
        ActivityScenario.launch(MainActivity::class.java).use {
            // Choose Claude, sending the file (default method).
            waitFor("Nouvelle séance")
            rule.onNode(hasText("Config") and hasClickAction()).performClick()
            rule.onNodeWithText("Analyse IA").performClick()
            waitFor("App IA par défaut")
            rule.onNodeWithText("Claude").performClick()
            rule.waitUntilAtLeastOneExists(hasText("Claude") and isSelected(), 10_000)
            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }

            rule.onNode(hasText("Accueil") and hasClickAction()).performClick()
            rule.onNodeWithText("Analyse IA du jour").performScrollTo().performClick()
            rule.waitUntil(20_000) { shadowOf(app).peekNextStartedActivity() != null }

            val started = shadowOf(app).nextStartedActivity
            assertEquals("com.anthropic.claude", started.`package`)
            assertEquals(Intent.ACTION_SEND, started.action)
            assertNull(started.getStringExtra(Intent.EXTRA_TEXT)) // Claude drops it when a file is attached
            @Suppress("DEPRECATION")
            val uri = started.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertTrue(uri.toString().endsWith("forme-analyse-${LocalDate.now()}.md"))
            assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

            val markdown = File(File(app.cacheDir, "share"), "forme-analyse-${LocalDate.now()}.md").readText()
            File("build/smoke-screens").apply { mkdirs() }.resolve("ai-analyse.md").writeText(markdown)
            listOf(
                "## Consignes", "## Profil", "46 ans · homme · 178 cm", "Objectif : prise de masse",
                "## Séance du jour : C · Mixte + tronc dominant", "Chest press : 45 kg × 10 (RIR 2)", "ressenti 4/5",
                "Fréquence cardiaque : moyenne", "Notes : Épaule droite un peu raide",
                "## Nutrition du jour", "Protéines : 55 g / 145 g", "Petit-déjeuner (10 g prot., 300 kcal) : Flocons d'avoine 80 g",
                "## Semaine", "Séances : 1 / 3",
            ).forEach { expected -> assertTrue("missing: $expected", markdown.contains(expected)) }
        }
    }
}
