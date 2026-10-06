package fr.olivier.forme.ui

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** V2.4 lot 0: choose the AI app, then share the sample file + lead text to it. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AiShareSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)

    @Test
    fun chooseClaudeThenShareFileAndText() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        // A fake Claude app able to receive shared text.
        val claude = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.anthropic.claude"
                name = "com.anthropic.claude.ShareActivity"
                applicationInfo = ApplicationInfo().apply { packageName = "com.anthropic.claude" }
            }
        }
        shadowOf(app.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_SEND).setType("text/plain"), claude)
        shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName("com.anthropic.claude", "com.anthropic.claude.ShareActivity"))
        shadowOf(app.packageManager).addIntentFilterForActivity(
            ComponentName("com.anthropic.claude", "com.anthropic.claude.ShareActivity"),
            android.content.IntentFilter(Intent.ACTION_SEND).apply { addDataType("text/plain") },
        )

        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Nouvelle séance")
            rule.onNode(hasText("Config") and hasClickAction()).performClick()
            rule.onNodeWithText("Analyse IA").performClick()
            waitFor("App IA par défaut")
            rule.onNodeWithText("Claude").performClick()
            // The choice is saved asynchronously (DataStore): wait until the screen shows it.
            rule.waitUntilAtLeastOneExists(hasText("Claude") and isSelected(), 10_000)
            rule.onNodeWithText("Fichier + texte").performScrollTo().performClick()
            rule.waitForIdle()

            val started = shadowOf(app).nextStartedActivity
            assertNotNull(started)
            assertEquals("com.anthropic.claude", started.`package`)
            assertEquals(Intent.ACTION_SEND, started.action)
            assertTrue(started.getStringExtra(Intent.EXTRA_TEXT)!!.startsWith("Analyse le fichier joint"))
            @Suppress("DEPRECATION")
            val uri = started.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
            assertEquals("content", uri.scheme)
            assertTrue(uri.toString().endsWith("forme-test-partage.md"))
            assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        }
    }
}
