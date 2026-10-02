package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import fr.olivier.forme.feature.sport.data.TemplateRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.Instant

/** V1.2a features: weekly stats, next-time note, superset, busy-machine replacement. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FeaturesSmokeTest {
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
    fun newFeatures() {
        val clock = TestClock(Instant.now().minus(Duration.ofDays(40)))
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(clock))
            .build()
        runBlocking {
            val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
            val templates = TemplateRepository(db, db.templateDao(), db.sessionDao())
            // ~5 weeks of A / B / C sessions, every 2-3 days.
            val rotation = listOf(SeedData.templateA, SeedData.templateB, SeedData.templateC)
            var i = 0
            while (clock.now.isBefore(Instant.now().minus(Duration.ofDays(1)))) {
                val id = sessions.start(rotation[i % 3].id)
                db.sessionDao().getSessionLines(id).forEach { (line, ex) ->
                    repeat(line.targetSets) { s ->
                        sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = ex.id, setIndex = s, loadKg = 15.0, reps = line.targetRepsMax ?: 10, durationSec = line.targetDurationSec, completedAt = Instant.EPOCH))
                    }
                }
                clock.advance(Duration.ofMinutes(55))
                sessions.finish(id, 4, null)
                clock.advance(Duration.ofDays(if (i % 3 == 2) 3 else 2))
                i++
            }
            // Note left last time on Chest press; superset Abdominal crunch + Chaise romaine in template C.
            db.exerciseDao().setNextNote(SeedData.chestPress.id, "Monter la charge")
            val lines = templates.getLines(SeedData.templateC.id).map { it.line }
            templates.save(SeedData.templateC, lines.mapIndexed { idx, l -> if (idx == 3 || idx == 4) l.copy(supersetGroup = 1) else l })
            // Make C the proposed session.
            templates.saveRotation(listOf(SeedData.templateC.id, SeedData.templateA.id, SeedData.templateB.id))
        }
        db.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Cette semaine :")
            shot("f1-home")

            rule.onNode(hasText("Historique") and hasClickAction()).performClick()
            rule.onNode(hasText("Semaine") and hasClickAction()).performClick()
            waitFor("Séries par groupe musculaire")
            shot("f2-week")

            rule.onNode(hasText("Accueil") and hasClickAction()).performClick()
            waitFor("Séance proposée")
            rule.onNodeWithText("Démarrer").performClick()
            waitFor("Allure")
            rule.onNodeWithText("Enregistrer").performClick() // rower
            waitFor("Monter la charge")
            shot("f3-note-banner")

            // Replace "Pull down prise neutre" (3rd card) by "Pull down".
            rule.onAllNodesWithContentDescription("Actions")[2].performClick()
            rule.onNodeWithText("Remplacer… (machine occupée)").performClick()
            waitFor("Même groupe")
            shot("f4-replace-dialog")
            rule.onNodeWithText("Pull down").performClick()
            waitFor("remplace Pull down prise neutre")

            // Expand the superset.
            rule.onNodeWithText("Abdominal crunch machine").performClick()
            waitFor("Superset avec Chaise romaine")
            shot("f5-superset")
        }
    }
}
