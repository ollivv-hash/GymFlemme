package fr.olivier.forme.feature.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.ai.data.AiShare
import fr.olivier.forme.feature.ai.data.ShareMode
import fr.olivier.forme.feature.ai.data.ShareTarget
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val share: AiShare,
) : ViewModel() {
    val targets: List<ShareTarget> = share.targets()
    val selected = settings.aiApp.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(packageName: String?) = viewModelScope.launch { settings.setAiApp(packageName) }

    /** Lot 0: sends a small sample analysis with [mode], to see what the chosen app does with it. */
    fun test(mode: ShareMode) = share.share(selected.value, mode, SAMPLE_FILE, SAMPLE, LEAD)

    companion object {
        const val SAMPLE_FILE = "forme-test-partage.md"
        const val LEAD = "Analyse le fichier joint en suivant les consignes qu'il contient."
        val SAMPLE = """
            # Forme · test de partage

            ## Consignes
            Ceci est un test d'envoi depuis l'app Forme. Réponds en une phrase : « Fichier bien reçu », puis cite le
            poids et les protéines du jour indiqués plus bas, pour prouver que tu as lu le contenu.

            ## Données (exemple)
            - Poids : 80,4 kg
            - Protéines du jour : 132 g / 144 g
            - Séance : C · Mixte + tronc dominant, 52 min, FC moyenne 117 bpm
        """.trimIndent()
    }
}

/** Config → Analyse IA: default AI app and (lot 0) sharing test. */
@Composable
fun AiSettingsScreen(onBack: () -> Unit, viewModel: AiSettingsViewModel = hiltViewModel()) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    Scaffold(topBar = { FormeTopBar("Analyse IA", onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("App IA par défaut")
            Text(
                "Le bouton « Analyse IA » ouvrira cette app avec la séance et la nutrition du jour. " +
                    "Rien n'est envoyé tant que tu n'as pas validé dans l'app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TargetRow("Demander à chaque fois", selected == null) { viewModel.select(null) }
            val (ai, others) = viewModel.targets.partition { it.isAi }
            ai.forEach { t -> TargetRow(t.label, selected == t.packageName) { viewModel.select(t.packageName) } }
            if (others.isNotEmpty()) {
                Text("Autres apps", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                others.forEach { t -> TargetRow(t.label, selected == t.packageName) { viewModel.select(t.packageName) } }
            }

            SectionTitle("Test du partage")
            Text(
                "Envoie un petit fichier d'exemple à l'app choisie, de trois façons. Note pour chacune ce qui arrive " +
                    "(fichier joint ? texte pré-rempli ? rien ?) et si l'IA cite bien le poids et les protéines.",
                style = MaterialTheme.typography.bodyMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                ShareMode.entries.forEach { mode ->
                    OutlinedButton(onClick = { viewModel.test(mode) }) { Text(mode.label) }
                }
            }
            Text(
                "Le contenu est aussi copié dans le presse-papiers à chaque essai.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
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
