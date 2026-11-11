package fr.olivier.forme.feature.sport.ui.home.bento

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.olivier.forme.ui.theme.BentoDims
import fr.olivier.forme.ui.theme.DarkBento
import fr.olivier.forme.ui.theme.LightBento
import fr.olivier.forme.ui.theme.LocalBento

/** Sample data of the mock-up (V2.22), for the previews and the screenshot test. */
object BentoSamples {
    val session = SessionTileState(title = "B · Dos", week = 2 to 3, exercises = 5, typicalMinutes = 40)
    val calories = CaloriesTileState(kcal = 2012, target = 2137)
    val proteins = ProteinTileState(grams = 164, target = 148)
    val challenge = ChallengeTileState("Défi de novembre", "Douze coups", 5.0, 12.0, "5", "12", "séances", done = false)
    val creatine = SupplementsTileState("Créatine", "12 jours d'affilée", listOf(SupplementItem(1, "3 g pris", true)))
    val level = LevelTileState(2, "Flemmard repenti", 62, 30, 100)
}

/** The tiles of the home screen as they are laid out, without view models. */
@Composable
fun BentoHomeContent(
    session: SessionTileState,
    calories: CaloriesTileState?,
    proteins: ProteinTileState?,
    challenge: ChallengeTileState?,
    supplements: SupplementsTileState?,
    level: LevelTileState?,
    hello: String? = "Salut Olivier",
    greeting: String = "On bouge un peu ?",
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .background(LocalBento.current.background)
            .padding(start = BentoDims.screenMargin, end = BentoDims.screenMargin, top = 26.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(BentoDims.gap),
    ) {
        HomeHeader(hello, greeting, null, {}, Modifier.padding(bottom = 6.dp))
        SessionTile(session, "C'est parti", {}, {})
        TileRow(
            left = calories?.let { s -> { m: Modifier -> CaloriesTile(s, {}, m) } },
            right = proteins?.let { s -> { m: Modifier -> ProteinTile(s, {}, m) } },
        )
        TileRow(
            left = challenge?.let { s -> { m: Modifier -> ChallengeTile(s, {}, m) } },
            right = supplements?.let { s -> { m: Modifier -> SupplementsTile(s, { _, _ -> }, {}, m) } },
        )
        level?.let { LevelTile(it, {}) }
    }
}

@Composable
private fun Sample(dark: Boolean, withData: Boolean) {
    CompositionLocalProvider(LocalBento provides if (dark) DarkBento else LightBento) {
        if (withData) {
            BentoHomeContent(BentoSamples.session, BentoSamples.calories, BentoSamples.proteins, BentoSamples.challenge, BentoSamples.creatine, BentoSamples.level)
        } else {
            BentoHomeContent(
                SessionTileState(), CaloriesTileState(), ProteinTileState(), challenge = null,
                supplements = BentoSamples.creatine.copy(streak = "Série à lancer", items = listOf(SupplementItem(1, "5 g pris", false))),
                level = LevelTileState(1, "Flemmard débutant", 0, 0, 30), hello = null,
            )
        }
    }
}

@Preview(name = "Clair, avec données", widthDp = 390, heightDp = 844)
@Composable
private fun LightWithData() = Sample(dark = false, withData = true)

@Preview(name = "Sombre, avec données", widthDp = 390, heightDp = 844)
@Composable
private fun DarkWithData() = Sample(dark = true, withData = true)

@Preview(name = "Clair, sans données", widthDp = 390, heightDp = 844)
@Composable
private fun LightEmpty() = Sample(dark = false, withData = false)

@Preview(name = "Sombre, sans données", widthDp = 390, heightDp = 844)
@Composable
private fun DarkEmpty() = Sample(dark = true, withData = false)

@Preview(name = "Barre de navigation", widthDp = 390)
@Composable
private fun NavBarPreview() = CompositionLocalProvider(LocalBento provides LightBento) {
    fr.olivier.forme.ui.navigation.FloatingNavBar(
        listOf(
            fr.olivier.forme.ui.navigation.NavEntry("Accueil", fr.olivier.forme.R.drawable.ic_nav_home, true) {},
            fr.olivier.forme.ui.navigation.NavEntry("Séances", fr.olivier.forme.R.drawable.ic_nav_sessions, false) {},
            fr.olivier.forme.ui.navigation.NavEntry("Nutrition", fr.olivier.forme.R.drawable.ic_nav_nutrition, false) {},
            fr.olivier.forme.ui.navigation.NavEntry("Badges", fr.olivier.forme.R.drawable.ic_nav_badges, false) {},
            fr.olivier.forme.ui.navigation.NavEntry("Réglages", fr.olivier.forme.R.drawable.ic_nav_settings, false) {},
        ),
    )
}
