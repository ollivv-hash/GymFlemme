package fr.olivier.forme.feature.sport.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.bodyweight.domain.MovingAverage
import fr.olivier.forme.feature.bodyweight.domain.WeightPoint
import fr.olivier.forme.feature.bodyweight.ui.WeightEntryDialog
import fr.olivier.forme.feature.nutrition.ui.JournalViewModel
import fr.olivier.forme.feature.nutrition.ui.ProteinGauge
import androidx.compose.material3.Checkbox
import fr.olivier.forme.feature.sport.data.SessionEntity
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.TemplateRepository
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.domain.WeeklyStats
import fr.olivier.forme.core.settings.SettingsRepository
import kotlinx.coroutines.flow.combine
import fr.olivier.forme.feature.sport.ui.templates.title
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.theme.accentButtonColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class WeightSummary(val lastKg: Double, val lastDate: LocalDate, val average7Kg: Double)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val sessions: SessionRepository,
    templates: TemplateRepository,
    private val weights: BodyWeightRepository,
    settings: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private fun <T> kotlinx.coroutines.flow.Flow<T>.state(initial: T) =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val proposed = templates.observeProposed().state(null)
    val templates = templates.observeAll().map { list -> list.filterNot { it.archived } }.state(emptyList())
    val active = sessions.observeActive().state(null)
    val recent = sessions.observeRecent(5).state(emptyList())
    val weight = weights.observeAll().map { entries ->
        val last = entries.maxByOrNull { it.date } ?: return@map null
        val avg = MovingAverage.sevenDays(entries.map { WeightPoint(it.date, it.weightKg) }).last().weightKg
        WeightSummary(last.weightKg, last.date, avg)
    }.state(null)

    /** "2/3": finished sessions this week (Monday → Sunday) vs the weekly goal. */
    val weekProgress = combine(sessions.observeFinished(), settings.trainingGoals) { finished, goals ->
        val start = WeeklyStats.weekStart(LocalDate.now(clock))
        finished.count { WeeklyStats.weekStart(it.startedAt.atZone(clock.zone).toLocalDate()) == start } to goals.sessionsPerWeek
    }.state(null)

    fun today(): LocalDate = LocalDate.now(clock)
    val zone: ZoneId get() = clock.zone

    fun start(templateId: Long?, then: () -> Unit) = viewModelScope.launch {
        sessions.start(templateId)
        then()
    }

    fun saveWeight(date: LocalDate, kg: Double) = viewModelScope.launch { weights.save(date, kg) }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Fmt.locale)

fun SessionEntity.label() =
    listOfNotNull(templateCodeSnapshot, templateNameSnapshot).joinToString(" · ").ifEmpty { "Séance libre" }

fun SessionEntity.durationMin(): Long? = endedAt?.let { Duration.between(startedAt, it).toMinutes() }

@Composable
fun HomeScreen(
    onOpenSession: () -> Unit,
    onOpenSessionDetail: (Long) -> Unit,
    onOpenWeight: () -> Unit,
    onOpenNutrition: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    journal: JournalViewModel = hiltViewModel(),
) {
    val nutrition by journal.ui.collectAsStateWithLifecycle()
    val proposed by viewModel.proposed.collectAsStateWithLifecycle()
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val active by viewModel.active.collectAsStateWithLifecycle()
    val recent by viewModel.recent.collectAsStateWithLifecycle()
    val weight by viewModel.weight.collectAsStateWithLifecycle()
    val weekProgress by viewModel.weekProgress.collectAsStateWithLifecycle()
    var addingWeight by remember { mutableStateOf(false) }

    Scaffold(topBar = { FormeTopBar("Forme") }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            val running = active
            if (running != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Séance en cours", style = MaterialTheme.typography.labelLarge)
                        Text(running.label(), style = MaterialTheme.typography.headlineSmall)
                        Button(onClick = onOpenSession, colors = accentButtonColors(), modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) {
                            Text("Reprendre")
                        }
                    }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Séance proposée", style = MaterialTheme.typography.labelLarge)
                        Text(proposed?.title() ?: "Aucun modèle en rotation", style = MaterialTheme.typography.headlineSmall)
                        weekProgress?.let { (done, goal) ->
                            Text(
                                "Cette semaine : $done/$goal séances" + if (done >= goal) " ✓" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (done >= goal) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Button(
                            colors = accentButtonColors(),
                            onClick = { viewModel.start(proposed?.id, onOpenSession) },
                            enabled = proposed != null,
                            modifier = Modifier.fillMaxWidth().height(64.dp).padding(top = 12.dp),
                        ) { Text("Démarrer", style = MaterialTheme.typography.titleLarge) }
                    }
                }
                SectionTitle("Autres séances")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    templates.filter { it.id != proposed?.id }.forEach { template ->
                        FilledTonalButton(onClick = { viewModel.start(template.id, onOpenSession) }) { Text(template.title()) }
                    }
                    OutlinedButton(onClick = { viewModel.start(null, onOpenSession) }) { Text("Séance libre") }
                }
            }

            SectionTitle("Nutrition du jour")
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenNutrition)) {
                Column(Modifier.padding(16.dp)) {
                    ProteinGauge(nutrition.totals, nutrition.proteinTargetG)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = nutrition.creatineTaken, onCheckedChange = journal::setCreatine)
                        Text("Créatine ${Fmt.number(nutrition.profile.creatineDoseG)} g prise")
                    }
                }
            }

            SectionTitle("Poids")
            Card(Modifier.fillMaxWidth().clickable(onClick = onOpenWeight)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        val w = weight
                        if (w == null) {
                            Text("Aucune pesée")
                        } else {
                            Text(Fmt.kg(w.lastKg), style = MaterialTheme.typography.headlineSmall)
                            Text("le ${w.lastDate.format(dayFormat)} · moyenne 7 j : ${Fmt.kg(w.average7Kg)}")
                        }
                    }
                    FilledTonalButton(onClick = { addingWeight = true }) { Text("+ pesée") }
                }
            }

            SectionTitle("Dernières séances")
            if (recent.isEmpty()) Text("Aucune séance pour l'instant.", style = MaterialTheme.typography.bodyMedium)
            recent.forEach { session ->
                ListItem(
                    modifier = Modifier.clickable { onOpenSessionDetail(session.id) },
                    headlineContent = { Text(session.label()) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                session.startedAt.atZone(viewModel.zone).format(dayFormat),
                                session.durationMin()?.let { "$it min" },
                            ).joinToString(" · "),
                        )
                    },
                )
            }
        }
    }

    if (addingWeight) {
        WeightEntryDialog(
            initialKg = weight?.lastKg ?: 75.0,
            today = viewModel.today(),
            onSave = { date, kg ->
                viewModel.saveWeight(date, kg)
                addingWeight = false
            },
            onDismiss = { addingWeight = false },
        )
    }
}
