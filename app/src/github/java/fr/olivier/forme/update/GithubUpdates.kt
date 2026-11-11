package fr.olivier.forme.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import fr.olivier.forme.BuildConfig
import fr.olivier.forme.R
import fr.olivier.forme.core.update.UpdateFeature
import fr.olivier.forme.feature.sport.ui.home.bento.BentoTile
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.theme.BentoType
import fr.olivier.forme.ui.theme.LocalBento
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data object UpdateRoute

const val UPDATE_CARD_TAG = "update_card"
const val UPDATE_CHECK_TAG = "update_check"

/** "github" variant (V2.28): the update from the releases repository, see [UpdateManager]. */
@Singleton
class GithubUpdates @Inject constructor(private val manager: UpdateManager) : UpdateFeature {
    override fun onAppStart() = manager.checkIfDue()

    @Composable
    override fun HomeCard(onOpen: () -> Unit, modifier: Modifier) {
        val state by manager.state.collectAsStateWithLifecycle()
        val available = state as? UpdateState.Available ?: return
        if (available.dismissed) return
        val c = LocalBento.current
        BentoTile(c.tile, modifier = modifier.fillMaxWidth().testTag(UPDATE_CARD_TAG), onClick = onOpen) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.update_card_title, available.update.versionName), style = BentoType.tileTitle, color = c.text)
                Text(stringResource(R.string.update_card_text), style = BentoType.body, color = c.textSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpen) { Text(stringResource(R.string.update_see)) }
                    TextButton(onClick = manager::dismiss) { Text(stringResource(R.string.update_later)) }
                }
            }
        }
    }

    @Composable
    override fun AboutSection(onOpen: () -> Unit) {
        val state by manager.state.collectAsStateWithLifecycle()
        val wifiOnly by manager.wifiOnly.collectAsStateWithLifecycle(true)
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(statusText(state), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(
                    onClick = manager::checkNow,
                    enabled = state !is UpdateState.Checking && state !is UpdateState.Downloading,
                    modifier = Modifier.testTag(UPDATE_CHECK_TAG),
                ) { Text(stringResource(R.string.update_check)) }
                if (state is UpdateState.Available || state is UpdateState.Ready) {
                    Button(onClick = onOpen) { Text(stringResource(R.string.update_see)) }
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.update_wifi_only)) },
                trailingContent = { Switch(checked = wifiOnly, onCheckedChange = { manager.setWifiOnly(it) }) },
            )
        }
    }

    override fun register(builder: NavGraphBuilder, nav: NavController) {
        builder.composable<UpdateRoute> { UpdateScreen(manager, onBack = { nav.popBackStack() }) }
    }

    override fun open(nav: NavController) = nav.navigate(UpdateRoute)
}

@Composable
private fun statusText(state: UpdateState): String = when (state) {
    UpdateState.Idle -> stringResource(R.string.update_installed, BuildConfig.VERSION_NAME)
    UpdateState.Checking -> stringResource(R.string.update_checking)
    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME)
    is UpdateState.Available -> stringResource(R.string.update_available, state.update.versionName)
    is UpdateState.Downloading -> stringResource(R.string.update_downloading)
    is UpdateState.Ready -> stringResource(R.string.update_ready, state.update.versionName)
    is UpdateState.Installing -> stringResource(R.string.update_installing)
    is UpdateState.Failed -> stringResource(errorText(state.error))
}

private fun errorText(error: UpdateError): Int = when (error) {
    UpdateError.OFFLINE -> R.string.update_error_offline
    UpdateError.RATE_LIMITED -> R.string.update_error_rate
    UpdateError.NO_RELEASE -> R.string.update_error_none
    UpdateError.INVALID -> R.string.update_error_invalid
    UpdateError.INCOMPATIBLE -> R.string.update_error_incompatible
    UpdateError.DOWNLOAD -> R.string.update_error_download
    UpdateError.CHECKSUM -> R.string.update_error_checksum
    UpdateError.INSTALL -> R.string.update_error_install
}

/** The new version, its notes, then download (Wi-Fi rule), permission to install, and installation. */
@Composable
private fun UpdateScreen(manager: UpdateManager, onBack: () -> Unit) {
    val state by manager.state.collectAsStateWithLifecycle()
    val wifiOnly by manager.wifiOnly.collectAsStateWithLifecycle(true)
    val context = LocalContext.current
    var unmetered by remember { mutableStateOf(manager.onUnmeteredNetwork()) }
    var canInstall by remember { mutableStateOf(manager.canInstall()) }
    // Back from Android's settings (install permission) or after a network change.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        unmetered = manager.onUnmeteredNetwork()
        canInstall = manager.canInstall()
    }
    LaunchedEffect(Unit) { if (state is UpdateState.Idle) manager.checkNow() }
    val update = when (val s = state) {
        is UpdateState.Available -> s.update
        is UpdateState.Downloading -> s.update
        is UpdateState.Ready -> s.update
        is UpdateState.Installing -> s.update
        is UpdateState.Failed -> s.update
        else -> null
    }

    Scaffold(topBar = { FormeTopBar(stringResource(R.string.update_title), onBack) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.update_installed, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            if (update != null) {
                Text(stringResource(R.string.update_new_version, update.versionName), style = MaterialTheme.typography.titleLarge)
                if (update.notes.isNotBlank()) Text(update.notes, style = MaterialTheme.typography.bodyMedium)
            }
            when (val s = state) {
                UpdateState.Idle, UpdateState.Checking -> Text(stringResource(R.string.update_checking))
                UpdateState.UpToDate -> Text(stringResource(R.string.update_up_to_date, BuildConfig.VERSION_NAME))
                is UpdateState.Available -> {
                    val waiting = !UpdatePolicy.mayDownload(wifiOnly, unmetered, forced = false)
                    if (waiting) {
                        Text(stringResource(R.string.update_waiting_wifi), style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { manager.download(s.update, forced = true) }) { Text(stringResource(R.string.update_download_anyway)) }
                    } else {
                        Button(onClick = { manager.download(s.update, forced = false) }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.update_download))
                        }
                    }
                }
                is UpdateState.Downloading -> {
                    Text(stringResource(R.string.update_downloading))
                    s.progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) }
                        ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                is UpdateState.Ready -> if (!canInstall) {
                    // First time: Android must allow GymFlemme to install apps.
                    Text(stringResource(R.string.update_permission_text), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                        )
                    }) { Text(stringResource(R.string.update_permission_open)) }
                } else {
                    Text(stringResource(R.string.update_ready, s.update.versionName))
                    Button(onClick = manager::install, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.update_install)) }
                }
                is UpdateState.Installing -> Text(stringResource(R.string.update_installing))
                is UpdateState.Failed -> {
                    Text(stringResource(errorText(s.error)), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = manager::checkNow) { Text(stringResource(R.string.update_retry)) }
                }
            }
            Text(stringResource(R.string.update_data_kept), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateModule {
    @Binds
    abstract fun bind(impl: GithubUpdates): UpdateFeature
}

/** Reaches the update manager outside injection (the screen tests). */
@dagger.hilt.EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateEntryPoint {
    fun updateManager(): UpdateManager
}
