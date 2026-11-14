package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.feature.hydration.ui.DRINK_FOOD_SKIP_TAG
import fr.olivier.forme.feature.hydration.ui.WATER_CONTAINER_TAG
import fr.olivier.forme.feature.hydration.ui.WATER_DRINK_TAG
import fr.olivier.forme.feature.hydration.ui.WATER_JOURNAL_TAG
import fr.olivier.forme.feature.sport.ui.home.bento.WATER_ADD_TAG
import fr.olivier.forme.feature.sport.ui.home.bento.WATER_TILE_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * V2.29: the "+" of the water tile adds the default container (gourde, 750 ml) and "Annuler" takes it back; a long
 * press opens the choice; the water screen adds and deletes (editing: EditIntakeDialogTest); Nutrition has its "Eau"
 * entry.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WaterSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)
    private fun waitForDescription(text: String) = rule.waitUntilAtLeastOneExists(hasContentDescription(text, substring = true), 15_000)

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun tileScreenAndJournal() {
        rule.waitForHome()
        rule.waitUntilAtLeastOneExists(hasTestTag(WATER_TILE_TAG), 15_000)
        rule.onNodeWithTag(WATER_TILE_TAG).performScrollTo()
        // No weight yet: 2 L.
        waitForDescription("Eau, 0 litre sur 2")
        shot("wa1-home-tile")

        // "+": the gourde (750 ml of water), then "Annuler".
        rule.onNodeWithTag(WATER_ADD_TAG).performClick()
        waitFor("+750 ml")
        waitForDescription("Eau, 0,8 litre sur 2")
        rule.onNode(hasText("Annuler") and hasClickAction()).performClick()
        waitForDescription("Eau, 0 litre sur 2")

        // Long press: the choice, a glass of water.
        rule.onNodeWithTag(WATER_ADD_TAG).performTouchInput { longClick() }
        waitFor("Ajouter une boisson")
        rule.onNodeWithTag(WATER_CONTAINER_TAG + 1).performClick()
        waitFor("+250 ml")
        waitForDescription("Eau, 0,3 litre sur 2")

        // The water screen: a cup holds coffee.
        rule.onNodeWithTag(WATER_TILE_TAG).performClick()
        waitFor("Calculé : 2 L · Utilisé : 2 L")
        rule.onNodeWithTag(WATER_CONTAINER_TAG + 2).performClick()
        waitForDescription(", Café, 200 ml")
        shot("wa2-water-screen")

        // (Editing an intake: EditIntakeDialogTest.)
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("+200 ml")).fetchSemanticsNodes().isEmpty() }

        // Deleted with its bin.
        rule.onNode(hasContentDescription("Supprimer Café", substring = true)).performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasContentDescription(", Café, 200 ml", substring = true)).fetchSemanticsNodes().isEmpty() }

        // (Another quantity: AddDrinkPanelTest.)

        // A caloric drink with no product: its choice opens first; not counting the calories adds the glass.
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("+200 ml")).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithTag(WATER_DRINK_TAG + 5).performClick()
        rule.onNodeWithTag(WATER_CONTAINER_TAG + 1).performClick()
        waitFor("Produit : Jus de fruits")
        shot("wa3-drink-food")
        rule.onNodeWithTag(DRINK_FOOD_SKIP_TAG).performClick()
        waitFor("+250 ml")
        waitForDescription(", Jus de fruits, 250 ml")
        // Nutrition: the "Eau" entry at the head of the journal.
        rule.onNode(hasContentDescription("Retour")).performClick()
        rule.tab("Nutrition")
        rule.waitUntilAtLeastOneExists(hasTestTag(WATER_JOURNAL_TAG), 15_000)
        waitForDescription("Eau, 0,5 litre sur 2")
        shot("wa4-nutrition-entry")
    }
}
