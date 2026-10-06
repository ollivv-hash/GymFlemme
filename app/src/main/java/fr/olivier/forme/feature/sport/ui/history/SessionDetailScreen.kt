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
import fr.olivier.forme.feature.ai.data.AiAnalysis
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.heart.data.HeartSync
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import fr.olivier.forme.ui.navigation.SessionDetailRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import fr.olivier.forme.feature.heart.data.SessionHeartEntity
import fr.olivier.forme.feature.heart.domain.SetHeart
import fr.olivier.forme.feature.heart.ui.ExercisePeaks
import fr.olivier.forme.feature.heart.ui.SessionHeartSection
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject
import kotlin.math.roundToInt

data class HeartDetail(
    val entity: SessionHeartEntity,
    val setTimes: List<Instant>,
    val peaks: List<ExercisePeaks>,
    val suggestedHrMax: Int?,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    private val heartRepository: HeartRepository,
    private val heartSync: HeartSync,
    private val analysis: AiAnalysis,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<SessionDetailRoute>()
    val justFinished = route.justFinished
    private val _report = MutableStateFlow<SessionReport?>(null)
    val report = _report.asStateFlow()
    /** Maximum heart rate currently in the profile (for the "adjust it" suggestion). */
    private val currentHrMax = MutableStateFlow<Int?>(null)
    private val hrMaxDismissed = MutableStateFlow(false)

    val heart: StateFlow<HeartDetail?> = combine(
        heartRepository.observe(route.sessionId),
        sessions.observeSets(route.sessionId),
        _report,
        currentHrMax,
        hrMaxDismissed,
    ) { entity, sets, report, hrMax, dismissed ->
        entity ?: return@combine null
        val series = entity.series
        val working = sets.filterNot { it.isWarmup }.sortedBy { it.completedAt }
        val names = report?.exercises?.associate { it.exerciseId to it.name }.orEmpty()
        val peaks = working.groupBy { it.exerciseId }.mapNotNull { (exerciseId, exSets) ->
            val values = exSets.mapNotNull { SetHeart.peak(series, it.completedAt) }
            if (values.isEmpty()) null else ExercisePeaks(names[exerciseId] ?: "Exercice", values)
        }
        HeartDetail(
            entity = entity,
            setTimes = working.map { it.completedAt },
            peaks = peaks,
            suggestedHrMax = entity.maxBpm.takeIf { hrMax != null && it > hrMax && !dismissed },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch { _report.value = sessions.buildReport(route.sessionId) }
        viewModelScope.launch { currentHrMax.value = heartRepository.heartProfile().hrMax }
        // Foreground catch-up: the watch data may have reached Health Connect since the session ended.
        viewModelScope.launch { runCatching { heartSync.sync(route.sessionId) } }
    }

    /** [bpm] < 0 = dismissed for now. */
    fun acceptHrMax(bpm: Int) = viewModelScope.launch {
        if (bpm > 0) {
            heartRepository.setHrMaxOverride(bpm)
            currentHrMax.value = bpm
        } else {
            hrMaxDismissed.value = true
        }
    }

    fun text(): String? = _report.value?.let(SessionTextFormatter::format)

    /** Hands this session's day (session + nutrition) to the AI app (V2.4). */
    fun analyse() {
        _report.value?.let { analysis.launch(it.startedAt.toLocalDate()) }
    }

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
    val heart by viewModel.heart.collectAsStateWithLifecycle()
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
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    ),
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

            heart?.let { h ->
                SectionTitle("Fréquence cardiaque")
                SessionHeartSection(h.entity, h.setTimes, h.peaks, h.suggestedHrMax, viewModel::acceptHrMax)
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

            Button(onClick = viewModel::analyse, modifier = Modifier.fillMaxWidth().padding(top = 24.dp)) {
                Text("Analyse IA de cette journée")
            }
            OutlinedButton(
                onClick = { viewModel.text()?.let { copy(context, it) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
