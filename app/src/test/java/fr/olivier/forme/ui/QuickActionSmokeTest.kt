package fr.olivier.forme.ui

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.core.db.context
import fr.olivier.forme.core.launch.QuickAction
import fr.olivier.forme.feature.nutrition.domain.MealType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalTime

/** V2.6: launcher shortcuts open the right screen; the session one starts the session the rotation proposes. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class QuickActionSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun launch(action: QuickAction) =
        ActivityScenario.launch<MainActivity>(action.intent(context()).apply { removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)

    @Test
    fun shortcutsArePublishedAtStartup() {
        launch(QuickAction.SCAN).use {
            waitFor("Scanner")
            val ids = ShortcutManagerCompat.getDynamicShortcuts(context()).map { it.id }.sorted()
            assertEquals(QuickAction.entries.map { it.name }.sorted(), ids)
        }
    }

    @Test
    fun startSessionOpensTheProposedSession() {
        launch(QuickAction.START_SESSION).use {
            waitFor("Terminer la séance")
        }
    }

    @Test
    fun addMealOpensTheSearchOnTheCurrentMeal() {
        launch(QuickAction.ADD_MEAL).use {
            waitFor("Rechercher un aliment")
        }
    }

    @Test
    fun scanOpensTheScannerOnTheCurrentMeal() {
        launch(QuickAction.SCAN).use {
            waitFor("Scanner · ${MealType.at(LocalTime.now()).label}")
        }
    }
}
