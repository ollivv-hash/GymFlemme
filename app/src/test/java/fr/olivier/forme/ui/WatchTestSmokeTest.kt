package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Config → "Montre cardio (test)" opens and shows both test sections. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WatchTestSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun watchTestScreen() {
        rule.waitUntilAtLeastOneExists(hasText("Séance proposée"), 10_000)
        rule.onNode(hasText("Config") and hasClickAction()).performClick()
        rule.onNodeWithText("Montre cardio").performClick()
        rule.waitUntilAtLeastOneExists(hasText("1. FC en direct (Bluetooth)"), 10_000)
        rule.onNodeWithText("2. Health Connect (après séance)").assertExists()
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "h0-watch-test.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
