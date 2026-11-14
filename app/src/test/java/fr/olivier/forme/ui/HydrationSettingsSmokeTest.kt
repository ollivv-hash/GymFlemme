package fr.olivier.forme.ui

import android.Manifest
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOff
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import fr.olivier.forme.MainActivity
import fr.olivier.forme.feature.hydration.reminder.HydrationReminderWorker
import fr.olivier.forme.feature.hydration.ui.HYDRATION_BONUS_TAG
import fr.olivier.forme.feature.hydration.ui.HYDRATION_CUSTOM_GOAL_TAG
import fr.olivier.forme.feature.hydration.ui.HYDRATION_REMINDERS_TAG
import fr.olivier.forme.feature.hydration.ui.WATER_INTRO_START_TAG
import fr.olivier.forme.feature.hydration.ui.WATER_INTRO_TAG
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * V2.29: the first-use card "C'est parti" switches the reminders on (permission granted); Réglages › Hydratation sets
 * a goal of one's own, the session bonus and the interval of the reminders.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HydrationSettingsSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun settings() = runBlocking {
        EntryPointAccessors.fromApplication(rule.activity.applicationContext, HydrationReminderWorker.Deps::class.java)
            .hydrationReminder().state()
    }

    @Test
    fun introThenSettings() {
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        rule.waitForHome()
        rule.waitUntilAtLeastOneExists(hasTestTag(WATER_INTRO_TAG), 15_000)
        rule.onNodeWithTag(WATER_INTRO_START_TAG).performScrollTo().performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasTestTag(WATER_INTRO_TAG)).fetchSemanticsNodes().isEmpty() }
        val state = settings()
        assertTrue(state.introSeen)
        assertTrue(state.enabled)

        rule.openSettings()
        rule.onNode(hasText("Hydratation") and hasClickAction()).performScrollTo().performClick()
        waitFor("Valeur calculée : 2 L")
        rule.onNodeWithTag(HYDRATION_CUSTOM_GOAL_TAG).assertIsOff().performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag(HYDRATION_CUSTOM_GOAL_TAG) and isOn(), 15_000)
        rule.onAllNodes(hasContentDescription("Plus") and hasClickAction())[0].performClick() // the goal's (the bonus has one too)
        waitFor("2,1 L")
        shot("hs1-hydration-settings")

        rule.onNodeWithTag(HYDRATION_BONUS_TAG).assertIsOn().performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag(HYDRATION_BONUS_TAG) and isOff(), 15_000)

        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasTestTag(HYDRATION_REMINDERS_TAG))
        rule.onNodeWithTag(HYDRATION_REMINDERS_TAG).assertIsOn()
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("120 min"))
        rule.onNode(hasText("120 min") and hasClickAction()).performClick()
        rule.waitUntilAtLeastOneExists(hasText("120 min") and androidx.compose.ui.test.isSelected(), 15_000)
        shot("hs2-hydration-reminders")
        // The home tile shows the goal of one's own, without the bonus.
        rule.onNode(hasContentDescription("Retour")).performClick()
        rule.tab("Accueil")
        rule.waitUntilAtLeastOneExists(hasContentDescription("Eau, 0 litre sur 2,1", substring = true), 15_000)
        assertEquals(true, settings().enabled)
    }
}
