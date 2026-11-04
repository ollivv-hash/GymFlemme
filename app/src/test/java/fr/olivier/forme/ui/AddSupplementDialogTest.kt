package fr.olivier.forme.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.feature.nutrition.data.SupplementUnit
import fr.olivier.forme.feature.nutrition.ui.AddSupplementDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** V2.20: "Nouveau complément" needs a name and an amount (decimal comma allowed), in the unit chosen. */
@RunWith(AndroidJUnit4::class)
class AddSupplementDialogTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun addsANamedSupplementWithItsAmountAndUnit() {
        var added: Triple<String, Double, SupplementUnit>? = null
        rule.setContent { AddSupplementDialog(onAdd = { name, amount, unit -> added = Triple(name, amount, unit) }, onDismiss = {}) }
        val add = rule.onNode(hasText("Ajouter") and hasClickAction())
        add.assertIsNotEnabled()

        rule.onNode(hasSetTextAction() and hasText("Nom", substring = true)).performTextInput("Spiruline")
        add.assertIsNotEnabled() // amount missing
        rule.onNode(hasSetTextAction() and hasText("Quantité par jour", substring = true)).performTextInput("2,5")
        rule.onNode(hasText("gélule") and hasClickAction()).performClick()
        rule.onNode(hasText("2,5 gélules par jour")).assertExists()
        add.assertIsEnabled()
        add.performClick()

        assertEquals(Triple("Spiruline", 2.5, SupplementUnit.CAPSULE), added)
    }
}
