package fr.olivier.forme.feature.sport.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.sport.data.CardioEntryEntity
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.ExerciseRepository
import fr.olivier.forme.feature.sport.data.ExerciseSetRow
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.toSessionSets
import fr.olivier.forme.feature.sport.data.toWorkSet
import fr.olivier.forme.feature.sport.domain.ExerciseRecords
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.domain.OneRepMax
import fr.olivier.forme.feature.sport.domain.Plateau
import fr.olivier.forme.feature.sport.domain.PlateauDetector
import fr.olivier.forme.feature.sport.domain.RecordsCalculator
import fr.olivier.forme.feature.sport.ui.session.PlateauCard
import fr.olivier.forme.feature.sport.domain.SessionTextFormatter
import fr.olivier.forme.ui.components.ChartPoint
import fr.olivier.forme.ui.components.ChartSeries
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.LineChart
import fr.olivier.forme.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SessionGroup(val startedAt: Instant, val rows: List<ExerciseSetRow>)

data class ExerciseHistoryUi(
    val exercise: ExerciseEntity? = null,
    /** Real sessions only (seed hidden), most recent first. */
    val sessions: List<SessionGroup> = emptyList(),
    /** Seed included: it is the starting reference. */
    val records: ExerciseRecords = ExerciseRecords(null, null),
    val cardio: List<CardioEntryEntity> = emptyList(),
    /** Loaded exercises: best estimated 1RM (seed included) and current plateau, if any. */
    val bestOneRepMaxKg: Double? = null,
    val plateau: Plateau? = null,
)

@HiltViewModel
class ExerciseHistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exercises: ExerciseRepository,
    sessions: SessionRepository,
    clock: Clock,
) : ViewModel() {
    private val exerciseId = savedStateHandle.toRoute<fr.olivier.forme.ui.navigation.ExerciseHistoryRoute>().exerciseId
    val zone = clock.zone

    val ui = combine(
        exercises.observe(exerciseId),
        sessions.observeExerciseHistory(exerciseId),
        sessions.observeCardioHistory(exerciseId),
    ) { exercise, rows, cardio ->
        val history = rows.toSessionSets()
        val weighted = exercise?.type == ExerciseType.WEIGHTED
        ExerciseHistoryUi(
            exercise = exercise,
            sessions = rows.filterNot { it.isSeed }.groupBy { it.sessionId }
                .map { (_, r) -> SessionGroup(r.first().startedAt, r) }
                .sortedByDescending { it.startedAt },
            records = RecordsCalculator.records(history),
            cardio = cardio.reversed(),
            bestOneRepMaxKg = if (weighted) history.mapNotNull { OneRepMax.best(it.sets) }.maxOrNull() else null,
            plateau = if (weighted) PlateauDetector.detect(history, exercise!!.loadIncrementKg) else null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseHistoryUi())
}

private val shortDate = DateTimeFormatter.ofPattern("dd/MM", Fmt.locale)
private val longDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Fmt.locale)

@Composable
fun ExerciseHistoryScreen(onBack: () -> Unit, onSheet: (Long) -> Unit = {}, viewModel: ExerciseHistoryViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val exercise = ui.exercise
    Scaffold(
        topBar = {
            FormeTopBar(exercise?.name ?: "Exercice", onBack) {
                exercise?.let { e -> IconButton(onClick = { onSheet(e.id) }) { Icon(Icons.Filled.Info, contentDescription = "Fiche de l'exercice") } }
            }
        },
    ) { padding ->
        if (exercise == null) return@Scaffold
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            if (exercise.type == ExerciseType.CARDIO) {
                item { SectionTitle("Séances") }
                items(ui.cardio, key = { it.id }) { c ->
                    ListItem(
                        headlineContent = {
                            Text(
                                listOfNotNull(
                                    Fmt.mmss(c.durationSec),
                                    c.distanceM?.let { "$it m" },
                                    c.pacePer500mSec?.let { "${Fmt.mmss(it)} /500 m" },
                                    c.caloriesDisplayed?.let { "$it kcal" },
                                ).joinToString(" · "),
                            )
                        },
                    )
                }
                return@LazyColumn
            }

            exercise.nextNote?.let { note ->
                item {
                    Column {
                        SectionTitle("Note pour la prochaine fois")
                        Text("📝 $note")
                    }
                }
            }
            item {
                Column {
                    SectionTitle("Records")
                    Text("Meilleure charge : ${ui.records.bestLoadKg?.let(Fmt::kg) ?: "—"}")
                    Text("Meilleur volume (séance) : ${ui.records.bestVolumeKg?.let(Fmt::kg) ?: "—"}")
                    ui.bestOneRepMaxKg?.let { Text("1RM estimé (Epley, ≤ ${OneRepMax.MAX_REPS} reps) : ${Fmt.kg(Math.round(it * 2) / 2.0)}") }
                }
            }
            ui.plateau?.let { item { PlateauCard(it, Modifier.padding(top = 8.dp)) } }

            val chartPoints = ui.sessions.mapNotNull { group ->
                val value = when (exercise.type) {
                    ExerciseType.WEIGHTED -> group.rows.mapNotNull { it.loadKg }.maxOrNull()
                    ExerciseType.BODYWEIGHT -> group.rows.mapNotNull { it.reps }.maxOrNull()?.toDouble()
                    else -> group.rows.mapNotNull { it.durationSec }.maxOrNull()?.toDouble()
                } ?: return@mapNotNull null
                ChartPoint(group.startedAt.atZone(viewModel.zone).toLocalDate().toEpochDay().toDouble(), value)
            }
            val oneRepMaxPoints = if (exercise.type == ExerciseType.WEIGHTED) {
                ui.sessions.mapNotNull { group ->
                    val best = OneRepMax.best(group.rows.map { it.toWorkSet() }) ?: return@mapNotNull null
                    ChartPoint(group.startedAt.atZone(viewModel.zone).toLocalDate().toEpochDay().toDouble(), best)
                }
            } else {
                emptyList()
            }
            if (chartPoints.isNotEmpty()) {
                item {
                    Column {
                        SectionTitle(
                            when (exercise.type) {
                                ExerciseType.WEIGHTED -> "Charge max par séance"
                                ExerciseType.BODYWEIGHT -> "Reps max par séance"
                                else -> "Durée max par séance"
                            },
                        )
                        LineChart(
                            series = listOfNotNull(
                                ChartSeries(chartPoints, MaterialTheme.colorScheme.primary),
                                oneRepMaxPoints.takeIf { it.isNotEmpty() }?.let { ChartSeries(it, MaterialTheme.colorScheme.tertiary) },
                            ),
                            xLabel = { LocalDate.ofEpochDay(it.toLong()).format(shortDate) },
                            yLabel = { if (exercise.type == ExerciseType.WEIGHTED) Fmt.kg(it) else Fmt.number(it, 0) },
                        )
                        if (oneRepMaxPoints.isNotEmpty()) {
                            Row {
                                Text("— charge max", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                Text("   — 1RM estimé", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Séries") }
            if (ui.sessions.isEmpty()) item { Text("Pas encore de séance réalisée.") }
            items(ui.sessions, key = { it.startedAt.toEpochMilli() }) { group ->
                ListItem(
                    headlineContent = { Text(group.startedAt.atZone(viewModel.zone).format(longDate)) },
                    supportingContent = {
                        Text(
                            group.rows.joinToString(" · ") {
                                SessionTextFormatter.setLabel(exercise.type, exercise.perSide, it.toWorkSet())
                            },
                        )
                    },
                )
            }
        }
    }
}
