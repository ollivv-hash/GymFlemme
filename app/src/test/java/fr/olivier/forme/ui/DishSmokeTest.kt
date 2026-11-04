package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.feature.nutrition.ui.ADD_INGREDIENT_TAG
import fr.olivier.forme.feature.nutrition.ui.NEW_DISH_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * V2.21: a dish made in Config → Mes aliments from a Ciqual food, in 2 servings, is proposed by the serving in
 * Nutrition → Ajouter; a meal of the journal is then saved as a new dish, its foods as ingredients.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class DishSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

    private fun exists(text: String) =
        rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()

    /** Text fields keep a blinking cursor: the clock is driven by hand while one is focused. */
    private fun pump(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met" }
            rule.mainClock.advanceTimeBy(100)
            Thread.sleep(50)
        }
    }

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun makeADishAddItAndSaveAMealAsADish() {
        rule.waitForHome()
        rule.openSettings()
        rule.onNode(hasText("Mes aliments") and hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag(NEW_DISH_TAG), 15_000)
        rule.onNodeWithTag(NEW_DISH_TAG).performClick()
        waitFor("Nouveau plat")

        rule.mainClock.autoAdvance = false
        rule.onNode(hasSetTextAction() and hasText("Nom du plat", substring = true)).performTextInput("Poulet du dimanche")
        rule.onNodeWithTag(ADD_INGREDIENT_TAG).performClick()
        pump { exists("Ajouter un ingrédient") && exists("Rechercher un aliment") }
        rule.onNode(hasSetTextAction() and hasText("Rechercher un aliment", substring = true)).performTextInput("poulet filet grill")
        pump { exists("Poulet, filet sans peau grillé/poêlé") }
        rule.onNode(hasText("Poulet, filet sans peau grillé/poêlé") and hasClickAction()).performClick()
        pump { exists("Pour 100 g") }
        rule.onNode(hasText("200 g") and hasClickAction()).performClick()
        rule.onNode(hasText("Ajouter") and hasClickAction()).performClick()

        // Back on the dish: 200 g of chicken (30.1 g of protein per 100 g), 2 servings of 100 g.
        pump { exists("Plat entier : 200 g") }
        rule.onNode(hasText("+") and hasClickAction()).performClick()
        pump { exists("Une portion (2 en tout) : 100 g") }
        shot("di1-dish")
        rule.onNode(hasText("Enregistrer") and hasClickAction()).performClick()
        pump { exists("Poulet du dimanche") && !exists("Nouveau plat") }
        rule.mainClock.autoAdvance = true
        waitFor("Plat ·")

        // Nutrition → Ajouter: the dish is among the recent foods, one serving proposed.
        rule.onNode(hasContentDescription("Retour")).performClick()
        rule.tab("Nutrition")
        rule.waitUntilAtLeastOneExists(hasContentDescription("Ajouter à Déjeuner"), 15_000)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Ajouter à Déjeuner"))
        rule.onNode(hasContentDescription("Ajouter à Déjeuner")).performClick()
        rule.waitUntilAtLeastOneExists(hasText("Poulet du dimanche") and hasClickAction(), 15_000)
        rule.onNode(hasText("Poulet du dimanche") and hasClickAction()).performClick()
        waitFor("100 g")
        rule.onNode(hasText("Ajouter") and hasClickAction()).performClick()
        waitFor("ajouté à déjeuner")

        // The journal: one line for the dish, and its meal saved as a new dish.
        rule.onNode(hasContentDescription("Retour")).performClick()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Enregistrer comme plat"))
        waitFor("Poulet du dimanche")
        shot("di2-journal")
        rule.onNode(hasText("Enregistrer comme plat") and hasClickAction()).performClick()
        waitFor("Nouveau plat")
        waitFor("Plat entier : 100 g")
        waitFor("Poulet du dimanche")
        shot("di3-meal-as-dish")
    }
}
