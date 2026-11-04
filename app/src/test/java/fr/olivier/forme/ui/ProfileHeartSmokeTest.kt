package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasAnySibling
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate

/** Profile: birth year and sex give the advised maximum heart rate (Tanaka). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class ProfileHeartSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun advisedMaximumHeartRate() {
        rule.waitForHome()
        rule.openSettings()
        rule.onNodeWithText("Profil").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Toi (fréquence cardiaque)"), 10_000)
        val year = LocalDate.now().year - 46
        rule.onNode(hasSetTextAction() and hasText("Année de naissance")).performTextInput(year.toString())
        rule.onNodeWithText("Homme").performClick()
        // On the very first launch the database is busy loading Ciqual and the exercise library: the form only
        // appears once the stored profile is read (typed earlier, the year used to be wiped by that late read).
        rule.waitUntilAtLeastOneExists(hasText("46 ans : valeur conseillée", substring = true), 15_000)
        rule.onNodeWithText("176").assertExists()
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "h1-profile-heart.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** 2.12.1: training goals moved from Réglages to Profil, saved with « Enregistrer ». */
    @Test
    fun trainingGoalsAreSetInTheProfile() {
        rule.waitForHome()
        rule.openSettings()
        rule.onNodeWithText("Profil").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Objectifs d'entraînement"), 15_000)
        rule.onNodeWithText("Séances par semaine").performScrollTo()
        rule.onNode(hasContentDescription("Augmenter : Séances par semaine")).performClick()
        rule.onNode(hasText("Enregistrer") and hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Sources et licences"), 10_000) // back to Config

        rule.onNodeWithText("Profil").performClick()
        rule.waitUntilAtLeastOneExists(hasText("Objectifs d'entraînement"), 15_000)
        rule.onNodeWithText("Séances par semaine").performScrollTo()
        // Default 3 sessions a week, plus one.
        rule.onNode(hasText("4") and hasAnySibling(hasContentDescription("Augmenter : Séances par semaine"))).assertExists()
        // Réglages no longer shows them.
        rule.onNode(hasText("Enregistrer") and hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Sources et licences"), 10_000) // back to Config
        rule.onNode(hasText("Réglages et sauvegarde") and hasClickAction()).performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Thème"), 10_000)
        rule.onNodeWithText("Séances par semaine").assertDoesNotExist()
    }
}
