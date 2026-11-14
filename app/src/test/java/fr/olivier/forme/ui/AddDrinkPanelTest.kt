package fr.olivier.forme.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.feature.hydration.data.HydrationDefaults
import fr.olivier.forme.feature.hydration.ui.AddDrinkPanel
import fr.olivier.forme.feature.hydration.ui.WATER_CONTAINER_TAG
import fr.olivier.forme.feature.hydration.ui.WATER_DRINK_TAG
import fr.olivier.forme.feature.hydration.ui.WATER_OTHER_ADD_TAG
import fr.olivier.forme.feature.hydration.ui.WATER_OTHER_FIELD_TAG
import fr.olivier.forme.feature.hydration.ui.WATER_OTHER_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * V2.29: a container adds the drink it usually holds, or the drink chosen; "Autre quantité" takes 10 to 2 000 ml.
 * (Default screen size: see EditIntakeDialogTest.)
 */
@RunWith(AndroidJUnit4::class)
class AddDrinkPanelTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun containersDrinksAndAnotherQuantity() {
        val added = mutableListOf<Pair<Long, Int>>()
        rule.setContent { AddDrinkPanel(HydrationDefaults.drinks, HydrationDefaults.containers, onAdd = { id, ml -> added += id to ml }) }

        // TalkBack: each container says what it adds.
        rule.onNodeWithContentDescription("Tasse, 200 ml, Café").performClick()
        rule.onNodeWithTag(WATER_CONTAINER_TAG + 4).performClick()
        // Tea chosen: the bottle holds tea.
        rule.onNodeWithTag(WATER_DRINK_TAG + 4).performClick()
        rule.onNodeWithContentDescription("Bouteille, 500 ml, Thé / tisane").performClick()

        rule.onNodeWithTag(WATER_OTHER_TAG).performClick()
        rule.onNodeWithTag(WATER_OTHER_FIELD_TAG).performTextInput("5")
        rule.onNodeWithTag(WATER_OTHER_ADD_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(WATER_OTHER_FIELD_TAG).performTextReplacement("2500")
        rule.onNodeWithTag(WATER_OTHER_ADD_TAG).assertIsNotEnabled()
        rule.onNodeWithTag(WATER_OTHER_FIELD_TAG).performTextReplacement("333")
        rule.onNodeWithTag(WATER_OTHER_ADD_TAG).assertIsEnabled().performClick()

        assertEquals(listOf(3L to 200, 1L to 750, 4L to 500, 4L to 330), added)
    }
}
