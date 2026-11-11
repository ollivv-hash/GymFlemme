package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.core.settings.AccentColor
import fr.olivier.forme.feature.settings.ui.accentSwatchTag
import fr.olivier.forme.ui.theme.pair
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * V2.24: Réglages → Apparence. A new accent applies at once (the active tab of the bar takes it), the mode too; the
 * theme left Préférences et sauvegarde.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AppearanceSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    /** The colour behind the icon of the active tab (top middle of its rounded square). */
    private fun activeTabColor(): Color {
        val image = rule.onNode(hasContentDescription("Réglages") and isSelectable()).captureToImage().toPixelMap()
        return image[image.width / 2, image.height / 6]
    }

    private fun assertSameColor(expected: Color, actual: Color) =
        assertEquals("$expected vs $actual", expected.copy(alpha = 1f).value, actual.copy(alpha = 1f).value)

    @Test
    fun anAccentAppliesAtOnce() {
        rule.waitForHome()
        rule.openSettings()
        rule.onNode(hasText("Apparence") and hasClickAction()).performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag(accentSwatchTag(AccentColor.GREEN)), 10_000)
        rule.onNodeWithTag(accentSwatchTag(AccentColor.GREEN)).assertIsSelected()
        rule.onNode(hasText("Suivre le système") and hasClickAction()).assertIsSelected()
        rule.onNode(hasContentDescription("Retour")).performClick()
        assertSameColor(AccentColor.GREEN.pair().accent, activeTabColor())

        rule.onNode(hasText("Apparence") and hasClickAction()).performScrollTo().performClick()
        rule.onNodeWithTag(accentSwatchTag(AccentColor.ORANGE)).performClick()
        rule.waitUntil(10_000) { rule.onAllNodes(hasContentDescription("Orange, sélectionnée")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasText("Clair") and hasClickAction()).performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodes(hasText("Clair") and hasClickAction() and androidx.compose.ui.test.isSelected()).fetchSemanticsNodes().isNotEmpty()
        }
        shot("ap1-appearance-orange-light")
        rule.onNode(hasContentDescription("Retour")).performClick()
        assertSameColor(AccentColor.ORANGE.pair().accent, activeTabColor())

        // The home screen too, without restarting.
        rule.tab("Accueil")
        rule.waitForHome()
        shot("ap2-home-orange-light")

        // The theme is no longer in Préférences et sauvegarde.
        rule.openSettings()
        rule.onNode(hasText("Préférences et sauvegarde") and hasClickAction()).performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasText("Entre deux séries"), 10_000)
        assertEquals(0, rule.onAllNodes(hasText("Thème")).fetchSemanticsNodes().size)
    }
}
