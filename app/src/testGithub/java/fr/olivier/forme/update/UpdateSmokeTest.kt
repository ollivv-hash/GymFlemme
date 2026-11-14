package fr.olivier.forme.update

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import fr.olivier.forme.MainActivity
import fr.olivier.forme.ui.openSettings
import fr.olivier.forme.ui.waitForHome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * V2.28 ("github"): a new version on the home screen, "Plus tard", the update screen (Wi-Fi rule, permission to
 * install), and "Rechercher une mise à jour" in À propos. States are set by hand: no request to GitHub.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class UpdateSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val manager by lazy {
        EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext(), UpdateEntryPoint::class.java).updateManager()
    }
    private val update = UpdateManifest(60, "2.28.0", "https://example.org/a.apk", "a".repeat(64), 28, "Mise à jour automatique depuis GitHub.")

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun aNewVersionOnTheHomeScreenThenItsScreen() {
        rule.waitForHome()
        rule.runOnUiThread { manager.showForTest(UpdateState.Available(update, dismissed = false)) }
        rule.waitUntilAtLeastOneExists(hasTestTag(UPDATE_CARD_TAG), 15_000)
        waitFor("Version 2.28.0 disponible")
        shot("u1-home-card")

        rule.onNode(hasText("Voir") and hasClickAction()).performClick()
        waitFor("Nouvelle version : 2.28.0")
        waitFor("Mise à jour automatique depuis GitHub.")
        // No Wi-Fi in the test: the download waits, unless asked.
        waitFor("Le téléchargement attend une connexion Wi-Fi.")
        waitFor("Télécharger quand même")
        shot("u2-update-screen")

        // Downloaded and checked, but Android has not allowed GymFlemme to install apps yet.
        rule.runOnUiThread { manager.showForTest(UpdateState.Ready(update, File("x.apk"))) }
        waitFor("Autoriser cette source")
        waitFor("Ouvrir les paramètres")
        shot("u3-install-permission")
    }

    @Test
    fun laterHidesTheCardAndAboutChecksOnDemand() {
        rule.waitForHome()
        rule.runOnUiThread { manager.showForTest(UpdateState.Available(update, dismissed = false)) }
        rule.waitUntilAtLeastOneExists(hasTestTag(UPDATE_CARD_TAG), 15_000)
        // The card's own "Plus tard" (V2.29: the first-use card of the hydration has one too).
        rule.onNode(hasText("Plus tard") and hasClickAction() and hasAnyAncestor(hasTestTag(UPDATE_CARD_TAG))).performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(hasTestTag(UPDATE_CARD_TAG)).fetchSemanticsNodes().isEmpty() }

        rule.runOnUiThread { manager.showForTest(UpdateState.UpToDate) }
        rule.openSettings()
        rule.onNode(hasText("À propos") and hasClickAction()).performScrollTo().performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag(UPDATE_CHECK_TAG), 15_000)
        waitFor("GymFlemme est à jour")
        waitFor("Télécharger uniquement en Wi-Fi")
        shot("u4-about")
    }
}
