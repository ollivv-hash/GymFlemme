package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isOn
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * V2.20: Config → Compléments switches Oméga-3 on: it gets its box in the Nutrition journal next to creatine, and a
 * box ticked there is recorded. (The add dialog is tested on its own: AddSupplementDialogTest.)
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class SupplementsSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun switchOnAddAndTickSupplements() {
        rule.waitForHome()
        waitFor("3 g pris") // home tile: creatine on by default (3 g on a new install since V2.25)
        rule.openSettings()
        rule.onNode(hasText("Compléments") and hasClickAction()).performClick()
        waitFor("Oméga-3")
        waitFor("Vitamine D")

        rule.onNode(hasContentDescription("Oméga-3 actif")).performClick()
        waitFor("1 g par jour")
        rule.waitUntilAtLeastOneExists(hasText("Quantité par jour"), 15_000)

        shot("sp1-config-supplements")

        rule.onNode(hasContentDescription("Retour")).performClick()
        rule.tab("Nutrition")
        rule.waitUntilAtLeastOneExists(hasText("Oméga-3 1 g"), 15_000)
        waitFor("Créatine 3 g")
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Oméga-3 1 g"))
        val ticked = rule.onAllNodes(isOn()).fetchSemanticsNodes().size
        rule.onNode(hasText("Oméga-3 1 g") and hasClickAction()).performClick()
        rule.waitUntil(15_000) { rule.onAllNodes(isOn()).fetchSemanticsNodes().size == ticked + 1 }
        assertEquals(ticked + 1, rule.onAllNodes(isOn()).fetchSemanticsNodes().size)
        shot("sp2-nutrition-supplements")
    }
}
