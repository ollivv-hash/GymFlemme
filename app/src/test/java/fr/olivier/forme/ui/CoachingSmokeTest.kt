package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import fr.olivier.forme.feature.sport.domain.WeeklyStats
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** V2.3: deload advice on the home screen, plateau in the running session and in the exercise history. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CoachingSmokeTest {
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
    fun deloadPlateauAndOneRepMax() {
        val zone = ZoneId.systemDefault()
        val thisWeek = WeeklyStats.weekStart(LocalDate.now())
        val clock = TestClock(thisWeek.minusWeeks(8).atStartOfDay(zone).toInstant())
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(clock))
            .build()
        runBlocking {
            val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
            // 7 weeks in a row, one session a week: chest press progresses for 3 weeks, then stalls at 45 kg × 10.
            val chest = listOf(40.0 to 10, 42.5 to 10, 45.0 to 10, 45.0 to 10, 45.0 to 9, 45.0 to 10, 45.0 to 10)
            chest.forEachIndexed { w, (load, reps) ->
                val day = thisWeek.minusWeeks(7L - w).plusDays(1)
                clock.now = day.atTime(LocalTime.NOON).atZone(zone).toInstant()
                val id = sessions.start(SeedData.templateC.id)
                repeat(3) { s ->
                    clock.now = clock.now.plusSeconds(120)
                    sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = s, loadKg = load, reps = reps, completedAt = clock.now))
                    sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.pullDownNeutral.id, setIndex = s, loadKg = 50.0, reps = 12, completedAt = clock.now))
                }
                clock.now = clock.now.plusSeconds(1800)
                sessions.finish(id, 4, null)
            }
            // The seed session is the starting reference: keep it before the real ones, whenever the database was created.
            val seedAt = thisWeek.minusWeeks(9).atStartOfDay(zone).toInstant().toEpochMilli()
            db.openHelper.writableDatabase.execSQL("UPDATE session SET startedAt = $seedAt, endedAt = ${seedAt + 3_600_000} WHERE isSeed = 1")
            clock.now = Instant.now()
            sessions.start(SeedData.templateC.id)
        }
        db.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            // A running session is resumed at launch.
            waitFor("Chest press")
            rule.onNodeWithText("Chest press").performScrollTo().performClick()
            waitFor("Stagnation depuis")
            shot("c2-session-plateau")
            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }
            waitFor("Semaine de décharge conseillée")
            shot("c1-home-deload")

            rule.onNode(hasText("Historique") and hasClickAction()).performClick()
            rule.onNode(hasText("Exercices") and hasClickAction()).performClick()
            rule.waitUntilExactlyOneExists(hasScrollAction(), 10_000)
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("Chest press"))
            rule.onNode(hasText("Chest press") and hasClickAction()).performClick()
            waitFor("1RM estimé (Epley")
            shot("c3-exercise-history")

            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }
            rule.onNode(hasText("Accueil") and hasClickAction()).performClick()
            rule.onNodeWithText("Compris").performScrollTo().performClick()
            rule.waitUntilDoesNotExist(hasText("Semaine de décharge conseillée"), 10_000)
        }
    }
}
