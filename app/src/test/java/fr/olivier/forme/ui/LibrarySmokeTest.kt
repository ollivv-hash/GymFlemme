package fr.olivier.forme.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.core.db.context
import fr.olivier.forme.core.launch.QuickAction
import fr.olivier.forme.feature.library.ui.AI_NOTICE
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** V2.9: exercise sheet from the session and the catalogue, choosing / removing an illustration. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class LibrarySmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

    /** On the very first launch the library is still being loaded: wait for the list, not just its header. */
    private fun waitForLibraryList() = rule.waitUntil(15_000) {
        rule.onAllNodes(hasText("exercice(s)", substring = true)).fetchSemanticsNodes().any { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.none { it.text.startsWith("0 ") }
        }
    }

    @Test
    fun sheetFromTheRunningSession() {
        val intent = QuickAction.START_SESSION.intent(context()).apply { removeFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        ActivityScenario.launch<MainActivity>(intent).use {
            waitFor("Terminer la séance")
            rule.waitUntilAtLeastOneExists(hasContentDescription("Fiche de l'exercice"), 15_000)
            rule.onAllNodes(hasContentDescription("Fiche de l'exercice"))[0].performClick()
            waitFor(AI_NOTICE)
            waitFor("Muscles")
            rule.waitUntilAtLeastOneExists(hasContentDescription("Rowing Machine"), 15_000) // frames decoded
            shot("l1-sheet-session")
            rule.onNode(hasText("Mouvement")).performScrollTo()
            shot("l2-sheet-details")
        }
    }

    @Test
    fun catalogueThumbnailsAndChangingTheIllustration() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForHome()
            rule.openSettings()
            rule.onNode(hasText("Catalogue des exercices") and hasClickAction()).performClick()
            waitFor("Rameur")
            shot("l3-catalogue")
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("Chest press"))

            rule.onAllNodes(hasText("Chest press") and hasClickAction())[0].performClick()
            rule.onNode(hasText("Illustration")).performScrollTo()
            waitFor("Machine Chest Press")
            rule.onNode(hasText("Changer") and hasClickAction()).performScrollTo().performClick()
            waitForLibraryList()
            rule.onNode(hasScrollAction()).performScrollToNode(hasText("Pec Deck"))
            shot("l4-picker")
            rule.onNode(hasText("Pec Deck") and hasClickAction()).performClick()

            waitFor("Pec Deck")
            rule.onNode(hasText("Voir la fiche") and hasClickAction()).performScrollTo()
            shot("l5-edit-illustration")
            rule.onAllNodes(hasText("Retirer") and hasClickAction())[0].performClick()
            waitFor("Choisir dans la bibliothèque")
        }
    }

    @Test
    fun libraryScreenFiltersAndAddsToTheCatalogue() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForHome()
            rule.openSettings()
            rule.onNode(hasText("Bibliothèque d'exercices") and hasClickAction()).performClick()
            waitForLibraryList()

            // A filter narrows the list; the seeded hack squat is already linked.
            rule.onNode(hasText("Muscle") and hasClickAction()).performClick()
            rule.onNode(hasText("Quadriceps") and hasClickAction()).performClick()
            rule.onNode(hasText("Lieu") and hasClickAction()).performClick()
            rule.onNode(hasText("Machines") and hasClickAction()).performClick()
            rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Leg Press")) // the list, not the filter row
            waitFor("Déjà dans mon catalogue")
            shot("l6-library-filtered")
            rule.onNode(hasText("Effacer") and hasClickAction()).performClick()

            rule.onNode(hasSetTextAction()).performTextInput("rope hammer")
            waitFor("Rope Hammer Curl")
            rule.onAllNodes(hasContentDescription("Ajouter à mon catalogue"))[0].performClick()
            waitFor("ajouté à ton catalogue")
            waitFor("Déjà dans mon catalogue")
            shot("l7-library-added")
            rule.onNode(hasText("Modifier") and hasClickAction()).performClick()
            rule.waitUntilAtLeastOneExists(hasText("Rope Hammer Curl") and hasSetTextAction(), 15_000)
            waitFor("Voir la fiche") // linked to its illustration
        }
    }

    @Test
    fun sourcesAndLicences() {
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForHome()
            rule.openSettings()
            rule.onNode(hasText("À propos") and hasClickAction()).performScrollTo().performClick()
            waitFor("Open Exercise Illustrations")
            waitFor("CC0 1.0")
            waitFor("2786aef")
            waitFor("Licence Ouverte / Etalab 2.0")
            shot("l8-sources")
        }
    }
}
