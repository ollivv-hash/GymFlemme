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
import androidx.compose.material3.TextButton
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
import fr.olivier.forme.core.backup.AutoBackupManager
import fr.olivier.forme.core.backup.AutoBackupResult
import fr.olivier.forme.core.settings.AutoBackupState
import fr.olivier.forme.ui.theme.accentButtonColors
import androidx.compose.material3.Card
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.mapLatest
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.core.settings.ThemeMode
import fr.olivier.forme.core.settings.TrainingGoals
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

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val backup: BackupRepository,
    private val autoBackupManager: AutoBackupManager,
    private val clock: Clock,
) : ViewModel() {
    val restSec = settings.defaultRestSec.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.DEFAULT_REST_SEC)
    val theme = settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.DARK)
    val goals = settings.trainingGoals.stateIn(viewModelScope, SharingStarted.Eagerly, TrainingGoals())
    val autoBackup = settings.autoBackup
        .mapLatest { state -> AutoBackupUi(state, state.targetUri?.let { autoBackupManager.targetName(it) }) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AutoBackupUi(AutoBackupState(), null))
    private val _autoBackupRunning = MutableStateFlow(false)
    val autoBackupRunning = _autoBackupRunning.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setRest(seconds: Int) = viewModelScope.launch { settings.setDefaultRestSec(seconds) }
    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setThemeMode(mode) }
    fun setGoals(goals: TrainingGoals) = viewModelScope.launch { settings.setTrainingGoals(goals) }
    fun suggestedFileName() = backup.suggestedFileName()

    fun setAutoBackupTarget(uri: Uri) = runAutoBackup { autoBackupManager.setTarget(uri) }
    fun autoBackupNow() = runAutoBackup { autoBackupManager.runNow() }
    fun disableAutoBackup() = viewModelScope.launch {
        autoBackupManager.clearTarget()
        _messages.send("Sauvegarde automatique désactivée")
    }

    private fun runAutoBackup(block: suspend () -> AutoBackupResult) = viewModelScope.launch {
        _autoBackupRunning.value = true
        val result = runCatching { block() }
        _autoBackupRunning.value = false
        result
            .onSuccess { _messages.send(if (it.targetError == null) "Sauvegarde enregistrée" else "Échec : ${it.targetError}") }
            .onFailure { _messages.send("Échec de la sauvegarde : ${it.message}") }
    }

    /** "aujourd'hui 18:42", "hier 18:42" or "02/10 18:42". */
    fun formatTime(millis: Long): String {
        val zone = clock.zone
        val at = Instant.ofEpochMilli(millis).atZone(zone)
        val today = LocalDate.now(clock)
        val time = at.format(DateTimeFormatter.ofPattern("HH:mm"))
        return when (at.toLocalDate()) {
            today -> "aujourd'hui $time"
            today.minusDays(1) -> "hier $time"
            else -> at.format(DateTimeFormatter.ofPattern("dd/MM")) + " $time"
        }
    }

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
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val autoBackup by viewModel.autoBackup.collectAsStateWithLifecycle()
    val autoBackupRunning by viewModel.autoBackupRunning.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingImport by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val autoBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::setAutoBackupTarget)
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

            SectionTitle("Objectifs d'entraînement")
            Stepper(
                label = "Séances par semaine",
                valueText = "${goals.sessionsPerWeek}",
                onMinus = { viewModel.setGoals(goals.copy(sessionsPerWeek = goals.sessionsPerWeek - 1)) },
                onPlus = { viewModel.setGoals(goals.copy(sessionsPerWeek = goals.sessionsPerWeek + 1)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Stepper(
                    label = "Séries / groupe : min",
                    valueText = "${goals.minSets}",
                    onMinus = { viewModel.setGoals(goals.copy(minSets = goals.minSets - 1)) },
                    onPlus = { viewModel.setGoals(goals.copy(minSets = goals.minSets + 1, maxSets = maxOf(goals.maxSets, goals.minSets + 1))) },
                    modifier = Modifier.weight(1f),
                )
                Stepper(
                    label = "max",
                    valueText = "${goals.maxSets}",
                    onMinus = { viewModel.setGoals(goals.copy(maxSets = maxOf(goals.minSets, goals.maxSets - 1))) },
                    onPlus = { viewModel.setGoals(goals.copy(maxSets = goals.maxSets + 1)) },
                    modifier = Modifier.weight(1f),
                )
            }
            // V2.12: band of the "Muscles" view (large muscles of the library), set apart from the groups' one.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Stepper(
                    label = "Séries / muscle : min",
                    valueText = "${goals.muscleMinSets}",
                    onMinus = { viewModel.setGoals(goals.copy(muscleMinSets = goals.muscleMinSets - 1)) },
                    onPlus = {
                        viewModel.setGoals(
                            goals.copy(muscleMinSets = goals.muscleMinSets + 1, muscleMaxSets = maxOf(goals.muscleMaxSets, goals.muscleMinSets + 1)),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
                Stepper(
                    label = "max",
                    valueText = "${goals.muscleMaxSets}",
                    onMinus = { viewModel.setGoals(goals.copy(muscleMaxSets = maxOf(goals.muscleMinSets, goals.muscleMaxSets - 1))) },
                    onPlus = { viewModel.setGoals(goals.copy(muscleMaxSets = goals.muscleMaxSets + 1)) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (goals.muscleMinSets != SettingsRepository.DEFAULT_MIN_SETS || goals.muscleMaxSets != SettingsRepository.DEFAULT_MAX_SETS) {
                TextButton(
                    onClick = {
                        viewModel.setGoals(goals.copy(muscleMinSets = SettingsRepository.DEFAULT_MIN_SETS, muscleMaxSets = SettingsRepository.DEFAULT_MAX_SETS))
                    },
                ) { Text("Revenir à ${SettingsRepository.DEFAULT_MIN_SETS}–${SettingsRepository.DEFAULT_MAX_SETS} par muscle") }
            }

            SectionTitle("Thème")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(selected = theme == mode, onClick = { viewModel.setTheme(mode) }, label = { Text(mode.label) })
                }
            }

            SectionTitle("Unités")
            Text("Charges en kg, distances en mètres.", style = MaterialTheme.typography.bodyMedium)

            SectionTitle("Sauvegarde automatique")
            AutoBackupSection(
                ui = autoBackup,
                running = autoBackupRunning,
                formatTime = viewModel::formatTime,
                onChoose = { autoBackupLauncher.launch(AutoBackupManager.TARGET_FILE_NAME) },
                onRunNow = viewModel::autoBackupNow,
                onDisable = viewModel::disableAutoBackup,
            )

            SectionTitle("Sauvegarde manuelle")
            Text(
                "Fichier JSON complet (exercices, modèles, séances, pesées, nutrition). Les photos ne sont pas incluses. " +
                    "Pour restaurer, « Importer » permet aussi de choisir le fichier dans Drive.",
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

data class AutoBackupUi(val state: AutoBackupState, val targetName: String?)

@Composable
private fun AutoBackupSection(
    ui: AutoBackupUi,
    running: Boolean,
    formatTime: (Long) -> String,
    onChoose: () -> Unit,
    onRunNow: () -> Unit,
    onDisable: () -> Unit,
) {
    val state = ui.state
    Text(
        "Un fichier JSON réécrit après chaque séance terminée et une fois par jour. " +
            "Choisis-le une seule fois : dans le sélecteur, ouvre Google Drive puis ton dossier. " +
            "Drive garde les versions précédentes (⋮ → Gérer les versions).",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (state.targetUri == null) {
        Button(
            onClick = onChoose,
            colors = accentButtonColors(),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) { Text("Choisir l'emplacement…") }
    } else {
        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Fichier : ${ui.targetName ?: AutoBackupManager.TARGET_FILE_NAME}", style = MaterialTheme.typography.titleSmall)
                val success = state.lastSuccessAt
                when {
                    state.lastError != null -> {
                        Text(state.lastError, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            success?.let { "Dernière réussite : ${formatTime(it)}" } ?: "Aucune sauvegarde réussie",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    success != null -> Text("✓ Dernière sauvegarde : ${formatTime(success)} · relue et vérifiée", modifier = Modifier.padding(top = 4.dp))
                    else -> Text("Pas encore de sauvegarde", modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Button(
            onClick = onRunNow,
            enabled = !running,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) { Text(if (running) "Sauvegarde en cours…" else "Sauvegarder maintenant") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedButton(onClick = onChoose, enabled = !running, modifier = Modifier.weight(1f)) { Text("Changer…") }
            OutlinedButton(onClick = onDisable, enabled = !running, modifier = Modifier.weight(1f)) { Text("Désactiver") }
        }
    }
    Text(
        "Les ${AutoBackupManager.LOCAL_KEEP} dernières sauvegardes sont aussi gardées dans l'app, par sécurité.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
