package fr.olivier.forme.feature.nutrition.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.olivier.forme.feature.bodyweight.ui.WeightScreen
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.Per100
import fr.olivier.forme.feature.nutrition.domain.WheyMoment
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.FormeTopBar
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM", Fmt.locale)
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Fmt.locale)

/** Nutrition tab: daily journal and body weight. */
@Composable
fun NutritionScreen(
    initialTab: Int,
    onAddFood: (LocalDate, MealType) -> Unit,
    onScan: (LocalDate, MealType) -> Unit,
    onProfile: () -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    Scaffold(topBar = { FormeTopBar("Nutrition") { TextButton(onClick = onProfile) { Text("Profil") } } }) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Journal") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Poids") })
            }
            if (tab == 0) JournalContent(onAddFood, onScan, onProfile) else WeightScreen(showTopBar = false)
        }
    }
}

@Composable
private fun JournalContent(
    onAddFood: (LocalDate, MealType) -> Unit,
    onScan: (LocalDate, MealType) -> Unit,
    onProfile: () -> Unit,
    viewModel: JournalViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<MealEntryEntity?>(null) }

    LazyColumn(Modifier.padding(horizontal = 12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { viewModel.shiftDay(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Jour précédent") }
                Text(
                    if (ui.isToday) "Aujourd'hui" else ui.date.format(dayFormat),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.shiftDay(1) }, enabled = !ui.isToday) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Jour suivant")
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    ProteinGauge(ui.totals, ui.proteinTargetG)
                    Spacer(Modifier.padding(4.dp))
                    MacroSummary(ui.totals)
                }
            }
        }
        item { WheyCard(ui, onAdd = viewModel::addWhey, onProfile = onProfile) }
        item {
            Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Row(Modifier.clickable { viewModel.setCreatine(!ui.creatineTaken) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = ui.creatineTaken, onCheckedChange = viewModel::setCreatine)
                    Column(Modifier.weight(1f)) {
                        Text("Créatine ${Fmt.number(ui.profile.creatineDoseG)} g", style = MaterialTheme.typography.titleMedium)
                        if (ui.creatineStreak > 0) Text("${ui.creatineStreak} jour(s) d'affilée", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        MealType.entries.forEach { type ->
            item(key = "h-${type.name}") {
                MealHeader(
                    type = type,
                    entries = ui.entriesOf(type),
                    onAdd = { onAddFood(ui.date, type) },
                    onScan = { onScan(ui.date, type) },
                )
            }
            items(ui.entriesOf(type), key = { it.id }) { entry ->
                ListItem(
                    modifier = Modifier.clickable { editing = entry },
                    headlineContent = { Text(entry.name) },
                    supportingContent = {
                        Text(
                            listOfNotNull(
                                entry.eatenAt.atZone(ZoneId.systemDefault()).format(timeFormat).takeIf { type == MealType.EXTRA },
                                "${Fmt.number(entry.quantityG, 0)} g",
                                macrosLine(entry.proteinG, entry.kcal, entry.carbsG, entry.fatG),
                            ).joinToString(" · "),
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { viewModel.delete(entry) }) { Icon(Icons.Filled.Delete, "Supprimer") }
                    },
                )
            }
        }
        item { Spacer(Modifier.padding(24.dp)) }
    }

    editing?.let { entry ->
        val q = entry.quantityG.takeIf { it > 0 } ?: 100.0
        QuantityDialog(
            title = entry.name,
            per100 = Per100(entry.proteinG * 100 / q, entry.kcal?.times(100 / q), entry.carbsG?.times(100 / q), entry.fatG?.times(100 / q)),
            initialG = entry.quantityG,
            portions = listOf(50.0, 100.0, 150.0, 200.0),
            confirmLabel = "Modifier",
            onConfirm = {
                viewModel.updateQuantity(entry, it)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun MealHeader(type: MealType, entries: List<MealEntryEntity>, onAdd: () -> Unit, onScan: () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(type.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                if (entries.isNotEmpty()) {
                    Text(
                        "${entries.sumOf { it.proteinG }.roundToInt()} g prot. · ${entries.sumOf { it.kcal ?: 0.0 }.roundToInt()} kcal",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            TextButton(onClick = onScan) { Text("Scanner") }
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Ajouter à ${type.label}") }
        }
        HorizontalDivider()
    }
}

@Composable
private fun WheyCard(ui: JournalUi, onAdd: () -> Unit, onProfile: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            if (!ui.wheyConfigured) {
                Text("Whey", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onProfile) { Text("Renseigner ma whey dans le profil") }
                return@Column
            }
            val p = ui.profile
            val proteinPerDose = p.wheyProteinPer100g!! * p.wheyScoopG / 100
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Whey · ${Fmt.number(p.wheyScoopG, 0)} g (${Fmt.number(proteinPerDose)} g prot.)",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(ui.wheyMoment.label, style = MaterialTheme.typography.bodySmall)
                }
                if (ui.wheyMoment != WheyMoment.DONE) Button(onClick = onAdd) { Text("Ajouter") } else Text("✓", style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}
