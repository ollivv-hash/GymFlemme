package fr.olivier.forme.feature.hydration.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.olivier.forme.R
import fr.olivier.forme.feature.hydration.data.ContainerEntity
import fr.olivier.forme.feature.hydration.data.DrinkEntity
import fr.olivier.forme.feature.hydration.data.ReminderSettings
import fr.olivier.forme.feature.hydration.domain.HydrationMath
import fr.olivier.forme.feature.hydration.health.HydrationHealthMirror
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val HYDRATION_CUSTOM_GOAL_TAG = "hydration_custom_goal"
const val HYDRATION_BONUS_TAG = "hydration_bonus"
const val HYDRATION_REMINDERS_TAG = "hydration_reminders"
const val HYDRATION_HEALTH_TAG = "hydration_health"
const val HYDRATION_ADD_CONTAINER_TAG = "hydration_add_container"
const val HYDRATION_ADD_DRINK_TAG = "hydration_add_drink"

/**
 * Réglages › Hydratation (V2.29): goal (computed, one's own), session bonus, containers, drinks, reminders, Health
 * Connect, and the "indicative values" mention.
 */
@Composable
fun HydrationSettingsScreen(
    onBack: () -> Unit,
    onChooseProduct: (drinkId: Long) -> Unit,
    viewModel: HydrationSettingsViewModel = hiltViewModel(),
) {
    val current by viewModel.current.collectAsStateWithLifecycle()
    val goal by viewModel.goal.collectAsStateWithLifecycle()
    val drinks by viewModel.drinks.collectAsStateWithLifecycle()
    val containers by viewModel.containers.collectAsStateWithLifecycle()
    val foods by viewModel.foodNames.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var editingContainer by remember { mutableStateOf<ContainerEntity?>(null) }
    var editingDrink by remember { mutableStateOf<DrinkEntity?>(null) }
    val healthPermission = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (HydrationHealthMirror.WRITE_PERMISSION in granted) {
            viewModel.setHealthConnect(true)
        } else {
            scope.launch { snackbar.showSnackbar(resources.getString(R.string.hydration_health_denied)) }
        }
    }

    Scaffold(
        topBar = { FormeTopBar(stringResource(R.string.hydration_settings_title), onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val s = current ?: return@Scaffold
        LazyColumn(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            // --- Goal ---
            item {
                SectionTitle(stringResource(R.string.hydration_goal_section))
                goal?.let { g ->
                    Text(stringResource(R.string.hydration_goal_computed, HydrationMath.liters(g.computedMl)))
                    if (!g.hasWeight) Text(stringResource(R.string.water_no_weight), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SwitchRow(stringResource(R.string.hydration_goal_custom), s.goal.customEnabled, Modifier.testTag(HYDRATION_CUSTOM_GOAL_TAG)) {
                    viewModel.setGoal(s.goal.copy(customEnabled = it, customMl = if (it && !s.goal.customEnabled) goal?.computedMl ?: s.goal.customMl else s.goal.customMl))
                }
                if (s.goal.customEnabled) {
                    Stepper(
                        HydrationMath.liters(s.goal.customMl),
                        onMinus = { viewModel.setGoal(s.goal.copy(customMl = s.goal.customMl - HydrationMath.CUSTOM_STEP_ML)) },
                        onPlus = { viewModel.setGoal(s.goal.copy(customMl = s.goal.customMl + HydrationMath.CUSTOM_STEP_ML)) },
                    )
                }
            }
            // --- Session bonus ---
            item {
                SectionTitle(stringResource(R.string.hydration_bonus_section))
                SwitchRow(stringResource(R.string.hydration_bonus_switch), s.goal.bonusEnabled, Modifier.testTag(HYDRATION_BONUS_TAG)) {
                    viewModel.setGoal(s.goal.copy(bonusEnabled = it))
                }
                if (s.goal.bonusEnabled) {
                    Stepper(
                        "+${s.goal.bonusMl} ml",
                        onMinus = { viewModel.setGoal(s.goal.copy(bonusMl = s.goal.bonusMl - HydrationMath.BONUS_STEP_ML)) },
                        onPlus = { viewModel.setGoal(s.goal.copy(bonusMl = s.goal.bonusMl + HydrationMath.BONUS_STEP_ML)) },
                    )
                }
            }
            // --- Containers ---
            item { SectionTitle(stringResource(R.string.hydration_containers_section)) }
            items(containers, key = { "c${it.id}" }) { container ->
                val drink = drinks.firstOrNull { it.id == container.defaultDrinkId }?.name.orEmpty()
                val status = listOfNotNull(
                    stringResource(R.string.hydration_container_line, container.volumeMl, drink),
                    stringResource(R.string.hydration_default).takeIf { container.isDefault && !container.archived },
                    stringResource(R.string.hydration_archived).takeIf { container.archived },
                ).joinToString(" · ")
                OrderedRow(container.name, status, null, onClick = { editingContainer = container }, onUp = { viewModel.moveContainer(container, -1) }, onDown = { viewModel.moveContainer(container, 1) })
            }
            item {
                TextButton(onClick = { editingContainer = ContainerEntity(name = "", volumeMl = 330, defaultDrinkId = drinks.firstOrNull { it.isWater }?.id ?: drinks.first().id, sortOrder = 0) }, modifier = Modifier.testTag(HYDRATION_ADD_CONTAINER_TAG)) {
                    Text(stringResource(R.string.hydration_add_container))
                }
            }
            // --- Drinks ---
            item { SectionTitle(stringResource(R.string.hydration_drinks_section)) }
            items(drinks, key = { "d${it.id}" }) { drink ->
                val percent = (drink.hydrationCoefficient * 100).roundToInt()
                val status = listOfNotNull(
                    if (drink.isCaloric) {
                        stringResource(R.string.hydration_drink_line_caloric, percent, drink.linkedFoodId?.let { foods[it] } ?: stringResource(R.string.hydration_drink_no_product))
                    } else {
                        stringResource(R.string.hydration_drink_line, percent)
                    },
                    stringResource(R.string.hydration_archived).takeIf { drink.archived },
                ).joinToString(" · ")
                OrderedRow(drink.name, status, DrinkIcons.of(drink.icon), onClick = { editingDrink = drink }, onUp = { viewModel.moveDrink(drink, -1) }, onDown = { viewModel.moveDrink(drink, 1) })
            }
            item {
                TextButton(onClick = { editingDrink = DrinkEntity(name = "", icon = "local_drink", hydrationCoefficient = 1.0, isCaloric = false, sortOrder = 0) }, modifier = Modifier.testTag(HYDRATION_ADD_DRINK_TAG)) {
                    Text(stringResource(R.string.hydration_add_drink))
                }
            }
            // --- Reminders ---
            item {
                SectionTitle(stringResource(R.string.hydration_reminders_section))
                RemindersSection(s.reminders, containers.filterNot { it.archived }, onChange = viewModel::setReminders)
            }
            // --- Health Connect ---
            item {
                SectionTitle(stringResource(R.string.hydration_health_section))
                SwitchRow(stringResource(R.string.hydration_health_switch), s.healthConnectWrite, Modifier.testTag(HYDRATION_HEALTH_TAG), stringResource(R.string.hydration_health_hint)) { on ->
                    when {
                        !on -> viewModel.setHealthConnect(false)
                        !viewModel.healthConnectAvailable() -> scope.launch { snackbar.showSnackbar(resources.getString(R.string.hydration_health_unavailable)) }
                        else -> scope.launch {
                            if (viewModel.healthConnectGranted()) viewModel.setHealthConnect(true) else healthPermission.launch(setOf(HydrationHealthMirror.WRITE_PERMISSION))
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.water_indicative),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }

    editingContainer?.let { container ->
        ContainerDialog(container, drinks.filter { !it.archived || it.id == container.defaultDrinkId }, onSave = { viewModel.saveContainer(it); editingContainer = null }, onDismiss = { editingContainer = null })
    }
    editingDrink?.let { drink ->
        DrinkDialog(
            drink = drink,
            productName = drink.linkedFoodId?.let { foods[it] },
            onSave = { viewModel.saveDrink(it); editingDrink = null },
            onChooseProduct = { editingDrink = null; onChooseProduct(drink.id) },
            onUnlink = { viewModel.unlinkFood(drink.id); editingDrink = null },
            onDismiss = { editingDrink = null },
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, modifier: Modifier = Modifier, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val less = stringResource(R.string.hydration_less)
        val more = stringResource(R.string.hydration_more)
        OutlinedButton(onClick = onMinus, modifier = Modifier.semantics { contentDescription = less }) { Text("−") }
        Text(value, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
        OutlinedButton(onClick = onPlus, modifier = Modifier.semantics { contentDescription = more }) { Text("+") }
    }
}

/** A container or a drink: tap to edit, arrows to reorder. */
@Composable
private fun OrderedRow(title: String, subtitle: String, icon: Int?, onClick: () -> Unit, onUp: () -> Unit, onDown: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = icon?.let { { Icon(painterResource(it), contentDescription = null) } },
        trailingContent = {
            Row {
                IconButton(onClick = onUp) { Icon(painterResource(R.drawable.ic_arrow_up), stringResource(R.string.hydration_move_up, title)) }
                IconButton(onClick = onDown) { Icon(painterResource(R.drawable.ic_arrow_down), stringResource(R.string.hydration_move_down, title)) }
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun RemindersSection(r: ReminderSettings, containers: List<ContainerEntity>, onChange: (ReminderSettings) -> Unit) {
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    SwitchRow(stringResource(R.string.hydration_reminders_switch), r.enabled, Modifier.testTag(HYDRATION_REMINDERS_TAG)) { onChange(r.copy(enabled = it)) }
    if (!r.enabled) return
    Text(stringResource(R.string.hydration_reminders_hours, clock(r.startMinutes), clock(r.endMinutes)), modifier = Modifier.padding(top = 4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picking = "start" }) { Text(stringResource(R.string.hydration_reminders_start) + " " + clock(r.startMinutes)) }
        OutlinedButton(onClick = { picking = "end" }) { Text(stringResource(R.string.hydration_reminders_end) + " " + clock(r.endMinutes)) }
    }
    Text(stringResource(R.string.hydration_reminders_interval), modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ReminderSettings.INTERVALS.forEach { minutes ->
            FilterChip(r.intervalMinutes == minutes, { onChange(r.copy(intervalMinutes = minutes)) }, { Text(stringResource(R.string.hydration_reminders_interval_value, minutes)) })
        }
    }
    SwitchRow(stringResource(R.string.hydration_skip_after_drink), r.skipAfterDrink, subtitle = stringResource(R.string.hydration_skip_after_drink_hint)) { onChange(r.copy(skipAfterDrink = it)) }
    SwitchRow(stringResource(R.string.hydration_skip_during_session), r.skipDuringSession) { onChange(r.copy(skipDuringSession = it)) }
    Text(stringResource(R.string.hydration_actions), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    val volumes = (containers.map { it.volumeMl } + r.action1Ml + r.action2Ml).distinct().sorted()
    listOf(r.action1Ml to { ml: Int -> r.copy(action1Ml = ml) }, r.action2Ml to { ml: Int -> r.copy(action2Ml = ml) }).forEachIndexed { i, (value, update) ->
        Text(stringResource(R.string.hydration_action_n, i + 1), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            volumes.forEach { ml ->
                val names = containers.filter { it.volumeMl == ml }.joinToString(", ") { it.name }
                FilterChip(value == ml, { onChange(update(ml)) }, { Text(if (names.isEmpty()) "$ml ml" else "$names · $ml ml") })
            }
        }
    }
    picking?.let { which ->
        val minutes = if (which == "start") r.startMinutes else r.endMinutes
        val state = rememberTimePickerState(minutes / 60, minutes % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = null },
            title = { Text(stringResource(if (which == "start") R.string.hydration_reminders_start else R.string.hydration_reminders_end)) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    val picked = state.hour * 60 + state.minute
                    onChange(if (which == "start") r.copy(startMinutes = picked) else r.copy(endMinutes = picked))
                    picking = null
                }) { Text(stringResource(R.string.hydration_ok)) }
            },
            dismissButton = { TextButton(onClick = { picking = null }) { Text(stringResource(R.string.water_cancel)) } },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ContainerDialog(container: ContainerEntity, drinks: List<DrinkEntity>, onSave: (ContainerEntity) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(container.name) }
    var volume by rememberSaveable { mutableStateOf(container.volumeMl.toString()) }
    var drinkId by rememberSaveable { mutableStateOf(container.defaultDrinkId) }
    var isDefault by rememberSaveable { mutableStateOf(container.isDefault) }
    var archived by rememberSaveable { mutableStateOf(container.archived) }
    val ml = volume.toIntOrNull()?.takeIf { it in HydrationMath.FREE_MIN_ML..HydrationMath.FREE_MAX_ML }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(container.name.ifEmpty { stringResource(R.string.hydration_add_container) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.hydration_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    volume, { volume = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.hydration_volume)) },
                    isError = ml == null, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(R.string.hydration_default_drink), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    drinks.forEach { d -> FilterChip(d.id == drinkId, { drinkId = d.id }, { Text(d.name) }) }
                }
                SwitchRow(stringResource(R.string.hydration_default_container), isDefault) { isDefault = it }
                if (container.id != 0L) {
                    TextButton(onClick = { archived = !archived }) { Text(stringResource(if (archived) R.string.hydration_restore else R.string.hydration_archive)) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { ml?.let { onSave(container.copy(name = name, volumeMl = it, defaultDrinkId = drinkId, isDefault = isDefault, archived = archived)) } },
                enabled = ml != null && name.isNotBlank(),
            ) { Text(stringResource(R.string.water_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.water_cancel)) } },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DrinkDialog(
    drink: DrinkEntity,
    productName: String?,
    onSave: (DrinkEntity) -> Unit,
    onChooseProduct: () -> Unit,
    onUnlink: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(drink.name) }
    var icon by rememberSaveable { mutableStateOf(drink.icon) }
    var percent by rememberSaveable { mutableStateOf((drink.hydrationCoefficient * 10).roundToInt().toFloat()) }
    var caloric by rememberSaveable { mutableStateOf(drink.isCaloric) }
    var archived by rememberSaveable { mutableStateOf(drink.archived) }
    val labels = LocalResources.current.getStringArray(R.array.drink_icon_labels)
    fun edited() = drink.copy(name = name, icon = icon, hydrationCoefficient = percent / 10.0, isCaloric = caloric, archived = archived)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(drink.name.ifEmpty { stringResource(R.string.hydration_add_drink) }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text(stringResource(R.string.hydration_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.hydration_icon), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DrinkIcons.all.entries.forEachIndexed { i, (key, res) ->
                        FilledTonalIconToggleButton(checked = icon == key, onCheckedChange = { icon = key }, modifier = Modifier.size(48.dp)) {
                            Icon(painterResource(res), contentDescription = labels.getOrNull(i))
                        }
                    }
                }
                Text(stringResource(R.string.hydration_coefficient, (percent * 10).roundToInt()))
                Slider(value = percent, onValueChange = { percent = it.roundToInt().toFloat() }, valueRange = 0f..10f, steps = 9)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = caloric, role = Role.Switch) { caloric = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.hydration_caloric), modifier = Modifier.weight(1f))
                    Switch(checked = caloric, onCheckedChange = null)
                }
                if (caloric && drink.id != 0L) {
                    Text(stringResource(R.string.hydration_product, productName ?: stringResource(R.string.hydration_drink_no_product)))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onSave(edited()); onChooseProduct() }) {
                            Text(stringResource(if (productName == null) R.string.hydration_choose_product else R.string.hydration_change_product))
                        }
                        if (productName != null) TextButton(onClick = onUnlink) { Text(stringResource(R.string.hydration_unlink_product)) }
                    }
                }
                if (drink.id != 0L) {
                    TextButton(onClick = { archived = !archived }) { Text(stringResource(if (archived) R.string.hydration_restore else R.string.hydration_archive)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(edited()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.water_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.water_cancel)) } },
    )
}
