package fr.olivier.forme.feature.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.ai.data.AiAnalysis
import fr.olivier.forme.feature.ai.data.AiShare
import fr.olivier.forme.feature.ai.data.ShareTarget
import fr.olivier.forme.feature.ai.domain.AnalysisMarkdown
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    share: AiShare,
    private val analysis: AiAnalysis,
    private val clock: Clock,
) : ViewModel() {
    val targets: List<ShareTarget> = share.targets()
    val selected = settings.aiApp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val asText = settings.aiShareAsText.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun select(packageName: String?) = viewModelScope.launch { settings.setAiApp(packageName) }
    fun setAsText(value: Boolean) = viewModelScope.launch { settings.setAiShareAsText(value) }

    suspend fun prompt(): String = settings.aiPrompt.first() ?: AnalysisMarkdown.DEFAULT_PROMPT

    /** Saving the default text (or an empty one) goes back to following the app's default prompt. */
    fun savePrompt(text: String) = viewModelScope.launch {
        settings.setAiPrompt(text.takeIf { it.trim() != AnalysisMarkdown.DEFAULT_PROMPT.trim() })
    }

    fun analyseToday() = analysis.launch(LocalDate.now(clock))
}

/** Config → Analyse IA: default AI app, how to send, and the analysis instructions. */
@Composable
fun AiSettingsScreen(onBack: () -> Unit, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val asText by viewModel.asText.collectAsStateWithLifecycle()
    var prompt by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (prompt == null) prompt = viewModel.prompt() }

    Scaffold(topBar = { FormeTopBar("Analyse IA", onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "« Analyse IA » (accueil, bilan de séance) prépare un fichier avec tes consignes, la séance et la nutrition " +
                    "du jour, puis ouvre ton app IA. Rien n'est envoyé tant que tu n'as pas validé dans l'app.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )

            SectionTitle("App IA par défaut")
            TargetRow("Demander à chaque fois", selected == null) { viewModel.select(null) }
            val (ai, others) = viewModel.targets.partition { it.isAi }
            ai.forEach { t -> TargetRow(t.label, selected == t.packageName) { viewModel.select(t.packageName) } }
            if (others.isNotEmpty()) {
                Text("Autres apps", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                others.forEach { t -> TargetRow(t.label, selected == t.packageName) { viewModel.select(t.packageName) } }
            }

            SectionTitle("Méthode d'envoi")
            TargetRow("Fichier .md (recommandé, testé avec Claude)", !asText) { viewModel.setAsText(false) }
            TargetRow("Texte (pour les apps qui refusent les fichiers)", asText) { viewModel.setAsText(true) }
            Text(
                "Le contenu est aussi copié dans le presse-papiers, pour le coller si besoin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("Consignes d'analyse (prompt)")
            prompt?.let { text ->
                OutlinedTextField(
                    value = text,
                    onValueChange = { prompt = it },
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Button(onClick = { viewModel.savePrompt(text) }) { Text("Enregistrer") }
                    TextButton(onClick = {
                        prompt = AnalysisMarkdown.DEFAULT_PROMPT
                        viewModel.savePrompt(AnalysisMarkdown.DEFAULT_PROMPT)
                    }) { Text("Rétablir par défaut") }
                }
            }

            OutlinedButton(
                onClick = viewModel::analyseToday,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
            ) { Text("Tester avec la journée d'aujourd'hui") }
        }
    }
}

@Composable
private fun TargetRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 2.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label)
    }
}
