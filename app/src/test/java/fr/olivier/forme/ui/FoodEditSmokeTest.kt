package fr.olivier.forme.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.feature.nutrition.ui.NEW_FOOD_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Config → Mes aliments → new food: "Enregistrer" turns on once the name and the protein are typed, and saves. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class FoodEditSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)
    private fun field(label: String) = rule.onNode(hasSetTextAction() and hasText(label, substring = true))
    private fun save() = rule.onNode(hasText("Enregistrer") and hasClickAction())

    @Test
    fun saveTurnsOnWithNameAndProtein() {
        rule.waitForHome()
        rule.openSettings()
        rule.onNode(hasText("Mes aliments") and hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag(NEW_FOOD_TAG), 15_000)
        rule.onNodeWithTag(NEW_FOOD_TAG).performClick()
        waitFor("Nouvel aliment")
        save().assertIsNotEnabled()

        field("Nom").performTextInput("Arancini Gorgonzola")
        save().assertIsNotEnabled() // protein still missing
        field("Protéines").performTextInput("11")
        field("Énergie").performTextInput("265")
        field("Lipides").performTextInput("10")
        save().assertIsEnabled()

        save().performClick()
        waitFor("Arancini Gorgonzola")
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Nouvel aliment")).fetchSemanticsNodes().isEmpty() }
    }

    /** V2.19.2: from Nutrition → Ajouter too, the sheet of a food opens its form and deletes it. */
    @Test
    fun editAndDeleteFromTheAddFoodSheet() {
        rule.waitForHome()
        rule.openSettings()
        rule.onNode(hasText("Mes aliments") and hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag(NEW_FOOD_TAG), 15_000)
        rule.onNodeWithTag(NEW_FOOD_TAG).performClick()
        waitFor("Nouvel aliment")
        field("Nom").performTextInput("Arancini Gorgonzola")
        field("Protéines").performTextInput("11")
        save().performClick()
        // The name is in the form's own field too: wait for the form to close, back on "Mes aliments".
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Nouvel aliment")).fetchSemanticsNodes().isEmpty() }
        waitFor("Arancini Gorgonzola")

        // A new food is among the recent ones of Nutrition → Ajouter.
        rule.onNode(hasContentDescription("Retour")).performClick()
        rule.tab("Nutrition")
        rule.waitUntilAtLeastOneExists(hasContentDescription("Ajouter à Déjeuner"), 15_000)
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasContentDescription("Ajouter à Déjeuner"))
        rule.onNode(hasContentDescription("Ajouter à Déjeuner")).performClick()
        rule.foodsTab()
        // "Récents" shows before the recent foods load: wait for the food itself.
        rule.waitUntilAtLeastOneExists(hasText("Arancini Gorgonzola") and hasClickAction(), 15_000)
        rule.onNode(hasText("Arancini Gorgonzola") and hasClickAction()).performClick()

        rule.onNode(hasText("Modifier") and hasClickAction()).performClick()
        waitFor("Modifier l'aliment")
        field("Protéines").performTextReplacement("12")
        save().performClick()
        waitFor("Récents")
        waitFor("12 g prot.")

        rule.onNode(hasText("Arancini Gorgonzola") and hasClickAction()).performClick()
        rule.onNode(hasText("Supprimer") and hasClickAction()).performClick()
        waitFor("Supprimer « Arancini Gorgonzola » ?")
        rule.onNode(hasText("Supprimer") and hasClickAction()).performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasText("Arancini Gorgonzola")).fetchSemanticsNodes().isEmpty() }
    }
}
