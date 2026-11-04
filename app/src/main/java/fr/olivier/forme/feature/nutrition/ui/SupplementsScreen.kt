package fr.olivier.forme.feature.nutrition.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.SupplementEntity
import fr.olivier.forme.feature.nutrition.data.SupplementUnit
import fr.olivier.forme.feature.nutrition.data.dose
import fr.olivier.forme.feature.nutrition.reminder.SupplementReminder
import fr.olivier.forme.ui.components.ConfirmDialog
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.Stepper
import fr.olivier.forme.ui.components.parseDecimal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SupplementsViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val reminder: SupplementReminder,
) : ViewModel() {
    val supplements = repository.supplements.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Saved at once; the reminders follow. */
    fun update(supplement: SupplementEntity) = viewModelScope.launch {
        repository.updateSupplement(supplement)
        if (!supplement.active || !supplement.reminderEnabled) reminder.dismissNotification(supplement.id)
        reminder.reschedule()
    }

    fun add(name: String, amount: Double, unit: SupplementUnit) = viewModelScope.launch {
        repository.addSupplement(name, amount, unit)
    }

    fun delete(supplement: SupplementEntity) = viewModelScope.launch {
        reminder.cancel(supplement.id)
        repository.deleteSupplement(supplement.id)
    }
}

const val NEW_SUPPLEMENT_TAG = "new_supplement"

/** Step of the amount: 0.5 g, 50 mg, 5 µg, 100 UI, 1 gélule… */
private fun SupplementUnit.step() = when (this) {
    SupplementUnit.G -> 0.5
    SupplementUnit.MG -> 50.0
    SupplementUnit.UG -> 5.0
    SupplementUnit.IU -> 100.0
    SupplementUnit.CAPSULE, SupplementUnit.TABLET -> 1.0
    SupplementUnit.ML -> 5.0
}

/**
 * Config → Compléments (V2.20): each supplement switched on or off, its amount per day, its unit and its reminder.
 * The active ones get a box in the Nutrition journal and on the home screen.
 */
@Composable
fun SupplementsScreen(onBack: () -> Unit, viewModel: SupplementsViewModel = hiltViewModel()) {
    val supplements by viewModel.supplements.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<SupplementEntity?>(null) }
    var pickingTime by remember { mutableStateOf<SupplementEntity?>(null) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    Scaffold(
        topBar = { FormeTopBar("Compléments", onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                onClick = { adding = true },
                modifier = Modifier.testTag(NEW_SUPPLEMENT_TAG),
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Complément") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            item {
                Text(
                    "Les compléments actifs ont leur case dans Nutrition et sur l'accueil.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(supplements, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.name, style = MaterialTheme.typography.titleMedium)
                                Text(s.dose() + " par jour", style = MaterialTheme.typography.bodySmall)
                            }
                            if (s.code == null) {
                                IconButton(onClick = { deleting = s }) { Icon(Icons.Filled.Delete, "Supprimer ${s.name}") }
                            }
                            Switch(
                                checked = s.active,
                                onCheckedChange = { viewModel.update(s.copy(active = it)) },
                                modifier = Modifier.semantics { contentDescription = "${s.name} actif" },
                            )
                        }
                        if (s.active) {
                            Stepper(
                                label = "Quantité par jour",
                                valueText = s.dose(),
                                onMinus = { viewModel.update(s.copy(amount = (s.amount - s.unit.step()).coerceAtLeast(s.unit.step()))) },
                                onPlus = { viewModel.update(s.copy(amount = s.amount + s.unit.step())) },
                                onValueEntered = { t -> parseDecimal(t)?.takeIf { it > 0 }?.let { viewModel.update(s.copy(amount = it)) } },
                                decimal = true,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            UnitChips(s.unit) { viewModel.update(s.copy(unit = it)) }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text("Rappel quotidien", Modifier.weight(1f))
                                Switch(
                                    checked = s.reminderEnabled,
                                    onCheckedChange = { on ->
                                        viewModel.update(s.copy(reminderEnabled = on))
                                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                )
                            }
                            if (s.reminderEnabled) {
                                OutlinedButton(onClick = { pickingTime = s }) {
                                    Text("Heure du rappel : %02d:%02d".format(s.reminderMinutes / 60, s.reminderMinutes % 60))
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Pas de rappel si la prise du jour est déjà notée.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 88.dp),
                )
            }
        }
    }

    if (adding) {
        AddSupplementDialog(
            onAdd = { name, amount, unit ->
                viewModel.add(name, amount, unit)
                adding = false
            },
            onDismiss = { adding = false },
        )
    }
    deleting?.let { s ->
        ConfirmDialog(
            title = "Supprimer « ${s.name} » ?",
            text = "Les jours où il a été pris sont effacés avec lui.",
            confirmLabel = "Supprimer",
            onConfirm = {
                viewModel.delete(s)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
    pickingTime?.let { s -> ReminderTimeDialog(s, onPicked = { viewModel.update(s.copy(reminderMinutes = it)) }, onDismiss = { pickingTime = null }) }
}

/** Units in one scrolling row: the card stays short. */
@Composable
private fun UnitChips(selected: SupplementUnit, onSelect: (SupplementUnit) -> Unit) {
    Row(Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState())) {
        SupplementUnit.entries.forEach { unit ->
            FilterChip(
                selected = unit == selected,
                onClick = { onSelect(unit) },
                label = { Text(unit.label) },
                modifier = Modifier.padding(end = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(supplement: SupplementEntity, onPicked: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(supplement.reminderMinutes / 60, supplement.reminderMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(supplement.name) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = {
                onPicked(state.hour * 60 + state.minute)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
internal fun AddSupplementDialog(onAdd: (String, Double, SupplementUnit) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(SupplementUnit.MG) }
    val parsed = parseDecimal(amount)?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouveau complément") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Quantité par jour") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                UnitChips(unit) { unit = it }
                parsed?.let { Text(SupplementEntity(name = name, amount = it, unit = unit, active = true, sortOrder = 0).dose() + " par jour", style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && parsed != null, onClick = { onAdd(name, parsed!!, unit) }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
