package fr.olivier.forme.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.R
import fr.olivier.forme.core.settings.AccentColor
import fr.olivier.forme.ui.theme.pair
import fr.olivier.forme.ui.theme.withAccent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.olivier.forme.feature.sport.ui.home.bento.BentoHomeContent
import fr.olivier.forme.feature.sport.ui.home.bento.BentoSamples
import fr.olivier.forme.feature.sport.ui.home.bento.CaloriesTile
import fr.olivier.forme.feature.sport.ui.home.bento.CaloriesTileState
import fr.olivier.forme.feature.sport.ui.home.bento.HomeHeader
import fr.olivier.forme.feature.sport.ui.home.bento.LEVEL_TILE_TAG
import fr.olivier.forme.feature.sport.ui.home.bento.LevelTile
import fr.olivier.forme.feature.sport.ui.home.bento.ProteinTile
import fr.olivier.forme.feature.sport.ui.home.bento.ProteinTileState
import fr.olivier.forme.feature.sport.ui.home.bento.SESSION_START_TAG
import fr.olivier.forme.feature.sport.ui.home.bento.SessionTile
import fr.olivier.forme.feature.sport.ui.home.bento.SessionTileState
import fr.olivier.forme.feature.sport.ui.home.bento.SupplementItem
import fr.olivier.forme.feature.sport.ui.home.bento.SupplementsTile
import fr.olivier.forme.feature.sport.ui.home.bento.grouped
import fr.olivier.forme.ui.navigation.FloatingNavBar
import fr.olivier.forme.ui.navigation.NavEntry
import fr.olivier.forme.ui.theme.DarkBento
import fr.olivier.forme.ui.theme.LightBento
import fr.olivier.forme.ui.theme.LocalBento
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** V2.22: the bento tiles of the home screen, on their own (sample data of the mock-up). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w390dp-h844dp-xxhdpi")
class HomeTilesTest {
    @get:Rule
    val rule = createComposeRule()

