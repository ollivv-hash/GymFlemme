package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
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
import fr.olivier.forme.core.db.SeedCallback
import fr.olivier.forme.core.db.SeedData
import fr.olivier.forme.core.db.context
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.sport.data.CardioEntryEntity
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/** Prepares a finished session in the real database file, then checks home, recap, history and weight screens. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class RecapSmokeTest {
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
    fun recapHistoryAndWeight() {
        val clock = Clock.systemDefaultZone()
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addCallback(SeedCallback(clock))
            .build()
        runBlocking {
            val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
            val id = sessions.start(SeedData.templateC.id)
            sessions.saveCardio(CardioEntryEntity(sessionId = id, exerciseId = SeedData.rower.id, durationSec = 300, distanceM = 1250, pacePer500mSec = 120, caloriesDisplayed = 60))
            listOf(17.5, 17.5, 15.0).forEachIndexed { i, load ->
                sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = i, loadKg = load, reps = 10, rir = if (i == 2) 3 else null, completedAt = Instant.EPOCH))
            }
            repeat(3) { i ->
                sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.romanChair.id, setIndex = i, reps = 12, completedAt = Instant.EPOCH))
            }
            sessions.finish(id, 4, "Bonne énergie")
            val weights = BodyWeightRepository(db.bodyWeightDao())
            (0L..9L).forEach { weights.save(LocalDate.now().minusDays(9 - it), 82.0 - it * 0.15 + (it % 3) * 0.2) }
        }
        db.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Dernières séances")
            rule.acknowledgeBadges()
            // The session row can arrive a frame after the section title: wait for the row itself.
            rule.waitUntilAtLeastOneExists(hasTextEndingWith(" min"), 10_000)
            shot("6-home-after")
            rule.onNode(hasTextEndingWith(" min")).performScrollTo().performClick()
            waitFor("Copier le bilan texte")
            shot("7-recap")
            rule.onNodeWithText("Chest press").performClick()
            waitFor("Meilleure charge")
            shot("8-exercise-history")
            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }
            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }
            waitFor("Nutrition")
            rule.onNode(hasText("Nutrition") and androidx.compose.ui.test.hasClickAction()).performClick()
            waitFor("Journal")
            rule.onNode(hasText("Poids") and androidx.compose.ui.test.hasClickAction()).performClick()
            waitFor("Pesées")
            shot("9-weight")
        }
    }
}
