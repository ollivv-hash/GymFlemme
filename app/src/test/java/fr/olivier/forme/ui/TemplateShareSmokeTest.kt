package fr.olivier.forme.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.feature.sharing.IMPORT_BUTTON_TAG
import fr.olivier.forme.feature.sharing.SharedExercise
import fr.olivier.forme.feature.sharing.SharedTemplate
import fr.olivier.forme.feature.sharing.SharedTemplateFile
import fr.olivier.forme.feature.sharing.TemplateShareCodec
import fr.olivier.forme.feature.sport.ui.templates.TEMPLATE_QR_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * V2.27: Partager a template (first name offered, file or QR code), then a .gymflemme opened from another app: the
 * preview (recognised / to create), nothing written before Importer, a name already taken, Ajouter à ma rotation.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class TemplateShareSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun openTemplates() {
        rule.waitForHome()
        rule.openSettings()
        rule.onNode(hasText("Modèles de séance") and hasClickAction()).performClick()
        waitFor("Importer un modèle")
    }

    @Test
    fun shareATemplateAsQrCode() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openTemplates()
            // The list loads after the screen: wait for the template itself.
            rule.waitUntilAtLeastOneExists(hasContentDescription("Partager Tirage + tronc"), 15_000)
            rule.onNode(hasContentDescription("Partager Tirage + tronc")).performClick()
            waitFor("Seule la structure est partagée")
            waitFor("Afficher mon prénom")
            rule.onNode(hasText("Afficher un QR code") and hasClickAction()).performClick()
            rule.waitUntilAtLeastOneExists(hasTestTag(TEMPLATE_QR_TAG), 15_000)
            File(File("build/smoke-screens").apply { mkdirs() }, "t1-share-qr.png").outputStream().use { out ->
                rule.onNode(androidx.compose.ui.test.isDialog()).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    @Test
    fun openAFileFromAnotherApp() {
        val file = SharedTemplateFile(
            appVersion = "2.27.0",
            author = "Léa",
            template = SharedTemplate(
                name = "Tirage + tronc", // taken: becomes "Tirage + tronc (Léa)"
                code = "L",
                exercises = listOf(
                    SharedExercise(1, name = "CHEST PRESS", type = "WEIGHTED", muscleGroup = "PECTORAUX", targetSets = 4, targetRepsMin = 8, targetRepsMax = 10, restSec = 120),
                    SharedExercise(2, name = "Lower back", type = "WEIGHTED", muscleGroup = "DOS", targetSets = 3, targetRepsMin = 12, targetRepsMax = 15),
                ),
            ),
        )
        val dir = File(ApplicationProvider.getApplicationContext<android.content.Context>().cacheDir, "in").apply { mkdirs() }
        val f = File(dir, "lea.gymflemme").apply { writeText(TemplateShareCodec.encode(file)) }
        val intent = Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.fromFile(f))
        ActivityScenario.launch<MainActivity>(intent).use {
            waitFor("Partagé par Léa")
            waitFor("1 exercice reconnu, 1 à créer : Lower back")
            waitFor("reconnu : Chest press")
            rule.onNode(hasText("Tirage + tronc (Léa)")).assertExists()
            shot("t2-import-preview")

            // Nothing written yet: "Lower back" is not in the catalogue.
            rule.onNodeWithTag(IMPORT_BUTTON_TAG).performScrollTo().performClick()
            waitFor("« Tirage + tronc (Léa) » est importé.")
            rule.onNode(hasText("Ajouter à ma rotation") and hasClickAction()).performClick()
            waitFor("Ajouté à la fin de ta rotation.")
            shot("t3-import-done")
            rule.onNode(hasText("Terminé") and hasClickAction()).performClick()

            openTemplates()
            waitFor("L · Tirage + tronc (Léa)")
            waitFor("Rotation : position 4")
        }
    }
}
