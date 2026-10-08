package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** V2.17: a badge earned yesterday is announced on the home screen (feminine agreement), then listed in Historique. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BadgesSmokeTest {
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
    fun firstBadgeAnnouncedThenListed() {
        val zone = ZoneId.systemDefault()
        val yesterdayNoon = LocalDate.now().minusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant()
        val clock = TestClock(yesterdayNoon.minusSeconds(3_600))
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(clock))
            .build()
        runBlocking {
            db.profileDao().upsert(ProfileEntity(sex = Sex.FEMALE))
            val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
            clock.now = yesterdayNoon
            val id = sessions.start(SeedData.templateC.id)
            clock.now = clock.now.plusSeconds(60)
            // The seed's load: no record, only "Pas mort(e)".
            sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = 0, loadKg = 15.0, reps = 10, completedAt = clock.now))
            clock.now = clock.now.plusSeconds(1800)
            sessions.finish(id, 3, null)
        }
        db.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Nutrition du jour")
            waitFor("🏅 Pas morte")
            waitFor("Tu es vivante")
            shot("bg1-home-new-badge")
            rule.onNode(hasText("Compris") and hasClickAction()).performClick()
            rule.waitUntil(15_000) { rule.onAllNodes(hasText("🏅 Pas morte")).fetchSemanticsNodes().isEmpty() }

            rule.onNode(hasText("Historique") and hasClickAction()).performClick()
            rule.onNode(hasText("Badges") and hasClickAction()).performClick()
            waitFor("1 / 10 badges")
            waitFor("Obtenu le")
            waitFor("Lève-tôt malgré elle")
            shot("bg2-history-badges")
        }
    }
}
