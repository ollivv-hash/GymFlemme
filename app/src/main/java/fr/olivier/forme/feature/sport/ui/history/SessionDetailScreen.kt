package fr.olivier.forme.feature.sport.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.domain.SessionReport
import fr.olivier.forme.feature.sport.domain.SessionTextFormatter
import fr.olivier.forme.ui.components.ConfirmDialog
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.navigation.SessionDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<SessionDetailRoute>()
    val justFinished = route.justFinished
    private val _report = MutableStateFlow<SessionReport?>(null)
    val report = _report.asStateFlow()

    init {
        viewModelScope.launch { _report.value = sessions.buildReport(route.sessionId) }
    }

    fun text(): String? = _report.value?.let(SessionTextFormatter::format)

    fun delete(then: () -> Unit) = viewModelScope.launch {
        sessions.delete(route.sessionId)
        then()
    }
}

private val titleDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy · HH:mm", Fmt.locale)

@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    onOpenExercise: (Long) -> Unit,
    onCreateTemplate: () -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val report by viewModel.report.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            FormeTopBar(if (viewModel.justFinished) "Récapitulatif" else "Séance", onBack) {
                TextButton(onClick = { deleting = true }) { Text("Supprimer") }
            }
        },
    ) { padding ->
        val r = report ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                listOfNotNull(r.templateCode, r.templateName).joinToString(" · ").ifEmpty { "Séance libre" },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                listOfNotNull(
                    r.startedAt.format(titleDate),
                    r.durationMin?.let { "$it min" },
                    r.feeling?.let { "ressenti $it/5" },
                ).joinToString(" · "),
            )

            if (r.records.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("🏆 Records battus", style = MaterialTheme.typography.titleMedium)
                        r.records.forEach { ex ->
                            Text(
                                ex.name + " : " + listOfNotNull(
                                    ex.newRecords.loadKg?.let { "charge max ${Fmt.kg(it)}" },
                                    ex.newRecords.volumeKg?.let { "volume ${Fmt.kg(it)}" },
                                ).joinToString(", "),
                            )
                        }
                    }
                }
            }

            SectionTitle("Volume")
            Text(SessionTextFormatter.volumeLine(r.volume))
            r.previous?.let { prev ->
                val diff = r.volume.totalKg - prev.volume.totalKg
                val pct = if (prev.volume.totalKg > 0) " (${(diff / prev.volume.totalKg * 100).roundToInt()} %)" else ""
                Text(
                    "Séance précédente (${prev.startedAt.format(DateTimeFormatter.ofPattern("dd/MM", Fmt.locale))}) : " +
                        "${Fmt.kg(prev.volume.totalKg)} → ${if (diff >= 0) "+" else ""}${Fmt.kg(diff)}$pct",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SectionTitle("Exercices")
            r.exercises.forEach { ex ->
                Column(Modifier.fillMaxWidth().clickable { onOpenExercise(ex.exerciseId) }.padding(vertical = 6.dp)) {
                    Text(ex.name, style = MaterialTheme.typography.titleSmall)
                    Text(SessionTextFormatter.exerciseLine(ex), style = MaterialTheme.typography.bodyMedium)
                }
            }
            r.notes?.let {
                SectionTitle("Notes")
                Text(it)
            }

            Button(
                onClick = { viewModel.text()?.let { copy(context, it) } },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            ) { Text("Copier le bilan texte") }
            OutlinedButton(onClick = onCreateTemplate, modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp)) {
                Text("Créer un modèle depuis cette séance")
            }
        }
    }

    if (deleting) {
        ConfirmDialog(
            title = "Supprimer cette séance ?",
            text = "Ses séries disparaissent de l'historique et des records. Action définitive.",
            confirmLabel = "Supprimer",
            onConfirm = {
                deleting = false
                viewModel.delete(onBack)
            },
            onDismiss = { deleting = false },
        )
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Bilan de séance", text))
    Toast.makeText(context, "Bilan copié", Toast.LENGTH_SHORT).show()
}
