package fr.olivier.forme.ui

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.feature.hydration.data.HydrationDefaults
import fr.olivier.forme.feature.hydration.data.IntakeSource
import fr.olivier.forme.feature.hydration.data.WaterIntakeEntity
import fr.olivier.forme.feature.hydration.ui.EditIntakeDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** V2.29: an intake's drink, volume and time are changed; the volume stays within 10 – 2 000 ml. */
/*
 * Default screen size: at w411dp-xxhdpi, Robolectric never gets idle with a filled OutlinedTextField in an AlertDialog
 * (the empty one of "Autre quantité" is fine), whatever the dialog holds.
 */
@RunWith(AndroidJUnit4::class)
class EditIntakeDialogTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun changesTheDrinkAndTheVolume() {
        val zone = ZoneId.of("Europe/Paris")
        val at = Instant.parse("2026-10-01T08:05:00Z")
        var saved: Triple<Long, Int, Instant>? = null
        rule.setContent {
            EditIntakeDialog(
                WaterIntakeEntity(id = 3, timestamp = at, date = LocalDate.of(2026, 10, 1), drinkId = 3, volumeMl = 200, coefficientApplied = 1.0, source = IntakeSource.WATER_SCREEN),
                HydrationDefaults.drinks,
                zone,
                onSave = { d, v, t -> saved = Triple(d, v, t) },
                onDelete = {},
                onDismiss = {},
            )
        }
        rule.onNodeWithText("Heure : 10:05").assertExists()
        rule.onNodeWithText("Thé / tisane").performClick()
        rule.onNode(hasText("200")).performTextReplacement("5000")
        rule.onNodeWithText("Enregistrer").assertExists()
        rule.onNode(hasText("5000")).performTextReplacement("300")
        rule.onNodeWithText("Enregistrer").performClick()
        assertEquals(Triple(4L, 300, at), saved)
    }
}
