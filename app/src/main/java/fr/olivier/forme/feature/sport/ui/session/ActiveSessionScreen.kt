package fr.olivier.forme.feature.sport.ui.session

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.olivier.forme.core.settings.RestTimerState
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.domain.Prefill
import fr.olivier.forme.feature.sport.domain.SessionExerciseStatus
import fr.olivier.forme.feature.sport.domain.SessionTextFormatter
import fr.olivier.forme.feature.sport.domain.Suggestion
import fr.olivier.forme.feature.sport.ui.home.label
import fr.olivier.forme.feature.sport.ui.templates.ExercisePickerDialog
import fr.olivier.forme.feature.sport.data.toWorkSet
import fr.olivier.forme.ui.components.ConfirmDialog
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.RirChips
import fr.olivier.forme.ui.components.Stepper
import fr.olivier.forme.ui.components.parseDecimal
import fr.olivier.forme.ui.components.parseDuration
import kotlinx.coroutines.delay

@Composable
fun ActiveSessionScreen(
    onBack: () -> Unit,
    onFinished: (Long) -> Unit,
    onAbandoned: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val rest by viewModel.restTimerState.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val noted by viewModel.notedThisSession.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var abandoning by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    // Session finished elsewhere or abandoned: nothing to show.
    LaunchedEffect(ui.loaded, ui.session) { if (ui.loaded && ui.session == null && !leaving) onBack() }

    Scaffold(
        topBar = {
            FormeTopBar(ui.session?.label() ?: "Séance", onBack) {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Abandonner la séance") }, onClick = {
                        menuOpen = false
                        abandoning = true
                    })
                }
            }
        },
        bottomBar = { rest?.let { RestBar(it, onExtend = viewModel::extendRest, onStop = viewModel::stopRest) } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(ui.exercises, key = { it.line.id }) { item ->
                ExerciseCard(
                    item = item,
                    expanded = ui.expandedLineId == item.line.id,
                    isFirst = item == ui.exercises.first(),
                    isLast = item == ui.exercises.last(),
                    notedThisSession = noted,
                    viewModel = viewModel,
                )
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("Ajouter un exercice") }
                    Button(
                        onClick = { finishing = true },
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp),
                    ) { Text("Terminer la séance") }
                }
            }
        }
    }

    if (picking) {
        ExercisePickerDialog(catalog, onPick = { viewModel.addExercise(it.id); picking = false }, onDismiss = { picking = false })
    }
    if (finishing) {
        FinishDialog(onDismiss = { finishing = false }, onConfirm = { feeling, notes ->
            finishing = false
            leaving = true
            viewModel.finish(feeling, notes, onFinished)
        })
    }
    if (abandoning) {
        ConfirmDialog(
            title = "Abandonner la séance ?",
            text = "Toutes les séries saisies pendant cette séance seront supprimées.",
            confirmLabel = "Abandonner",
            onConfirm = {
                abandoning = false
                leaving = true
                viewModel.abandon(onAbandoned)
            },
            onDismiss = { abandoning = false },
        )
    }
}