    private fun shot(name: String) {
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use {
            rule.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun home(dark: Boolean, accent: AccentColor = AccentColor.GREEN) {
        rule.setContent {
            CompositionLocalProvider(LocalBento provides (if (dark) DarkBento else LightBento).withAccent(accent.pair(), dark)) {
                Box(Modifier.fillMaxSize()) {
                    BentoHomeContent(
                        BentoSamples.session, BentoSamples.calories, BentoSamples.proteins, BentoSamples.challenge,
                        BentoSamples.creatine, BentoSamples.level, modifier = Modifier.fillMaxSize(),
                    )
                    FloatingNavBar(
                        listOf(
                            NavEntry("Accueil", R.drawable.ic_nav_home, true) {},
                            NavEntry("Séances", R.drawable.ic_nav_sessions, false) {},
                            NavEntry("Nutrition", R.drawable.ic_nav_nutrition, false) {},
                            NavEntry("Badges", R.drawable.ic_nav_badges, false) {},
                            NavEntry("Réglages", R.drawable.ic_nav_settings, false) {},
                        ),
                        Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    @Test
    fun lightHomeShowsEveryTile() {
        home(dark = false)
        rule.onNodeWithText("PROCHAINE SÉANCE").assertExists()
        rule.onNodeWithText("B · Dos").assertExists()
        rule.onNodeWithText("Sem. 2 / 3").assertExists()
        rule.onNodeWithText("5 exercices · ~40 min").assertExists()
        rule.onNodeWithContentDescription("Calories, ${grouped(2012)} sur ${grouped(2137)} kilocalories").assertExists()
        rule.onNodeWithText("objectif 148 g atteint", substring = true).assertExists()
        rule.onNodeWithText("5 / 12 séances", substring = true).assertExists()
        rule.onNodeWithText("12 jours d'affilée", substring = true).assertExists()
        rule.onNodeWithText("62 / 100", substring = true).assertExists()
        rule.onNode(hasContentDescription("Badges")).assertExists()
        rule.onNode(hasContentDescription("Réglages")).assertExists()
        shot("bento-light")
    }

    @Test
    fun darkHome() {
        home(dark = true)
        rule.onNodeWithText("B · Dos").assertExists()
        shot("bento-dark")
    }

    /** V2.24: the home screen in each accent, light and dark (screenshots for review). */
    @Test
    fun eachAccent() {
        var current by mutableStateOf(AccentColor.GREEN to false)
        rule.setContent {
            val (accent, dark) = current
            CompositionLocalProvider(LocalBento provides (if (dark) DarkBento else LightBento).withAccent(accent.pair(), dark)) {
                BentoHomeContent(
                    BentoSamples.session, BentoSamples.calories, BentoSamples.proteins, BentoSamples.challenge,
                    BentoSamples.creatine, BentoSamples.level, modifier = Modifier.fillMaxSize(),
                )
            }
        }
        AccentColor.entries.forEach { accent ->
            listOf(false, true).forEach { dark ->
                current = accent to dark
                rule.waitForIdle()
                shot("accent-${accent.name.lowercase()}-${if (dark) "dark" else "light"}")
            }
        }
    }

    @Test
    fun withoutMealsTheNumbersAreDashesAndAddAMeal() {
        var opened = 0
        rule.setContent {
            CompositionLocalProvider(LocalBento provides LightBento) {
                androidx.compose.foundation.layout.Row {
                    CaloriesTile(CaloriesTileState(), { opened++ }, Modifier.weight(1f))
                    ProteinTile(ProteinTileState(target = 148), { opened++ }, Modifier.weight(1f))
                }
            }
        }
        assertEquals(2, rule.onAllNodes(hasText("—")).fetchSemanticsNodes().size)
        assertEquals(2, rule.onAllNodes(hasText("Ajouter un repas", substring = true)).fetchSemanticsNodes().size)
        rule.onNodeWithContentDescription("Calories, aucun repas aujourd'hui, ajouter un repas").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun withoutChallengeTheSupplementsTakeTheWholeWidth() {
        rule.setContent {
            CompositionLocalProvider(LocalBento provides LightBento) {
                BentoHomeContent(BentoSamples.session, null, null, challenge = null, supplements = BentoSamples.creatine, level = null)
            }
        }
        rule.onNodeWithText("Défi de novembre").assertDoesNotExist()
        rule.onNodeWithText("Calories").assertDoesNotExist()
        val tile = rule.onNodeWithText("12 jours d'affilée", substring = true).fetchSemanticsNode().boundsInRoot
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assert(tile.width > root.width * 0.7f) { "the supplements tile should span the width" }
    }

    @Test
    fun theCreatineBoxRecordsTheIntake() {
        var toggled: Pair<Long, Boolean>? = null
        rule.setContent {
            CompositionLocalProvider(LocalBento provides LightBento) {
                SupplementsTile(
                    BentoSamples.creatine.copy(items = listOf(SupplementItem(7, "5 g pris", false))),
                    onToggle = { item, taken -> toggled = item.id to taken },
                    onMore = {},
                )
            }
        }
        rule.onNode(androidx.compose.ui.test.isToggleable()).assertIsOff()
        rule.onNodeWithText("5 g pris").performClick()
        assertEquals(7L to true, toggled)
    }

    @Test
    fun theLevelOpensTheBadgesAndTheHeaderTheAnalysis() {
        var badges = 0
        var analyse = 0
        var started = 0
        var chose = 0
        rule.setContent {
            CompositionLocalProvider(LocalBento provides LightBento) {
                androidx.compose.foundation.layout.Column {
                    HomeHeader("Salut Olivier", "On bouge un peu ?", null, { analyse++ })
                    SessionTile(SessionTileState(title = "B · Dos"), "C'est parti", { started++ }, { chose++ })
                    LevelTile(BentoSamples.level, { badges++ })
                }
            }
        }
        rule.onNodeWithTag(LEVEL_TILE_TAG).performClick()
        rule.onNodeWithContentDescription("Analyse IA du jour").performClick()
        // V2.23: settings left the header for the navigation bar.
        rule.onNodeWithContentDescription("Réglages").assertDoesNotExist()
        rule.onNodeWithTag(SESSION_START_TAG).performClick()
        rule.onNodeWithText("B · Dos").performClick()
        assertEquals(listOf(1, 1, 1, 1), listOf(badges, analyse, started, chose))
    }
}
