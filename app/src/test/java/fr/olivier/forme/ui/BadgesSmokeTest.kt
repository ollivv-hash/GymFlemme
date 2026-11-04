package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.feature.badges.ui.BADGES_ACK_TAG
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * V2.18: the badges of the history are shown once in "Tes badges déjà gagnés" (feminine agreement); the level card of
 * the home screen opens the Badges screen, with its tiers, one-off and secret badges.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BadgesSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        // The screen's window (a sheet opens in its own window, not rendered by Robolectric: its texts are checked).
        File(dir, "$name.png").outputStream().use {
            rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

    @Test
    fun historyRecappedOnceThenBadgesScreen() {
        val zone = ZoneId.systemDefault()
        // Three days ago or earlier, never a Sunday or a 1 January: no secret badge, only "Pas morte".
        val day = generateSequence(LocalDate.now().minusDays(3)) { it.minusDays(1) }
            .first { it.dayOfWeek != DayOfWeek.SUNDAY && !(it.monthValue == 1 && it.dayOfMonth == 1) }
        val noon = day.atTime(LocalTime.NOON).atZone(zone).toInstant()
        val clock = TestClock(noon.minusSeconds(3_600))
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(clock))
            .build()
        runBlocking {
            db.profileDao().upsert(ProfileEntity(sex = Sex.FEMALE))
            val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
            clock.now = noon
            val id = sessions.start(SeedData.templateC.id)
            clock.now = clock.now.plusSeconds(60)
            // The seed's load: no record, only "Pas morte".
            sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = 0, loadKg = 15.0, reps = 10, completedAt = clock.now))
            clock.now = clock.now.plusSeconds(1800)
            sessions.finish(id, 3, null)
        }
        db.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Tes badges déjà gagnés")
            waitFor("Pas morte")
            waitFor("Tu es vivante")
            rule.onNodeWithTag(BADGES_ACK_TAG).performClick()
            rule.waitUntil(15_000) { rule.onAllNodes(hasText("Tes badges déjà gagnés")).fetchSemanticsNodes().isEmpty() }

            waitFor("Niveau 1 · Flemmarde débutante")
            waitFor("10 / 30 points")
            waitFor("🎯 Défi d") // V2.19: the challenge of the month under the level
            shot("bg2-home-level")
            rule.onNode(hasText("Niveau 1", substring = true) and hasClickAction()).performClick()

            waitFor("Prochain palier : Bronze")
            waitFor("1 / 10 séances")
            waitFor("Plus forte que le canapé")
            shot("bg3-badges-tiers")

            rule.onNode(hasText("Ponctuels") and hasClickAction()).performClick()
            waitFor("Obtenu le")
            waitFor("Lève-tôt malgré elle")
            waitFor("Séance commencée avant 8 h")
            shot("bg4-badges-one-off")

            // V2.19: the challenge of the month, with its progress, in the Défis tab (drawn from the real date).
            rule.onNode(hasText("Défis") and hasClickAction()).performClick()
            waitFor("🎯 Défi d")
            waitFor("Ce mois-ci")
            shot("bg5-badges-challenges")
        }
    }
}
