package fr.olivier.forme.update

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.olivier.forme.core.update.UpdateFeature
import javax.inject.Inject
import javax.inject.Singleton

/** "play" variant (V2.28): updates come from the store; no update code, no install permission. */
@Singleton
class NoUpdates @Inject constructor() : UpdateFeature {
    override fun onAppStart() = Unit

    @Composable
    override fun HomeCard(onOpen: () -> Unit, modifier: Modifier) = Unit

    @Composable
    override fun AboutSection(onOpen: () -> Unit) = Unit

    override fun register(builder: NavGraphBuilder, nav: NavController) = Unit

    override fun open(nav: NavController) = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds
    abstract fun bind(impl: NoUpdates): UpdateFeature
}