@Composable
private fun RestBar(state: RestTimerState, onExtend: (Int) -> Unit, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val remaining = ((state.endsAtMillis - now) / 1000).coerceAtLeast(0).toInt()
    // Orange = attention: the rest countdown, solid orange once the rest is over.
    val done = remaining == 0
    Surface(
        color = if (done) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = if (done) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (state.totalSec > 0) {
                LinearProgressIndicator(
                    progress = { 1f - remaining.toFloat() / state.totalSec },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (remaining == 0) "Repos terminé" else "Repos ${Fmt.mmss(remaining)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                val barContent = LocalContentColor.current
                TextButton(onClick = { onExtend(-15) }) { Text("−15", color = barContent) }
                TextButton(onClick = { onExtend(15) }) { Text("+15", color = barContent) }
                IconButton(onClick = onStop) { Icon(Icons.Filled.Close, contentDescription = "Arrêter") }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    item: ExerciseUi,
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    notedThisSession: Set<Long>,
    viewModel: ActiveSessionViewModel,
) {
    val skipped = item.line.status == SessionExerciseStatus.SKIPPED
    var menu by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }
    if (noting) {
        NoteDialog(
            exerciseName = item.exercise.name,
            initial = item.exercise.nextNote.orEmpty(),
            onDismiss = { noting = false },
            onSave = {
                viewModel.saveNote(item, it)
                noting = false
            },
        )
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { viewModel.expand(item.line.id) }.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (item.finished) if (skipped) "⤼ " else "✓ " else "") + item.exercise.name +
                        if (item.line.optional) " (optionnel)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (item.finished) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                Text(targetLabel(item), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { noting = true }) { Icon(Icons.Filled.Edit, contentDescription = "Note pour la prochaine fois") }
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Actions") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!isFirst) DropdownMenuItem(text = { Text("Monter") }, onClick = { menu = false; viewModel.move(item, -1) })
                if (!isLast) DropdownMenuItem(text = { Text("Descendre") }, onClick = { menu = false; viewModel.move(item, 1) })
                if (item.line.status == SessionExerciseStatus.PENDING) {
                    DropdownMenuItem(text = { Text("Sauter") }, onClick = { menu = false; viewModel.setSkipped(item, true) })
                } else {
                    DropdownMenuItem(text = { Text("Reprendre") }, onClick = { menu = false; viewModel.reopen(item) })
                }
                DropdownMenuItem(text = { Text("Retirer de la séance") }, onClick = { menu = false; viewModel.remove(item) })
            }
        }
        val note = item.exercise.nextNote
        val notedNow = item.exercise.id in notedThisSession
        if (note != null && !notedNow) {
            NoteBanner(note, onDismiss = { viewModel.dismissNote(item) })
        }
        if (expanded) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                if (note != null && notedNow) {
                    Text("📝 Note enregistrée pour la prochaine fois : $note", style = MaterialTheme.typography.bodySmall)
                }
                item.exercise.settingsNote?.let { Text("Réglages : $it", style = MaterialTheme.typography.bodySmall) }
                item.exercise.loadNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (item.exercise.type == ExerciseType.CARDIO) CardioEditor(item, viewModel) else SetEditor(item, viewModel)
            }
        } else if (item.sets.isNotEmpty() || item.cardio != null) {
            Text(
                summaryLine(item),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}

private fun targetLabel(item: ExerciseUi): String {
    val l = item.line
    val target = when (item.exercise.type) {
        ExerciseType.WEIGHTED, ExerciseType.BODYWEIGHT -> {
            val reps = if (l.targetRepsMin != null && l.targetRepsMax != null && l.targetRepsMin != l.targetRepsMax) {
                "${l.targetRepsMin}–${l.targetRepsMax}"
            } else {
                "${l.targetRepsMax ?: l.targetRepsMin ?: "?"}"
            }
            "${l.targetSets} × $reps"
        }
        ExerciseType.TIMED -> "${l.targetSets} × ${Fmt.duration(l.targetDurationSec ?: 0)}" + if (item.exercise.perSide) "/côté" else ""
        ExerciseType.CARDIO -> Fmt.duration(l.targetDurationSec ?: 0)
    }
    val done = if (item.exercise.type == ExerciseType.CARDIO) "" else " · ${item.workingSets.size}/${l.targetSets} séries"
    return target + done
}

private fun summaryLine(item: ExerciseUi): String =
    item.cardio?.let { c ->
        listOfNotNull(
            Fmt.mmss(c.durationSec),
            c.distanceM?.let { "$it m" },
            c.pacePer500mSec?.let { "${Fmt.mmss(it)} /500 m" },
        ).joinToString(" · ")
    } ?: item.workingSets.joinToString(" · ") { SessionTextFormatter.setLabel(item.exercise.type, item.exercise.perSide, it.toWorkSet()) }

@Composable
private fun SetEditor(item: ExerciseUi, viewModel: ActiveSessionViewModel) {
    val type = item.exercise.type
    val d = item.draft

    item.hint?.lastTime?.takeIf { it.isNotEmpty() }?.let { last ->
        Text(
            "Dernière fois : " + last.joinToString(" · ") { SessionTextFormatter.setLabel(type, item.exercise.perSide, it.toWorkSet()) },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    item.hint?.suggestion?.let { s ->
        AssistChip(
            colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.tertiary),
            onClick = { viewModel.applySuggestion(item) },
            label = {
                Text(
                    "↑ suggéré : " + when (s) {
                        is Suggestion.Load -> Fmt.kg(s.loadKg)
                        is Suggestion.Reps -> "${s.reps} reps"
                        is Suggestion.Duration -> Fmt.duration(s.durationSec)
                    } + " (toucher pour appliquer)",
                )
            },
        )
    }

    // Sets already done in this session.
    item.sets.forEachIndexed { index, set ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (set.isWarmup) "Échauff. " else "Série ${item.sets.take(index + 1).count { !it.isWarmup }} ") +
                    SessionTextFormatter.setLabel(type, item.exercise.perSide, set.toWorkSet()),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            IconButton(onClick = { viewModel.deleteSet(set) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Supprimer la série")
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        when (type) {
            ExerciseType.WEIGHTED, ExerciseType.BODYWEIGHT -> {
                Stepper(
                    label = if (type == ExerciseType.BODYWEIGHT) "Lest" else "Charge",
                    valueText = Fmt.kg(d.loadKg ?: 0.0),
                    onMinus = { viewModel.changeLoad(item, -1) },
                    onPlus = { viewModel.changeLoad(item, 1) },
                    onValueEntered = { t -> parseDecimal(t)?.let { v -> viewModel.updateDraft(item) { it.copy(loadKg = v.coerceAtLeast(0.0)) } } },
                    decimal = true,
                    modifier = Modifier.weight(1f),
                )
                Stepper(
                    label = "Reps",
                    valueText = "${d.reps ?: 0}",
                    onMinus = { viewModel.updateDraft(item) { it.copy(reps = ((it.reps ?: 0) - 1).coerceAtLeast(0)) } },
                    onPlus = { viewModel.updateDraft(item) { it.copy(reps = (it.reps ?: 0) + 1) } },
                    onValueEntered = { t -> t.trim().toIntOrNull()?.let { v -> viewModel.updateDraft(item) { it.copy(reps = v) } } },
                    modifier = Modifier.weight(1f),
                )
            }
            ExerciseType.TIMED -> Stepper(
                label = if (item.exercise.perSide) "Durée par côté" else "Durée",
                valueText = Fmt.duration(d.durationSec ?: 0),
                onMinus = { viewModel.updateDraft(item) { it.copy(durationSec = ((it.durationSec ?: 0) - 5).coerceAtLeast(0)) } },
                onPlus = { viewModel.updateDraft(item) { it.copy(durationSec = (it.durationSec ?: 0) + 5) } },
                onValueEntered = { t -> parseDuration(t)?.let { v -> viewModel.updateDraft(item) { it.copy(durationSec = v) } } },
                modifier = Modifier.weight(1f),
            )
            ExerciseType.CARDIO -> Unit
        }
    }
    if (type != ExerciseType.TIMED) {
        RirChips(
            selected = d.rir,
            onSelect = { v -> viewModel.updateDraft(item) { it.copy(rir = v) } },
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { viewModel.validateSet(item, warmup = true) }) { Text("Échauff.") }
        Spacer(Modifier.width(8.dp))
        Button(onClick = { viewModel.validateSet(item, warmup = false) }, modifier = Modifier.weight(1f).height(64.dp)) {
            Text(
                if (item.workingSets.size >= item.line.targetSets) "Série supplémentaire" else "Valider la série ${item.workingSets.size + 1}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun CardioEditor(item: ExerciseUi, viewModel: ActiveSessionViewModel) {
    val d = item.cardioDraft
    item.hint?.lastCardio?.let { last ->
        Text(
            "Dernière fois : " + listOfNotNull(
                Fmt.mmss(last.durationSec),
                last.distanceM?.let { "$it m" },
                last.pacePer500mSec?.let { "${Fmt.mmss(it)} /500 m" },
                last.caloriesDisplayed?.let { "$it kcal" },
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Stepper(
            label = "Durée",
            valueText = Fmt.mmss(d.durationSec),
            onMinus = { viewModel.updateCardio(item) { it.copy(durationSec = (it.durationSec - 30).coerceAtLeast(0)) } },
            onPlus = { viewModel.updateCardio(item) { it.copy(durationSec = it.durationSec + 30) } },
            onValueEntered = { t -> parseDuration(t)?.let { v -> viewModel.updateCardio(item) { it.copy(durationSec = v) } } },
        )
        Stepper(
            label = "Distance (m)",
            valueText = "${d.distanceM ?: 0}",
            onMinus = { viewModel.updateCardio(item) { it.copy(distanceM = ((it.distanceM ?: 0) - 50).coerceAtLeast(0)) } },
            onPlus = { viewModel.updateCardio(item) { it.copy(distanceM = (it.distanceM ?: 0) + 50) } },
            onValueEntered = { t -> t.trim().toIntOrNull()?.let { v -> viewModel.updateCardio(item) { it.copy(distanceM = v) } } },
        )
        Stepper(
            label = "Calories",
            valueText = "${d.calories ?: 0}",
            onMinus = { viewModel.updateCardio(item) { it.copy(calories = ((it.calories ?: 0) - 5).coerceAtLeast(0)) } },
            onPlus = { viewModel.updateCardio(item) { it.copy(calories = (it.calories ?: 0) + 5) } },
            onValueEntered = { t -> t.trim().toIntOrNull()?.let { v -> viewModel.updateCardio(item) { it.copy(calories = v) } } },
        )
    }
    Text(
        "Allure : " + (Prefill.pacePer500m(d.durationSec, d.distanceM)?.let { "${Fmt.mmss(it)} /500 m" } ?: "—"),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(top = 8.dp),
    )
    Button(onClick = { viewModel.saveCardio(item) }, modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 8.dp)) {
        Text(if (item.cardio == null) "Enregistrer" else "Mettre à jour")
    }
}

@Composable
private fun FinishDialog(onDismiss: () -> Unit, onConfirm: (Int?, String) -> Unit) {
    var feeling by rememberSaveable { mutableStateOf<Int?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terminer la séance") },
        text = {
            Column {
                Text("Ressenti")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { v ->
                        FilterChip(selected = feeling == v, onClick = { feeling = if (feeling == v) null else v }, label = { Text("$v") })
                    }
                }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    minLines = 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(feeling, notes) }) { Text("Terminer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Continuer") } },
    )
}

/** Phrases offered as one-tap notes. */
private val notePresets = listOf("Monter la charge", "Trop dur, rester", "Douleur / gêne", "Changer le réglage")

@Composable
private fun NoteDialog(exerciseName: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pour la prochaine fois") },
        text = {
            Column {
                Text(exerciseName, style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    notePresets.forEach { preset ->
                        FilterChip(selected = text == preset, onClick = { text = preset }, label = { Text(preset) })
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Note (facultatif)") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("Enregistrer") } },
        dismissButton = {
            Row {
                if (initial.isNotEmpty()) TextButton(onClick = { onSave("") }) { Text("Effacer") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        },
    )
}

/** Note left at the previous session, until dismissed. */
@Composable
private fun NoteBanner(note: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📝 $note", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onDismiss) { Text("Vu ✓", color = MaterialTheme.colorScheme.tertiary) }
        }
    }
}
