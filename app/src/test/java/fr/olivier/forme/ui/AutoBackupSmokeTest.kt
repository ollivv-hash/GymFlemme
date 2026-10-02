package fr.olivier.forme.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import fr.olivier.forme.MainActivity
import fr.olivier.forme.core.backup.AutoBackupWorker
import fr.olivier.forme.core.db.context
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Settings → automatic backup: a target file is configured and the status card shows the last backup. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class AutoBackupSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun configuredAutoBackup() {
        val target = tmp.newFile("forme-sauvegarde.json")
        val manager = EntryPointAccessors.fromApplication(context(), AutoBackupWorker.Deps::class.java).autoBackupManager()
        runBlocking { manager.setTarget(Uri.fromFile(target)) }

        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitUntilAtLeastOneExists(hasText("Séance proposée"), 10_000)
            rule.onNode(hasText("Config") and hasClickAction()).performClick()
            rule.onNodeWithText("Réglages et sauvegarde").performClick()
            rule.waitUntilAtLeastOneExists(hasText("Sauvegarde automatique"), 10_000)
            rule.waitUntilAtLeastOneExists(hasText("Dernière sauvegarde", substring = true), 10_000)
            rule.onNodeWithText("Désactiver").performScrollTo()
            shot("b1-auto-backup")
            rule.onNodeWithText("Désactiver").performClick()
            rule.waitUntilAtLeastOneExists(hasText("Choisir l'emplacement…"), 10_000)
            shot("b2-auto-backup-off")
        }
    }
}
