package fr.olivier.forme.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import fr.olivier.forme.feature.sport.ui.home.bento.SESSION_TILE_TAG

/**
 * V2.22: the bento home screen and the floating bar (icons with descriptions, Config behind "Réglages" in the home
 * screen's header).
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.waitForHome() = waitUntilAtLeastOneExists(hasTestTag(SESSION_TILE_TAG), 15_000)

/** A tab of the floating bar: Accueil, Séances, Nutrition or Badges. */
fun ComposeTestRule.tab(label: String) = onNode(hasContentDescription(label) and hasClickAction()).performClick()

/** Config, from the home screen's header. */
fun ComposeTestRule.openSettings() = onNode(hasContentDescription("Réglages") and hasClickAction()).performClick()

/** The choice of a session ("Nouvelle séance" sheet), from the session tile. */
fun ComposeTestRule.openSessionChoice() = onNodeWithTag(SESSION_TILE_TAG).performClick()

