package fr.olivier.forme.feature.hydration.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.olivier.forme.R
import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.core.humor.rememberQuip
import fr.olivier.forme.feature.hydration.data.IntakeSource
import fr.olivier.forme.feature.hydration.data.IntakeWithDrink
import fr.olivier.forme.feature.hydration.data.WaterToday
import fr.olivier.forme.feature.hydration.domain.HydrationMath
import fr.olivier.forme.ui.components.Bar
import fr.olivier.forme.ui.components.BarChart
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.theme.LocalBento
import java.time.format.TextStyle
import java.util.Locale

const val WATER_HISTORY_TAG = "water_history_"

/**
 * Water screen (V2.29): today's progress, the choice of a drink and a quantity, today's history (tap to edit,
 * bin to delete), the last 7 days.
 */
@Composable
fun WaterScreen(onBack: () -> Unit, onNeedsFood: (drinkId: Long) -> Unit = {}, viewModel: WaterViewModel = hiltViewModel()) {
    val today by viewModel.today.collectAsStateWithLifecycle()
    val intakes by viewModel.intakes.collectAsStateWithLifecycle()
    val drinks by viewModel.drinks.collectAsStateWithLifecycle()
    val allDrinks by viewModel.allDrinks.collectAsStateWithLifecycle()
    val containers by viewModel.containers.collectAsStateWithLifecycle()
    val week by viewModel.week.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by rememberSaveable { mutableStateOf<Long?>(null) }
    WaterSnackbarEffect(viewModel.events, snackbar, viewModel::undo, onNeedsFood, viewModel::resumePending)

    Scaffold(topBar = { FormeTopBar(stringResource(R.string.water_title), onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { today?.let { ProgressCard(it) } }
            item {
                AddDrinkPanel(drinks, containers, onAdd = { id, ml -> viewModel.add(id, ml, IntakeSource.WATER_SCREEN) })
            }
            item { SectionTitle(stringResource(R.string.water_today)) }
            if (intakes.isEmpty()) {
                item { rememberQuip(Quip.WATER_EMPTY)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
            items(intakes, key = { it.intake.id }) { row ->
                HistoryRow(row, viewModel, onEdit = { editing = row.intake.id })
            }
            item {
                SectionTitle(stringResource(R.string.water_week))
                WeekChart(week)
            }
            item { Text(stringResource(R.string.water_indicative), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp)) }
        }
    }

    editing?.let { id ->
        val row = intakes.firstOrNull { it.intake.id == id }
        if (row == null) {
            editing = null
        } else {
            EditIntakeDialog(
                intake = row.intake,
                drinks = allDrinks,
                zone = viewModel.zone,
                onSave = { drinkId, ml, at -> viewModel.update(id, drinkId, ml, at); editing = null },
                onDelete = { viewModel.delete(id); editing = null },
                onDismiss = { editing = null },
            )
        }
    }
}

@Composable
private fun ProgressCard(today: WaterToday) {
    val c = LocalBento.current
    val goal = today.goal
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            WaterRing(today.countedMl, goal.totalMl, color = c.accent, track = MaterialTheme.colorScheme.outlineVariant)
            Text(
                stringResource(R.string.water_goal_computed_used, HydrationMath.liters(goal.computedMl), HydrationMath.liters(goal.baseMl)),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (goal.bonusMl > 0) {
                rememberQuip(Quip.WATER_BONUS, goal.bonusMl)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
            }
            if (!goal.hasWeight && goal.customMl == null) {
                Text(stringResource(R.string.water_no_weight), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun HistoryRow(row: IntakeWithDrink, viewModel: WaterViewModel, onEdit: () -> Unit) {
    val time = row.intake.timestamp.localTime(viewModel.zone)
    val spoken = stringResource(R.string.water_history_row_spoken, time, row.drink.name, row.intake.volumeMl)
    ListItem(
        headlineContent = { Text(row.drink.name) },
        supportingContent = {
            val counted = HydrationMath.counted(row.intake.volumeMl, row.intake.coefficientApplied).toInt()
            Text(if (counted == row.intake.volumeMl) time else time + " · " + stringResource(R.string.water_history_counted, counted))
        },
        leadingContent = { Icon(painterResource(DrinkIcons.of(row.drink.icon)), contentDescription = null, modifier = Modifier.size(24.dp)) },
        trailingContent = {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${row.intake.volumeMl} ml", style = MaterialTheme.typography.bodyLarge)
                DeleteButton(stringResource(R.string.water_delete_spoken, row.drink.name, time)) { viewModel.delete(row.intake.id) }
            }
        },
        modifier = Modifier
            .clickable(onClickLabel = stringResource(R.string.water_edit_title), onClick = onEdit)
            .semantics { contentDescription = spoken }
            .testTag(WATER_HISTORY_TAG + row.intake.id),
    )
}

@Composable
private fun WeekChart(week: List<WaterBar>) {
    val c = LocalBento.current
    val bars = week.map { bar ->
        Bar(bar.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.FRENCH).uppercase(Locale.FRENCH), bar.countedMl.toDouble(), bar.goalMl?.toDouble())
    }
    val res = androidx.compose.ui.platform.LocalResources.current
    val description = week.joinToString(". ") { bar ->
        res.getString(
            R.string.water_week_spoken,
            bar.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.FRENCH),
            HydrationMath.litersSpoken(bar.countedMl),
            bar.goalMl?.let(HydrationMath::litersSpoken) ?: "—",
        )
    }
    BarChart(bars, color = c.accent, description = description, yLabel = { HydrationMath.liters(it.toInt()) }, modifier = Modifier.padding(top = 8.dp))
}

const val WATER_JOURNAL_TAG = "water_journal_entry"

/** "Eau" entry at the head of the Nutrition journal (V2.29): today's water, opens the water screen. */
@Composable
fun WaterJournalEntry(onOpen: () -> Unit, modifier: Modifier = Modifier, viewModel: WaterViewModel = hiltViewModel()) {
    val today by viewModel.today.collectAsStateWithLifecycle()
    // Always laid out (an empty first item would leave the journal scrolled past it once it fills).
    val counted = today?.countedMl ?: 0
    val goal = today?.goal?.totalMl ?: 0
    val spoken = stringResource(R.string.water_journal_entry_spoken, HydrationMath.litersSpoken(counted), HydrationMath.litersNumber(goal))
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = spoken }.testTag(WATER_JOURNAL_TAG),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.water_journal_entry)) },
            supportingContent = {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { if (goal > 0) (counted.toFloat() / goal).coerceIn(0f, 1f) else 0f },
                    color = LocalBento.current.accent,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            },
            leadingContent = { Icon(painterResource(R.drawable.ic_drink_water_full), contentDescription = null) },
            trailingContent = {
                Text(
                    if (today == null) "—" else stringResource(R.string.water_progress, HydrationMath.litersNumber(counted), HydrationMath.liters(goal)),
                    style = MaterialTheme.typography.titleMedium,
                )
            },
        )
    }
}
