package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.R
import fr.olivier.forme.core.db.context
import fr.olivier.forme.core.humor.Quip
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalTime

/** V2.15: a greeting quip on the home screen and in the new session sheet; serious mode makes them neutral. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class HumorSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

    private fun waitForAny(texts: Collection<String>) = rule.waitUntil(15_000) {
        texts.any { rule.onAllNodes(hasText(it)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun greetingAndStartLineThenSeriousMode() {
        val resources = context().resources
        val greeting = Quip.greeting(LocalTime.now())
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForHome()
            waitForAny(resources.getStringArray(greeting.humor).toList())
            shot("q1-home-greeting")

            rule.openSessionChoice()
            waitForAny(resources.getStringArray(R.array.humor_start).toList())
            shot("q2-start-line")
            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }

            rule.openSettings()
            rule.onNode(hasText("Préférences et sauvegarde") and hasClickAction()).performScrollTo().performClick()
            rule.onNode(hasText("Mode sérieux", substring = true) and hasClickAction()).performScrollTo().performClick()
            shot("q3-serious-mode")
            it.onActivity { a -> a.onBackPressedDispatcher.onBackPressed() }
            rule.tab("Accueil")
            waitFor(resources.getString(greeting.neutral))
        }
    }
}
