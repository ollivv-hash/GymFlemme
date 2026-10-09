package fr.olivier.forme.feature.sport.ui.history

import fr.olivier.forme.feature.badges.ui.BadgesContent
import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.core.humor.QuipText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.sport.data.ExerciseRepository
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.ui.home.durationMin
import fr.olivier.forme.feature.sport.ui.home.label
import fr.olivier.forme.ui.components.FormeTopBar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    sessions: SessionRepository,
    exercises: ExerciseRepository,
    clock: Clock,
) : ViewModel() {
    val zone = clock.zone
    val sessions = sessions.observeFinished().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val exercises = exercises.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Fmt.locale)

@Composable
fun HistoryScreen(onOpenSession: (Long) -> Unit, onOpenExercise: (Long) -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(topBar = { FormeTopBar("Historique") }) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Séances") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Exercices") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Semaine") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Badges") })
            }
            if (tab == 2) {
                WeekContent()
                return@Column
            }
            if (tab == 3) {
                BadgesContent()
                return@Column
            }
            LazyColumn {
                if (tab == 0) {
                    if (sessions.isEmpty()) {
                        item { QuipText(Quip.EMPTY_SESSIONS, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
                    }
                    items(sessions, key = { it.id }) { s ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenSession(s.id) },
                            headlineContent = { Text(s.label()) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        s.startedAt.atZone(viewModel.zone).format(dateFormat),
                                        s.durationMin()?.let { "$it min" },
                                        s.feeling?.let { "ressenti $it/5" },
                                    ).joinToString(" · "),
                                )
                            },
                        )
                    }
                } else {
                    items(exercises, key = { it.id }) { e ->
                        ListItem(
                            modifier = Modifier.clickable { onOpenExercise(e.id) },
                            headlineContent = { Text(e.name + if (e.archived) " (archivé)" else "") },
                            supportingContent = { Text(e.muscleGroup.label) },
                        )
                    }
                }
            }
        }
    }
}
