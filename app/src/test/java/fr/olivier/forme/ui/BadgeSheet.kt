package fr.olivier.forme.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import fr.olivier.forme.feature.badges.ui.BADGES_ACK_TAG

/**
 * V2.18: with a history, the badges already earned are recapped once in a sheet over the home screen. Waits for it
 * and closes it, then (V2.19) for the card of the month's challenge, drawn by the same evaluation: the home screen no
 * longer moves, so that the next clicks reach it whatever the timing.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeTestRule.acknowledgeBadges() {
    waitUntilAtLeastOneExists(hasTestTag(BADGES_ACK_TAG), 15_000)
    onNodeWithTag(BADGES_ACK_TAG).performClick()
    waitUntil(15_000) { onAllNodes(hasTestTag(BADGES_ACK_TAG)).fetchSemanticsNodes().isEmpty() }
    waitUntilAtLeastOneExists(hasText("🎯 Défi d", substring = true), 15_000)
    waitForIdle()
}
