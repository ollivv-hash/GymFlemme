package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.feature.settings.ui.ABOUT_VERSION_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * V2.23: Réglages is the 5th tab of the floating bar (no button in the home screen's header any more); the Bluetooth
 * log of Montre cardio only shows in developer mode, turned on by 7 taps on the version in À propos.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SettingsTabSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

    @Test
    fun fiveTabsWithReglagesLast() {
        rule.waitForHome()
        val tabs = rule.onAllNodes(isSelectable() and hasClickAction()).fetchSemanticsNodes()
            .map { it.config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single() }
        assertEquals(listOf("Accueil", "Séances", "Nutrition", "Badges", "Réglages"), tabs)
        // Only the tab: no settings button left in the home screen's header.
        assertEquals(1, rule.onAllNodes(hasContentDescription("Réglages")).fetchSemanticsNodes().size)
        rule.onNode(hasContentDescription("Accueil") and isSelectable()).assertIsSelected()

        rule.tab("Réglages")
        waitFor("Catalogue des exercices")
        rule.onNode(hasContentDescription("Réglages") and isSelectable()).assertIsSelected()
        rule.onNode(hasContentDescription("Retour")).assertDoesNotExist()
        shot("s1-settings-tab")

        // Back to the home screen and to Réglages again, from the bar.
        rule.tab("Accueil")
        rule.waitForHome()
        rule.tab("Réglages")
        waitFor("Catalogue des exercices")
    }

    @Test
    fun developerModeShowsTheBluetoothLog() {
        rule.waitForHome()
        rule.openSettings()
        rule.onNodeWithText("Montre cardio").performClick()
        waitFor("1. FC en direct (Bluetooth)")
        rule.onNodeWithText("Journal Bluetooth").assertDoesNotExist()
        rule.onNode(hasContentDescription("Retour")).performClick()

        rule.onNode(hasText("À propos") and hasClickAction()).performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(androidx.compose.ui.test.hasTestTag(ABOUT_VERSION_TAG), 10_000)
        repeat(6) { rule.onNodeWithTag(ABOUT_VERSION_TAG).performClick() }
        waitFor("Encore 1 appui pour activer le mode développeur")
        rule.onNodeWithTag(ABOUT_VERSION_TAG).performClick()
        waitFor("Mode développeur activé")
        waitFor("Affiche le journal Bluetooth")
        shot("s2-developer-mode")

        rule.onNode(hasContentDescription("Retour")).performClick()
        rule.onNodeWithText("Montre cardio").performClick()
        waitFor("Journal Bluetooth")
        rule.onNode(hasContentDescription("Retour")).performClick()

        // Off from the same screen.
        rule.onNode(hasText("À propos") and hasClickAction()).performScrollTo().performClick()
        rule.onNode(androidx.compose.ui.test.isToggleable()).performClick()
        waitFor("Mode développeur désactivé")
        rule.waitUntil(10_000) { rule.onAllNodes(hasText("Affiche le journal Bluetooth", substring = true)).fetchSemanticsNodes().isEmpty() }
        rule.onNode(hasContentDescription("Retour")).performClick()
        rule.onNodeWithText("Montre cardio").performClick()
        waitFor("1. FC en direct (Bluetooth)")
        rule.onNodeWithText("Journal Bluetooth").assertDoesNotExist()
    }
}
