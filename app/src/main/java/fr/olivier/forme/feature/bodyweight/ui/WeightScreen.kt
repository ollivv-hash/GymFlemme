package fr.olivier.forme.feature.bodyweight.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.bodyweight.data.BodyWeightEntity
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.bodyweight.domain.MovingAverage
import fr.olivier.forme.feature.bodyweight.domain.WeightPoint
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.ChartPoint
import fr.olivier.forme.ui.components.ChartSeries
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.LineChart
import fr.olivier.forme.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class WeightUi(
    val entries: List<BodyWeightEntity> = emptyList(),
    val average: List<WeightPoint> = emptyList(),
)

@HiltViewModel
class WeightViewModel @Inject constructor(private val repository: BodyWeightRepository, private val clock: Clock) : ViewModel() {
    val ui = repository.observeAll().map { entries ->
        WeightUi(entries, MovingAverage.sevenDays(entries.map { WeightPoint(it.date, it.weightKg) }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUi())

    fun today(): LocalDate = LocalDate.now(clock)
    fun save(date: LocalDate, kg: Double) = viewModelScope.launch { repository.save(date, kg) }
    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }
}

private val shortDate = DateTimeFormatter.ofPattern("dd/MM", Fmt.locale)
private val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Fmt.locale)

/** The "Pesée" button (tests: its label appears with the button's expansion). */
const val ADD_WEIGHT_TAG = "add_weight"

@Composable
fun WeightScreen(showTopBar: Boolean = true, viewModel: WeightViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    Scaffold(
        topBar = { if (showTopBar) FormeTopBar("Poids") },
        floatingActionButton = {
            ExtendedFloatingActionButton(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary, onClick = { adding = true }, modifier = Modifier.testTag(ADD_WEIGHT_TAG), icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Pesée") })
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            if (ui.entries.isNotEmpty()) {
                item {
                    LineChart(
                        series = listOf(
                            ChartSeries(
                                ui.entries.map { ChartPoint(it.date.toEpochDay().toDouble(), it.weightKg) },
                                MaterialTheme.colorScheme.outline,
                            ),
                            ChartSeries(
                                ui.average.map { ChartPoint(it.date.toEpochDay().toDouble(), it.weightKg) },
                                MaterialTheme.colorScheme.primary,
                                showDots = false,
                            ),
                        ),
                        xLabel = { LocalDate.ofEpochDay(it.toLong()).format(shortDate) },
                        yLabel = { Fmt.kg(it) },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("● pesées  ", color = MaterialTheme.colorScheme.outline)
                        Text("— moyenne mobile 7 jours", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            item { SectionTitle("Pesées") }
            if (ui.entries.isEmpty()) item { Text("Aucune pesée. Ajoute la première avec « Pesée ».") }
            val averages = ui.average.associateBy { it.date }
            items(ui.entries.reversed(), key = { it.id }) { entry ->
                ListItem(
                    headlineContent = { Text(Fmt.kg(entry.weightKg)) },
                    supportingContent = {
                        Text(
                            entry.date.format(longDate) +
                                (averages[entry.date]?.let { " · moy. 7 j ${Fmt.kg(it.weightKg)}" } ?: ""),
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.delete(entry.id) }) { Icon(Icons.Filled.Close, contentDescription = "Supprimer") }
                    },
                )
            }
        }
    }
    if (adding) {
        WeightEntryDialog(
            initialKg = ui.entries.lastOrNull()?.weightKg ?: 75.0,
            today = viewModel.today(),
            onSave = { date, kg ->
                viewModel.save(date, kg)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
}
