package fr.olivier.forme.feature.settings.ui

import fr.olivier.forme.core.update.LocalUpdateFeature
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.R
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.settings.domain.DeveloperTaps
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import fr.olivier.forme.BuildConfig
import fr.olivier.forme.feature.settings.domain.Credit
import fr.olivier.forme.feature.settings.domain.Credits
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle

@HiltViewModel
class AboutViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val developerMode = settings.developerMode.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val taps = DeveloperTaps()

    /** A tap on the version: the message to show, if any. */
    fun onVersionTap(nowMs: Long = System.currentTimeMillis()): DeveloperTaps.Result? {
        if (developerMode.value) return null
        val result = taps.tap(nowMs)
        if (result == DeveloperTaps.Result.Enabled) viewModelScope.launch { settings.setDeveloperMode(true) }
        return result
    }

    fun disableDeveloperMode() {
        viewModelScope.launch { settings.setDeveloperMode(false) }
    }
}

const val ABOUT_VERSION_TAG = "about_version"
const val DEVELOPER_SWITCH_TAG = "developer_switch"

/**
 * Réglages → À propos (V2.23, formerly "Sources et licences", V2.10): the version, whose 7 taps turn the developer
 * mode on (off with its switch here), then the data and software shipped in the app, with their licences.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenUpdate: () -> Unit = {}, viewModel: AboutViewModel = hiltViewModel()) {
    val developerMode by viewModel.developerMode.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    fun say(text: String) = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        snackbar.showSnackbar(text)
    }

    Scaffold(topBar = { FormeTopBar(stringResource(R.string.about_title), onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME)) },
                supportingContent = { Text(stringResource(R.string.about_version_note)) },
                modifier = Modifier
                    .testTag(ABOUT_VERSION_TAG)
                    .clickable {
                        when (val result = viewModel.onVersionTap()) {
                            null -> say(resources.getString(R.string.developer_mode_already))
                            DeveloperTaps.Result.Enabled -> say(resources.getString(R.string.developer_mode_enabled))
                            is DeveloperTaps.Result.Countdown ->
                                say(resources.getQuantityString(R.plurals.developer_mode_taps_left, result.left, result.left))
                            DeveloperTaps.Result.Silent -> Unit
                        }
                    },
            )
            if (developerMode) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.developer_mode)) },
                    supportingContent = { Text(stringResource(R.string.developer_mode_hint)) },
                    trailingContent = {
                        Switch(
                            modifier = Modifier.testTag(DEVELOPER_SWITCH_TAG),
                            checked = true,
                            onCheckedChange = {
                                viewModel.disableDeveloperMode()
                                say(resources.getString(R.string.developer_mode_disabled))
                            },
                        )
                    },
                )
            }
            // V2.28 ("github" variant): "Rechercher une mise à jour".
            LocalUpdateFeature.current?.AboutSection(onOpenUpdate)
            SectionTitle(stringResource(R.string.about_sources))
            SectionTitle("Données")
            Credits.data.forEach { CreditCard(it) }
            SectionTitle("Logiciels")
            Credits.software.forEach { CreditCard(it) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CreditCard(credit: Credit) {
    val uri = LocalUriHandler.current
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
            Text(credit.title, style = MaterialTheme.typography.titleMedium)
            Text(credit.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(credit.usage, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            Text("Licence : ${credit.license}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            credit.note?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
        }
        Row(Modifier.padding(horizontal = 4.dp)) {
            TextButton(onClick = { uri.openUri(credit.url) }) { Text("Site") }
            TextButton(onClick = { uri.openUri(credit.licenseUrl) }) { Text("Licence") }
        }
    }
}
