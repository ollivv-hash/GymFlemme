package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.core.db.FormeDatabase
import fr.olivier.forme.core.db.Migrations
import fr.olivier.forme.core.db.SeedCallback
import fr.olivier.forme.core.db.context
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Clock
import java.time.LocalDate

/** Nutrition journal: gauge from the last weight, Ciqual search, add a food, whey card. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class NutritionSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun exists(text: String) =
        rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** Text fields keep a blinking cursor: drive the clock by hand while one is focused. */
    private fun pump(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met" }
            rule.mainClock.advanceTimeBy(100)
            Thread.sleep(50)
        }
    }

    @Test
    fun searchAndAddFood() {
        val clock = Clock.systemDefaultZone()
        val db = Room.databaseBuilder(context(), FormeDatabase::class.java, FormeDatabase.NAME)
            .addMigrations(*Migrations.ALL)
            .addCallback(SeedCallback(clock))
            .build()
        runBlocking {
            BodyWeightRepository(db.bodyWeightDao()).save(LocalDate.now(), 82.0)
            db.profileDao().upsert(ProfileEntity(wheyProteinPer100g = 80.0, wheyKcalPer100g = 380.0))
        }
        db.close()

        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntilAtLeastOneExists(hasText("Nutrition du jour"), 10_000)
            rule.onNode(hasText("Nutrition") and hasClickAction()).performClick()
            rule.waitUntilAtLeastOneExists(hasText("/ 148 g", substring = true), 10_000)
            shot("n1-journal-empty")

            rule.onNode(hasContentDescription("Ajouter à Déjeuner")).performScrollTo().performClick()
            rule.waitUntilAtLeastOneExists(hasText("Rechercher un aliment", substring = true), 10_000)
            rule.mainClock.autoAdvance = false
            rule.onNode(hasSetTextAction()).performTextInput("poulet filet grill")
            pump { exists("Poulet, filet sans peau grillé/poêlé") }
            shot("n2-search")
            rule.onNodeWithText("Poulet, filet sans peau grillé/poêlé").performClick()
            pump { exists("Pour 100 g") }
            shot("n3-quantity")
            rule.onNodeWithText("150 g").performClick()
            rule.onNodeWithText("Ajouter").performClick()
            pump { exists("ajouté à déjeuner") }
            rule.mainClock.autoAdvance = true
            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }
            // The day's rings fill the top of the journal: scroll down to the added food.
            rule.waitUntilAtLeastOneExists(hasText("/ 148 g", substring = true), 10_000)
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("45,2 g prot.", substring = true))
            shot("n4-journal-after")
        }
    }
}
