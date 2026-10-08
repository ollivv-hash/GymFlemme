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
import java.time.ZoneId

/** V2.13: after an upper body session this week, the new session sheet suggests the lower body template. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class CatchUpSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

    @Test
    fun lowerBodySuggestedAfterAnUpperBodySession() {
        val zone = ZoneId.systemDefault()
        val weekStart = WeeklyStats.weekStart(LocalDate.now()).atStartOfDay(zone).toInstant()
        // Earlier this week, even right after Monday midnight.
        val clock = TestClock(maxOf(Instant.now().minusSeconds(3_600), weekStart.plusSeconds(1)))
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(clock))
            .build()
        runBlocking {
            val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
            val id = sessions.start(SeedData.templateC.id)
            val upper = listOf(SeedData.chestPress, SeedData.pullDownNeutral, SeedData.shoulderPress, SeedData.rowingMachine, SeedData.crunchMachine)
            var index = 0
            upper.forEach { exercise ->
                repeat(4) {
                    clock.now = clock.now.plusMillis(100)
                    sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = exercise.id, setIndex = index++, loadKg = 40.0, reps = 10, completedAt = clock.now))
                }
            }
            sessions.finish(id, 3, null)
            // The seed session stays before the real one.
            val seedAt = weekStart.minusSeconds(7 * 86_400).toEpochMilli()
            db.openHelper.writableDatabase.execSQL("UPDATE session SET startedAt = $seedAt, endedAt = ${seedAt + 3_600_000} WHERE isSeed = 1")
        }
        db.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Nouvelle séance")
            rule.onNodeWithText("Nouvelle séance").performClick()
            // The rotation goes on (C → A); the lower body template catches up legs that were not trained yet.
            waitFor("suite de la rotation")
            waitFor("Pour rattraper : BAS · Bas du corps")
            waitFor("Muscles en retard : ")
            waitFor("quadriceps")
            shot("k1-new-session-catch-up")
            rule.onNode(hasText("Démarrer BAS · Bas du corps") and hasClickAction()).performClick()
            waitFor("Terminer la séance")
            waitFor("Hack squat")
        }
    }
}
