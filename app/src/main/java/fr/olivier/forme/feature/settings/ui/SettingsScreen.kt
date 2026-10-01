package fr.olivier.forme.feature.settings.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.core.backup.BackupRepository
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.core.settings.ThemeMode
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.ConfirmDialog
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.components.Stepper
import fr.olivier.forme.ui.components.parseDuration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
) : ViewModel() {
    val restSec = settings.defaultRestSec.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_REST_SEC)
    val theme = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setRest(seconds: Int) = viewModelScope.launch { settings.setDefaultRestSec(seconds) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun suggestedFileName() = backup.suggestedFileName()

    fun export(uri: Uri) = viewModelScope.launch {
        runCatching { backup.exportTo(uri) }
            .onSuccess { _messages.send("Sauvegarde exportée") }
            .onFailure { _messages.send("Échec de l'export : ${it.message}") }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        runCatching { backup.importFrom(uri) }
            .onSuccess {
                _messages.send(
                    "Import terminé : ${it.exercises} exercices, ${it.sessions} séances. " +
                        "Copie de sécurité : ${it.safetyBackup?.name}",
                )
            }
            .onFailure { _messages.send("Échec de l'import : ${it.message}") }
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val rest by viewModel.restSec.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingImport = uri
    }

    Scaffold(topBar = { FormeTopBar("Réglages", onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("Repos par défaut")
            Stepper(
                label = "Entre deux séries",
                valueText = Fmt.mmss(rest),
                onMinus = { viewModel.setRest(rest - 15) },
                onPlus = { viewModel.setRest(rest + 15) },
                onValueEntered = { text -> parseDuration(text)?.let(viewModel::setRest) },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Thème")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(selected = theme == mode, onClick = { viewModel.setTheme(mode) }, label = { Text(mode.label) })
                }
            }

            SectionTitle("Unités")
            Text("Charges en kg, distances en mètres.", style = MaterialTheme.typography.bodyMedium)

            SectionTitle("Sauvegarde")
            Text(
                "Fichier JSON complet (exercices, modèles, séances, pesées). Les photos ne sont pas incluses.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { exportLauncher.launch(viewModel.suggestedFileName()) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) { Text("Exporter une sauvegarde") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
            ) { Text("Importer une sauvegarde…") }
        }
    }

    pendingImport?.let { uri ->
        ConfirmDialog(
            title = "Remplacer toutes les données ?",
            text = "Les données actuelles seront remplacées par celles du fichier. " +
                "Une copie de sécurité est enregistrée automatiquement avant l'import.",
            confirmLabel = "Importer",
            onConfirm = {
                pendingImport = null
                viewModel.import(uri)
            },
            onDismiss = { pendingImport = null },
        )
    }
}
