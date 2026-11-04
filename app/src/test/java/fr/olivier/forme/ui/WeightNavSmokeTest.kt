package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
/** Home → weight card opens the Nutrition tab on "Poids"; the bottom bar "Accueil" returns home. */
class WeightNavSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** V2.22: the weight is no longer on the home screen; Nutrition → Poids from the floating bar, then back home. */
    @Test
    fun weightFromTheBarAndBackHome() {
        rule.waitForHome()
        rule.tab("Nutrition")
        rule.onNode(hasText("Poids") and androidx.compose.ui.test.hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Aucune pesée", substring = true), 10_000)
        shot("x-weight-from-bar")
        rule.tab("Accueil")
        rule.waitForHome()
        shot("x-back-home")
        // The Nutrition tab still works afterwards.
        rule.tab("Nutrition")
        rule.waitUntilAtLeastOneExists(hasText("Journal"), 10_000)
    }
}
