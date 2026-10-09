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
import fr.olivier.forme.R
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** V2.16: welcome back and lighter loads after a long break; a word when last week's goal was missed. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ComebackSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private val resources = context().resources
    private val zone = ZoneId.systemDefault()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

    private fun waitForAny(texts: List<String>) = rule.waitUntil(15_000) {
        texts.any { rule.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty() }
    }

    /** Finished sessions of template C on [days], chest press 40 kg × 10; the seed session is moved before them. */
    private fun sessionsOn(vararg days: LocalDate) {
        val clock = TestClock(days.min().minusDays(1).atStartOfDay(zone).toInstant())
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(clock))
            .build()
        runBlocking {
            val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
            days.sorted().forEach { day ->
                clock.now = day.atTime(LocalTime.NOON).atZone(zone).toInstant()
                val id = sessions.start(SeedData.templateC.id)
                clock.now = clock.now.plusSeconds(60)
                sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = 0, loadKg = 40.0, reps = 10, completedAt = clock.now))
                clock.now = clock.now.plusSeconds(1800)
                sessions.finish(id, 3, null)
            }
            val seedAt = days.min().minusDays(30).atStartOfDay(zone).toInstant().toEpochMilli()
            db.openHelper.writableDatabase.execSQL("UPDATE session SET startedAt = $seedAt, endedAt = ${seedAt + 3_600_000} WHERE isSeed = 1")
        }
        db.close()
    }

    @Test
    fun welcomeBackThenLighterLoadsOnRequest() {
        sessionsOn(LocalDate.now().minusDays(20))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Nutrition du jour")
            waitForAny(resources.getStringArray(R.array.humor_welcome_back).toList())
            rule.acknowledgeBadges()
            shot("cb1-welcome-back")

            rule.onNode(hasText("Nouvelle séance") and hasClickAction()).performClick()
            rule.onNode(hasText("Démarrer") and hasClickAction()).performClick()
            waitFor(resources.getStringArray(R.array.humor_lighter_loads).first())
            shot("cb2-lighter-loads-offer")
            rule.onNode(hasText("Appliquer") and hasClickAction()).performClick()
            rule.waitUntil(15_000) { rule.onAllNodes(hasText("Appliquer")).fetchSemanticsNodes().isEmpty() }
            // Chest press: 40 kg last time → 32 kg, rounded to its 2.5 kg step.
            rule.onNodeWithText("Chest press").performScrollTo().performClick()
            waitFor("32,5")
            shot("cb3-lighter-loads-applied")
        }
    }

    @Test
    fun missedGoalLastWeekUntilTheFirstSession() {
        val thisWeek = WeeklyStats.weekStart(LocalDate.now())
        // One session last Sunday (goal: 3), one three weeks ago: a regular who missed last week's goal.
        sessionsOn(thisWeek.minusDays(1), thisWeek.minusWeeks(3))
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Nutrition du jour")
            val variants = resources.getStringArray(R.array.humor_missed_goal).map { it.replace("%1\$s", "A · Poussée + tronc") }
            waitForAny(variants)
            shot("cb4-missed-goal")
        }
    }
}
