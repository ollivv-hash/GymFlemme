package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * End-to-end smoke test of the main flow on the JVM (Robolectric, real Hilt graph and seeded database):
 * home proposes C → start → cardio + one set with rest timer → finish → recap.
 * Screenshots are written to build/smoke-screens for a visual check.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitFor(text: String) = try {
        rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)
    } catch (e: Throwable) {
        shot("failure-waiting-for-${text.filter { it.isLetterOrDigit() }}")
        throw e
    }

    private fun exists(text: String) =
        rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    private fun pump(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met" }
            rule.mainClock.advanceTimeBy(100)
            Thread.sleep(50)
        }
    }

    @Test
    fun weightDialog() {
        waitFor("+ pesée")
        rule.onNodeWithText("+ pesée").performClick()
        waitFor("Nouvelle pesée")
        shot("w-dialog")
    }

    @Test
    fun mainFlow() {
        waitFor("Nouvelle séance")
        shot("1-home")

        rule.onNodeWithText("Nouvelle séance").performClick()
        waitFor("C · Mixte + tronc dominant")
        shot("1b-new-session-sheet")
        rule.onNodeWithText("Démarrer").performClick()
        waitFor("Allure")
        shot("2-session-start")

        // Rower (cardio) is expanded first.
        rule.onNodeWithText("Enregistrer").performClick()
        waitFor("15 kg × 10")
        shot("3-session-chest-press")

        // The rest countdown ticks forever: freeze the Compose clock while it is displayed.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithText("Valider la série 1").performClick()
        val deadline = System.currentTimeMillis() + 15_000
        while (rule.onAllNodes(hasText("Repos", substring = true)).fetchSemanticsNodes().isEmpty()) {
            check(System.currentTimeMillis() < deadline) { "rest timer not shown" }
            rule.mainClock.advanceTimeBy(250)
            Thread.sleep(50)
        }
        shot("4-rest-timer")
        rule.onNode(androidx.compose.ui.test.hasContentDescription("Arrêter")).performClick()
        while (rule.onAllNodes(hasText("Repos ", substring = true)).fetchSemanticsNodes().isNotEmpty()) {
            check(System.currentTimeMillis() < deadline + 15_000) { "rest timer not stopped" }
            rule.mainClock.advanceTimeBy(250)
            Thread.sleep(50)
        }
        rule.mainClock.autoAdvance = true

        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Terminer la séance"))
        shot("5-session-bottom")
        // Note: dialogs containing a text field never report idle under Robolectric, so the
        // finish dialog is not driven here; the recap is covered by RecapSmokeTest.
    }
}
