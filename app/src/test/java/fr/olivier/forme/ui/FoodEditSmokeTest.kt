package fr.olivier.forme.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
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
        waitFor("Nouvelle séance")
        rule.onNode(hasText("Config") and hasClickAction()).performClick()
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
}
