package fr.olivier.forme.feature.sport.ui.session

import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.core.humor.QuipText
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
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
import fr.olivier.forme.feature.heart.data.LiveHeart
import fr.olivier.forme.feature.heart.ui.LiveHeartStrip
import fr.olivier.forme.feature.heart.ui.RecoveryLine
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.domain.Prefill
import fr.olivier.forme.feature.sport.domain.SessionExerciseStatus
import fr.olivier.forme.feature.sport.domain.SessionTextFormatter
import fr.olivier.forme.feature.sport.domain.Plateau
import fr.olivier.forme.feature.sport.domain.PlateauDetector
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
    onSheet: (Long) -> Unit = {},
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val rest by viewModel.restTimerState.collectAsStateWithLifecycle()
    val lastLogged by viewModel.lastLogged.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val noted by viewModel.notedThisSession.collectAsStateWithLifecycle()
    val heart by viewModel.liveHeart.collectAsStateWithLifecycle()
    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.retryHeart()
    }
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
            Column {
                FormeTopBar(ui.session?.label() ?: "Séance", onBack) {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Abandonner la séance") }, onClick = {
                            menuOpen = false
                            abandoning = true
                        })
                    }
                }
                LiveHeartStrip(
                    live = heart,
                    onGrantPermission = { bluetoothPermission.launch(BLUETOOTH_PERMISSIONS) },
                    onRetry = viewModel::retryHeart,
                )
            }
        },
        bottomBar = { rest?.let { RestBar(it, heart, lastLogged, onExtend = viewModel::extendRest, onStop = viewModel::stopRest) } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            items(ui.exercises, key = { it.line.id }) { item ->
                val partner = item.line.supersetGroup?.let { g ->
                    ui.exercises.firstOrNull { it.line.supersetGroup == g && it.line.id != item.line.id }
                }
                ExerciseCard(
                    item = item,
                    // Both exercises of a superset are open together.
                    expanded = ui.expandedLineId == item.line.id || (partner != null && ui.expandedLineId == partner.line.id),
                    partner = partner,
                    linkedWithNext = partner != null && partner.line.position > item.line.position,
                    isFirst = item == ui.exercises.first(),
                    isLast = item == ui.exercises.last(),
                    notedThisSession = noted,
                    viewModel = viewModel,
                    onSheet = onSheet,
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

private val BLUETOOTH_PERMISSIONS =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
private fun RestBar(state: RestTimerState, heart: LiveHeart, logged: LoggedSet?, onExtend: (Int) -> Unit, onStop: () -> Unit) {
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
                if (done) {
                    // V2.15: one quip per rest.
                    QuipText(
                        Quip.REST_DONE,
                        key = state.endsAtMillis,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Text(
                        "Repos ${Fmt.mmss(remaining)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }
                val barContent = LocalContentColor.current
                TextButton(onClick = { onExtend(-15) }) { Text("−15", color = barContent) }
                TextButton(onClick = { onExtend(15) }) { Text("+15", color = barContent) }
                IconButton(onClick = onStop) { Icon(Icons.Filled.Close, contentDescription = "Arrêter") }
            }
            if (!done && logged != null) {
                QuipText(
                    if (logged.exerciseDone) Quip.EXERCISE_DONE else Quip.SET_DONE,
                    key = logged.key,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            RecoveryLine(heart)
        }
    }
}

@Composable
private fun ExerciseCard(
    item: ExerciseUi,
    expanded: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    partner: ExerciseUi?,
    linkedWithNext: Boolean,
    notedThisSession: Set<Long>,
    viewModel: ActiveSessionViewModel,
    onSheet: (Long) -> Unit,
) {
    val skipped = item.line.status == SessionExerciseStatus.SKIPPED
    var menu by remember { mutableStateOf(false) }
    var noting by remember { mutableStateOf(false) }
    var replacing by remember { mutableStateOf(false) }
    if (replacing) {
        val all by viewModel.allExercises.collectAsStateWithLifecycle()
        val inSession = viewModel.ui.collectAsStateWithLifecycle().value.exercises.map { it.exercise.id }.toSet()
        ReplaceDialog(
            current = item.exercise,
            candidates = all.filter { !it.archived && it.id !in inSession },
            onPick = {
                viewModel.replace(item, it.id)
                replacing = false
            },
            onDismiss = { replacing = false },
        )
    }
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
        // Superset members: framed in orange and drawn close together.
        modifier = Modifier.fillMaxWidth().padding(
            start = 12.dp,
            end = 12.dp,
            top = if (partner != null && !linkedWithNext) 0.dp else 4.dp,
            bottom = if (linkedWithNext) 2.dp else 4.dp,
        ),
        border = partner?.let { androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary) },
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
                partner?.let {
                    Text("⇄ Superset avec ${it.exercise.name}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                }
                item.line.replacedExerciseId?.let { id ->
                    val all by viewModel.allExercises.collectAsStateWithLifecycle()
                    all.firstOrNull { it.id == id }?.let {
                        Text("remplace ${it.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            // Exercise sheet (V2.9): illustration, muscles, settings, machine photo.
            IconButton(onClick = { onSheet(item.exercise.id) }) { Icon(Icons.Filled.Info, contentDescription = "Fiche de l'exercice") }
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
                if (linkedWithNext) {
                    DropdownMenuItem(text = { Text("Délier le superset") }, onClick = { menu = false; viewModel.toggleSuperset(item) })
                } else if (!isLast) {
                    DropdownMenuItem(text = { Text("⇄ Lier au suivant (superset)") }, onClick = { menu = false; viewModel.toggleSuperset(item) })
                }
                DropdownMenuItem(text = { Text("Remplacer… (machine occupée)") }, onClick = { menu = false; replacing = true })
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
        if (s is Suggestion.Load) {
            QuipText(
                Quip.PROGRESSION,
                Fmt.number(item.exercise.loadIncrementKg),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
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

    item.hint?.plateau?.let { PlateauCard(it) }

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

/** Same muscle group first; "Tous les exercices" widens the list. */
@Composable
private fun ReplaceDialog(
    current: fr.olivier.forme.feature.sport.data.ExerciseEntity,
    candidates: List<fr.olivier.forme.feature.sport.data.ExerciseEntity>,
    onPick: (fr.olivier.forme.feature.sport.data.ExerciseEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAll by remember { mutableStateOf(false) }
    val sameGroup = candidates.filter { it.muscleGroup == current.muscleGroup }
    val list = if (showAll || sameGroup.isEmpty()) candidates else sameGroup
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remplacer ${current.name}") },
        text = {
            Column {
                Text(
                    if (list === sameGroup) "Même groupe : ${current.muscleGroup.label}" else "Tous les exercices",
                    style = MaterialTheme.typography.labelLarge,
                )
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.id }) { ex ->
                        Text(
                            ex.name + if (list !== sameGroup) " · ${ex.muscleGroup.label}" else "",
                            modifier = Modifier.fillMaxWidth().clickable { onPick(ex) }.padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                if (!showAll && sameGroup.isNotEmpty()) TextButton(onClick = { showAll = true }) { Text("Tous les exercices") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

/** No progress for several sessions: the three ways out, in a compact card. */
@Composable
fun PlateauCard(plateau: Plateau, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                "Stagnation depuis ${plateau.sessions} séances (1RM estimé ${Fmt.kg(plateau.bestOneRepMaxKg.roundToHalf())})",
                style = MaterialTheme.typography.titleSmall,
            )
            PlateauDetector.advice(plateau).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun Double.roundToHalf(): Double = kotlin.math.round(this * 2) / 2
