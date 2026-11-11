package fr.olivier.forme.core.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder

/**
 * Updating the app from GitHub (V2.28), present in the "github" variant only: the shared code knows this interface,
 * each variant provides its implementation ("play": nothing at all, see src/play).
 */
interface UpdateFeature {
    /** At launch: checks for a new version, at most once a day. */
    fun onAppStart()

    /** A discreet card on the home screen when a new version is there (nothing otherwise). */
    @Composable
    fun HomeCard(onOpen: () -> Unit, modifier: Modifier)

    /** Réglages → À propos: "Rechercher une mise à jour" and the Wi-Fi setting. */
    @Composable
    fun AboutSection(onOpen: () -> Unit)

    /** The update screen's route, added to the navigation graph; [open] navigates there. */
    fun register(builder: NavGraphBuilder, nav: NavController)

    fun open(nav: NavController)
}

val LocalUpdateFeature = staticCompositionLocalOf<UpdateFeature?> { null }
