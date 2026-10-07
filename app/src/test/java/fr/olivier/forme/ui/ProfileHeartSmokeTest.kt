package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
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
        rule.waitUntilAtLeastOneExists(hasText("Nouvelle séance"), 10_000)
        rule.onNode(hasText("Config") and hasClickAction()).performClick()
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
}
