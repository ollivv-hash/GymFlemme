package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
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
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.max

/** V2.1 lot 4: heart rate in the session summary, the week tab and the nutrition day. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HeartSummarySmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)

    @Test
    fun heartSummaryWeekAndNutrition() {
        val start = Instant.now().minus(Duration.ofMinutes(55))
        val clock = TestClock(start)
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(clock))
            .build()
        runBlocking {
            db.profileDao().upsert(ProfileEntity(birthYear = LocalDate.now().year - 46, sex = Sex.MALE))
            BodyWeightRepository(db.bodyWeightDao()).save(LocalDate.now(), 80.0)
            val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
            val id = sessions.start(SeedData.templateC.id)
            // 3 exercises × 3 sets, one set every 2 min 30 from minute 6.
            val exercises = listOf(SeedData.chestPress.id to 15.0, SeedData.pullDownNeutral.id to 25.0, SeedData.crunchMachine.id to 20.0)
            val setTimes = mutableListOf<Instant>()
            exercises.forEachIndexed { e, (exerciseId, load) ->
                repeat(3) { s ->
                    val at = start.plusSeconds(360L + (e * 3 + s) * 150L)
                    setTimes += at
                    clock.now = at // logSet stamps the set with the repository clock, like a real validation
                    sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = exerciseId, setIndex = s, loadKg = load, reps = 10, completedAt = at))
                }
            }
            clock.now = start.plus(Duration.ofMinutes(35))
            sessions.finish(id, 4, null)
            // Warm-up on the rower (~120), then effort peaks before each set and recovery in between; one peak at 182.
            val samples = (0 until 35 * 60).map { sec ->
                val t = start.plusSeconds(sec.toLong())
                val rower = if (sec < 300) 95 + sec / 10 else 0
                val effort = setTimes.maxOf { set ->
                    val d = Duration.between(t, set).seconds
                    if (d in 0..45) 150 - d.toInt() / 2 + if (set == setTimes[7]) 35 else 0 else 0
                }
                val recovery = setTimes.filter { it.isBefore(t) }.maxOfOrNull { set ->
                    val after = Duration.between(set, t).seconds.toInt()
                    max(0, 140 - after / 3)
                } ?: 0
                t to maxOf(rower, effort, recovery, 92)
            }
            HeartRepository(db.sessionHeartDao(), db.sessionDao(), db.profileDao(), db.bodyWeightDao(), clock)
                .saveSession(id, samples, HeartSource.BLE)
        }
        db.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Dernières séances")
            rule.acknowledgeBadges()
            waitFor(" min")
            rule.onNode(hasText(" min", substring = true)).performScrollTo().performClick()
            waitFor("Fréquence cardiaque")
            rule.onNodeWithText("Fréquence cardiaque").performScrollTo()
            shot("h3-summary-heart")
            rule.onNodeWithText("Pic de FC par série").performScrollTo()
            shot("h4-summary-zones")
            rule.onNodeWithText("Passer à", substring = true).performScrollTo().performClick()
            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }

            rule.onNode(hasText("Historique") and hasClickAction()).performClick()
            rule.onNode(hasText("Semaine") and hasClickAction()).performClick()
            waitFor("séance mesurée")
            shot("h5-week-heart")

            rule.onNode(hasText("Nutrition") and hasClickAction()).performClick()
            waitFor("Dépense des séances")
            shot("h6-nutrition-spend")
        }
    }
}
