package fr.olivier.forme.feature.hydration.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.olivier.forme.R
import fr.olivier.forme.core.humor.LocalQuips
import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.feature.hydration.data.ContainerEntity
import fr.olivier.forme.feature.hydration.data.DrinkEntity
import fr.olivier.forme.feature.hydration.data.WaterIntakeEntity
import fr.olivier.forme.feature.hydration.domain.HydrationMath
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

const val WATER_DRINK_TAG = "water_drink_"
const val WATER_CONTAINER_TAG = "water_container_"
const val WATER_OTHER_TAG = "water_other"
const val WATER_OTHER_FIELD_TAG = "water_other_field"
const val WATER_OTHER_ADD_TAG = "water_other_add"

/** How long the "Annuler" snackbar stays (5 s). */
private const val UNDO_MILLIS = 5_000L

/**
 * Choice of a drink and a quantity (V2.29): the drinks, then the containers, then "Autre quantité". No drink chosen:
 * a container adds the drink it usually holds (the cup, coffee); a chosen drink goes into any container.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun AddDrinkPanel(
    drinks: List<DrinkEntity>,
    containers: List<ContainerEntity>,
    onAdd: (drinkId: Long, volumeMl: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var other by rememberSaveable { mutableStateOf(false) }
    val chosen = drinks.firstOrNull { it.id == selected }
    val water = drinks.firstOrNull { it.isWater } ?: drinks.firstOrNull()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.water_drinks), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            drinks.forEach { drink ->
                FilterChip(
                    selected = drink.id == selected,
                    onClick = { selected = if (selected == drink.id) null else drink.id },
                    label = { Text(drink.name) },
                    leadingIcon = { Icon(painterResource(DrinkIcons.of(drink.icon)), contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag(WATER_DRINK_TAG + drink.id),
                )
            }
        }
        Text(stringResource(R.string.water_containers), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            containers.forEach { container ->
                val drink = chosen ?: drinks.firstOrNull { it.id == container.defaultDrinkId } ?: water
                val spoken = stringResource(R.string.water_container_add_spoken, container.name, container.volumeMl, drink?.name.orEmpty())
                AssistChip(
                    onClick = { drink?.let { onAdd(it.id, container.volumeMl) } },
                    label = { Text(stringResource(R.string.water_container_volume, container.name, container.volumeMl)) },
                    leadingIcon = drink?.let { { Icon(painterResource(DrinkIcons.of(it.icon)), contentDescription = null, modifier = Modifier.size(18.dp)) } },
                    modifier = Modifier.testTag(WATER_CONTAINER_TAG + container.id).semantics { contentDescription = spoken },
                )
            }
            AssistChip(
                onClick = { other = true },
                label = { Text(stringResource(R.string.water_other_amount)) },
                modifier = Modifier.testTag(WATER_OTHER_TAG),
            )
        }
    }
    if (other) {
        val drink = chosen ?: water
        AmountDialog(
            title = drink?.name ?: stringResource(R.string.water_other_amount),
            onAdd = { ml -> drink?.let { onAdd(it.id, ml) }; other = false },
            onDismiss = { other = false },
        )
    }
}

/** "Autre quantité": 10 to 2 000 ml, by 10 ml. */
@Composable
private fun AmountDialog(title: String, onAdd: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val value = text.toIntOrNull()?.takeIf { it in HydrationMath.FREE_MIN_ML..HydrationMath.FREE_MAX_ML }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { new -> text = new.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.water_amount_ml)) },
                supportingText = { Text(stringResource(R.string.water_amount_hint)) },
                isError = text.isNotEmpty() && value == null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag(WATER_OTHER_FIELD_TAG),
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let { onAdd(HydrationMath.coerceFree(it)) } }, enabled = value != null, modifier = Modifier.testTag(WATER_OTHER_ADD_TAG)) {
                Text(stringResource(R.string.water_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.water_cancel)) } },
    )
}

