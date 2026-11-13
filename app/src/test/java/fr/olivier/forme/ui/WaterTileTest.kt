package fr.olivier.forme.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.feature.sport.ui.home.bento.WATER_ADD_TAG
import fr.olivier.forme.feature.sport.ui.home.bento.WATER_TILE_TAG
import fr.olivier.forme.feature.sport.ui.home.bento.WaterTile
import fr.olivier.forme.feature.sport.ui.home.bento.WaterTileState
import fr.olivier.forme.feature.sport.ui.home.waterTileQuip
import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.ui.theme.LightBento
import fr.olivier.forme.ui.theme.LocalBento
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** V2.29: the water tile of the home screen, on its own. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class WaterTileTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun plusAddsLongPressChoosesTheRestOpensAndTalkBackReadsOneSentence() {
        var added = 0
        var chosen = 0
        var opened = 0
        rule.setContent {
            CompositionLocalProvider(LocalBento provides LightBento) {
                WaterTile(
                    WaterTileState(1_800, 3_400, bonusMl = 500, line = "On avance, gorgée par gorgée.", defaultMl = 750, defaultDrink = "Eau"),
                    onOpen = { opened++ },
                    onAdd = { added++ },
                    onChoose = { chosen++ },
                )
            }
        }
        rule.onNodeWithContentDescription("Eau, 1,8 litre sur 3,4", substring = true).assertExists()
        rule.onNodeWithContentDescription("bonus séance de 500 millilitres", substring = true).assertExists()
        rule.onNodeWithTag(WATER_ADD_TAG).assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithContentDescription("Ajouter 750 ml, Eau").performClick()
        assertEquals(1, added)
        rule.onNodeWithTag(WATER_ADD_TAG).performTouchInput { longClick() }
        assertEquals(1, chosen)
        assertEquals(1, added)
        rule.onNodeWithTag(WATER_TILE_TAG).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theSecondaryLineFollowsTheProgress() {
        assertEquals(Quip.WATER_TILE_EMPTY, waterTileQuip(0, 3_400))
        assertEquals(Quip.WATER_TILE_LOW, waterTileQuip(1_600, 3_400))
        assertEquals(Quip.WATER_TILE_HIGH, waterTileQuip(1_700, 3_400))
        assertEquals(Quip.WATER_TILE_REACHED, waterTileQuip(3_400, 3_400))
    }
}
