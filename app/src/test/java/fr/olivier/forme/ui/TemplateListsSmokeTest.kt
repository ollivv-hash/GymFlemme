package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.core.db.FormeDatabase
import fr.olivier.forme.core.db.Migrations
import fr.olivier.forme.core.db.SeedCallback
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.context
import fr.olivier.forme.feature.sport.data.TemplateRepository
import fr.olivier.forme.feature.sport.ui.home.NEW_SESSION_SHEET_TAG
import fr.olivier.forme.feature.sport.data.WorkoutTemplateEntity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Instant

/** Long template lists: capped scrolling list on home, whole-screen scrolling on the rotation screen. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h640dp-xxhdpi")
class TemplateListsSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun seedExtraTemplates() {
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(TestClock(Instant.now())))
            .build()
        runBlocking {
            val templates = TemplateRepository(db, db.templateDao(), db.sessionDao())
            (1..8).forEach { templates.save(WorkoutTemplateEntity(name = "Extra $it", code = "X$it"), emptyList()) }
        }
        db.close()
    }

    @Test
    fun newSessionSheetListsProposedThenAllTemplates() {
        seedExtraTemplates()
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForHome()
            rule.openSessionChoice()
            rule.waitUntilAtLeastOneExists(hasText("Séance proposée", substring = true), 10_000)
            rule.onNodeWithText("Démarrer").assertExists()
            shot("r0-new-session-sheet")
            // Every other template is reachable by scrolling the sheet, then the free session.
            rule.onNodeWithTag(NEW_SESSION_SHEET_TAG).performScrollToNode(hasText("Extra 8", substring = true))
            rule.onNodeWithText("Extra 8", substring = true).assertIsDisplayed()
            rule.onNodeWithTag(NEW_SESSION_SHEET_TAG).performScrollToNode(hasText("Séance libre"))
            rule.onNodeWithText("Séance libre").assertIsDisplayed()
            shot("r0b-new-session-sheet-scrolled")
            rule.onNodeWithText("Extra 8", substring = true).performClick()
            rule.waitUntilAtLeastOneExists(hasText("X8 · Extra 8"), 10_000)
        }
    }

    @Test
    fun longRotationScrolls() {
        seedExtraTemplates()
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForHome()
            rule.openSettings()
            rule.onNode(hasText("Rotation") and hasClickAction()).performClick()
            rule.waitUntilAtLeastOneExists(hasText("Hors rotation"), 10_000)
            // Wait for the end of the screen transition (the Config list is scrollable too) and for the templates.
            rule.waitUntilExactlyOneExists(hasScrollAction(), 10_000)
            rule.waitUntilAtLeastOneExists(hasText("Extra 1", substring = true), 10_000)
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("Extra 8", substring = true))
            rule.onNodeWithText("Extra 8", substring = true).assertExists()
            shot("r1-rotation-scrolled")
        }
    }
}