/** Long press on the "+" of the home tile: drink, container or free quantity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDrinkSheet(
    drinks: List<DrinkEntity>,
    containers: List<ContainerEntity>,
    onAdd: (drinkId: Long, volumeMl: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.water_choose_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 12.dp))
            AddDrinkPanel(drinks, containers, onAdd = { id, ml -> onAdd(id, ml); onDismiss() })
        }
    }
}

/** Changes the drink, the volume and the time of an intake, or deletes it. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun EditIntakeDialog(
    intake: WaterIntakeEntity,
    drinks: List<DrinkEntity>,
    zone: ZoneId,
    onSave: (drinkId: Long, volumeMl: Int, at: Instant) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var drinkId by rememberSaveable { mutableStateOf(intake.drinkId) }
    var volume by rememberSaveable { mutableStateOf(intake.volumeMl.toString()) }
    val time = LocalDateTime.ofInstant(intake.timestamp, zone)
    val timeState = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
    var pickingTime by remember { mutableStateOf(false) }
    val ml = volume.toIntOrNull()?.takeIf { it in HydrationMath.FREE_MIN_ML..HydrationMath.FREE_MAX_ML }
    // An archived drink stays offered for the intake that has it.
    val offered = drinks.filter { !it.archived || it.id == intake.drinkId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.water_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    offered.forEach { drink ->
                        FilterChip(
                            selected = drink.id == drinkId,
                            onClick = { drinkId = drink.id },
                            label = { Text(drink.name) },
                            leadingIcon = { Icon(painterResource(DrinkIcons.of(drink.icon)), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
                OutlinedTextField(
                    value = volume,
                    onValueChange = { new -> volume = new.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.water_amount_ml)) },
                    isError = ml == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { pickingTime = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.water_edit_time) + " : " + "%02d:%02d".format(timeState.hour, timeState.minute))
                }
                TextButton(onClick = onDelete) { Text(stringResource(R.string.water_delete), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val at = time.withHour(timeState.hour).withMinute(timeState.minute).atZone(zone).toInstant()
                    ml?.let { onSave(drinkId, HydrationMath.coerceFree(it), at) }
                },
                enabled = ml != null,
            ) { Text(stringResource(R.string.water_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.water_cancel)) } },
    )
    if (pickingTime) {
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text(stringResource(R.string.water_edit_time)) },
            text = { TimePicker(timeState) },
            confirmButton = { TextButton(onClick = { pickingTime = false }) { Text("OK") } },
        )
    }
}

/** Progress ring: [counted] / [goal] in litres in the centre. */
@Composable
fun WaterRing(countedMl: Int, goalMl: Int, color: Color, track: Color, modifier: Modifier = Modifier, size: Dp = 180.dp, stroke: Dp = 14.dp) {
    val fraction = if (goalMl > 0) (countedMl.toFloat() / goalMl).coerceIn(0f, 1f) else 0f
    val spoken = stringResource(R.string.water_progress_spoken, HydrationMath.litersSpoken(countedMl), HydrationMath.litersNumber(goalMl))
    Box(modifier.size(size).semantics(mergeDescendants = true) { contentDescription = spoken }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val width = stroke.toPx()
            val inset = width / 2
            val arc = Size(this.size.width - width, this.size.height - width)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(width))
            if (fraction > 0f) drawArc(color, -90f, 360f * fraction, false, Offset(inset, inset), arc, style = Stroke(width, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(HydrationMath.liters(countedMl), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("/ " + HydrationMath.liters(goalMl), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Shows "+250 ml · Annuler" for 5 s after each intake ("Annuler" deletes it, with its meal entry), then, once a
 * day, the "goal reached" message.
 */
@Composable
fun WaterSnackbarEffect(events: SharedFlow<WaterEvent>, snackbar: SnackbarHostState, onUndo: (Long) -> Unit) {
    val resources = LocalResources.current
    val quips = LocalQuips.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is WaterEvent.Added -> scope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    val result = withTimeoutOrNull(UNDO_MILLIS) {
                        snackbar.showSnackbar(
                            resources.getString(R.string.water_added, event.volumeMl),
                            actionLabel = resources.getString(R.string.water_undo),
                            duration = SnackbarDuration.Indefinite,
                        )
                    }
                    if (result == null) snackbar.currentSnackbarData?.dismiss()
                    if (result == SnackbarResult.ActionPerformed) {
                        onUndo(event.id)
                    } else if (event.goalJustReached) {
                        val text = quips?.text(Quip.WATER_GOAL_REACHED, HydrationMath.liters(event.goalMl))
                            ?: resources.getString(R.string.neutral_water_goal_reached)
                        snackbar.showSnackbar(text, duration = SnackbarDuration.Short)
                    }
                }
                WaterEvent.Failed -> scope.launch { snackbar.showSnackbar(resources.getString(R.string.water_error)) }
            }
        }
    }
}

/** "08:05" (history). */
fun Instant.localTime(zone: ZoneId): String = LocalDateTime.ofInstant(this, zone).let { "%02d:%02d".format(it.hour, it.minute) }

/** Delete action of a history row (48 dp, its own description). */
@Composable
internal fun DeleteButton(description: String, onClick: () -> Unit) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Icon(painterResource(R.drawable.ic_delete), contentDescription = null)
    }
}
