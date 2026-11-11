package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.MainActivity
import fr.olivier.forme.feature.nutrition.ui.BELOW_BMR_TAG
import fr.olivier.forme.feature.nutrition.ui.KCAL_CUSTOM_TAG
import fr.olivier.forme.feature.onboarding.ONBOARDING_NEXT_TAG
import fr.olivier.forme.feature.onboarding.ONBOARDING_SKIP_TAG
import fr.olivier.forme.feature.onboarding.Onboarding
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * V2.25: the first-launch wizard on a new install: 4 screens and the summary, "Passer", back, bounds checked; the
 * answers end up in the profile and a weigh-in, and it does not come back.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class OnboardingSmokeTest {
    @get:Rule
    val rule = createEmptyComposeRule()

    @Before
    fun showTheWizard() {
        System.clearProperty(Onboarding.SKIP_PROPERTY)
    }

    @After
    fun skipItAgain() {
        System.setProperty(Onboarding.SKIP_PROPERTY, "true")
    }

    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), 15_000)
    private fun gone(text: String) = rule.waitUntil(15_000) { rule.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isEmpty() }
    private fun field(label: String) = rule.onNode(hasSetTextAction() and hasText(label, substring = true))
    private fun next() = rule.onNodeWithTag(ONBOARDING_NEXT_TAG).performClick()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onAllNodes(isRoot())[0].captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun answerEverythingThenTheProfileHasIt() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Étape 1 sur 4")
            field("Prénom").performTextInput("Léa")
            shot("o1-you")
            next()

            waitFor("Étape 2 sur 4")
            field("Année de naissance").performTextInput("1995")
            rule.onNode(hasText("Femme") and hasClickAction()).performClick()
            // Out of bounds: a clear message, and no "Suivant".
            field("Taille").performTextInput("90")
            waitFor("Taille entre 100 et 250 cm")
            rule.onNodeWithTag(ONBOARDING_NEXT_TAG).assertIsNotEnabled()
            field("Taille").performTextClearance()
            field("Taille").performTextInput("165")
            field("Poids").performTextInput("60")
            gone("Taille entre")
            rule.onNodeWithTag(ONBOARDING_NEXT_TAG).assertIsEnabled()
            shot("o2-body")
            next()

            waitFor("Étape 3 sur 4")
            rule.onNode(hasText("Maintien") and hasClickAction()).performClick()
            rule.onNode(hasText("Léger") and hasClickAction()).performClick()
            waitFor("un peu de marche")
            // Back: the answers of the previous screen are kept.
            rule.onNode(hasText("Retour") and hasClickAction()).performClick()
            waitFor("Étape 2 sur 4")
            rule.onNode(hasText("165") and hasSetTextAction()).assertExists()
            next()
            waitFor("Étape 3 sur 4")
            shot("o3-goal")
            next()

            waitFor("Étape 4 sur 4")
            rule.onNode(hasText("3") and hasSetTextAction()).assertExists() // creatine: 3 g by default
            field("Protéines par dose").performTextInput("24")
            next()

            waitFor("Récapitulatif")
            waitFor("Calculée :")
            rule.onNode(hasText("Calculée : —")).assertDoesNotExist()
            shot("o4-summary")
            rule.onNodeWithTag(ONBOARDING_NEXT_TAG).performClick() // Valider
            rule.waitForHome()
            waitFor("Salut Léa")

            // In Réglages → Profil: the answers, and the calculated target with its switch.
            rule.openSettings()
            rule.onNode(hasText("Profil") and hasClickAction()).performClick()
            waitFor("Activité au quotidien")
            rule.onNode(hasText("1995") and hasSetTextAction()).assertExists()
            rule.onNodeWithTag(KCAL_CUSTOM_TAG).performScrollTo().performClick()
            waitFor("Ma cible (kcal)")
            // A personalised target under the basal metabolism: a neutral warning.
            repeat(20) { rule.onNode(androidx.compose.ui.test.hasContentDescription("Diminuer : Ma cible (kcal)")).performScrollTo().performClick() }
            rule.waitUntilAtLeastOneExists(hasTestTag(BELOW_BMR_TAG), 10_000)
            shot("o5-profile-custom")
        }
        // Not shown again.
        ActivityScenario.launch(MainActivity::class.java).use {
            rule.waitForHome()
            rule.onNode(hasText("Étape 1 sur 4")).assertDoesNotExist()
        }
    }

    @Test
    fun skipEveryScreen() {
        ActivityScenario.launch(MainActivity::class.java).use {
            waitFor("Étape 1 sur 4")
            repeat(4) { rule.onNodeWithTag(ONBOARDING_SKIP_TAG).performClick() }
            waitFor("Récapitulatif")
            waitFor("Sans objectif, pas de cible de calories.")
            next()
            rule.waitForHome()
            rule.onNode(hasText("Salut", substring = true)).assertDoesNotExist()
        }
    }
}
