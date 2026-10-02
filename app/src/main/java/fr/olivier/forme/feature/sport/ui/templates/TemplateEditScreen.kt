package fr.olivier.forme.feature.sport.ui.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.ExerciseRepository
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.TemplateExerciseEntity
import fr.olivier.forme.feature.sport.data.TemplateRepository
import fr.olivier.forme.feature.sport.data.WorkoutTemplateEntity
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SmallStepper
import fr.olivier.forme.ui.navigation.TemplateEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import javax.inject.Inject

data class DraftLine(
    val key: Long,
    val exercise: ExerciseEntity,
    val targetSets: Int,
    val repsMin: Int?,
    val repsMax: Int?,
    val durationSec: Int?,
    val optional: Boolean,
    /** Null = default rest from settings. */
    val restSec: Int?,
)

data class TemplateDraft(
    val template: WorkoutTemplateEntity = WorkoutTemplateEntity(name = "", code = ""),
    val lines: List<DraftLine> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class TemplateEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val templates: TemplateRepository,
    private val sessions: SessionRepository,
    exercises: ExerciseRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TemplateEditRoute>()
    val isNew = route.templateId == 0L
    private var nextKey = 1L

    private val _draft = MutableStateFlow(TemplateDraft())
    val draft = _draft.asStateFlow()
    val catalog = exercises.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _draft.value = when {
                route.templateId != 0L -> {
                    val template = templates.get(route.templateId) ?: WorkoutTemplateEntity(name = "", code = "")
                    TemplateDraft(template, templates.getLines(route.templateId).map { (l, e) -> l.toDraft(e) }, true)
                }
                route.sourceTemplateId != 0L -> {
                    val source = templates.get(route.sourceTemplateId)
                    TemplateDraft(
                        WorkoutTemplateEntity(name = "${source?.name.orEmpty()} (variante)", code = "${source?.code.orEmpty()}'"),
                        templates.getLines(route.sourceTemplateId).map { (l, e) -> l.toDraft(e) },
                        true,
                    )
                }
                route.sourceSessionId != 0L -> {
                    val session = sessions.observeSession(route.sourceSessionId).first()
                    val all = exercises.observeAll().first().associateBy { it.id }
                    TemplateDraft(
                        WorkoutTemplateEntity(name = "${session?.templateNameSnapshot ?: "Séance"} (variante)", code = ""),
                        templates.linesFromSession(route.sourceSessionId).mapNotNull { l -> all[l.exerciseId]?.let { l.toDraft(it) } },
                        true,
                    )
                }
                else -> TemplateDraft(loaded = true)
            }
        }
    }

    private fun TemplateExerciseEntity.toDraft(exercise: ExerciseEntity) =
        DraftLine(nextKey++, exercise, targetSets, targetRepsMin, targetRepsMax, targetDurationSec, optional, restSec)

    fun rename(name: String) = _draft.update { it.copy(template = it.template.copy(name = name)) }
    fun recode(code: String) = _draft.update { it.copy(template = it.template.copy(code = code.uppercase())) }

    fun updateLine(key: Long, transform: (DraftLine) -> DraftLine) =
        _draft.update { d -> d.copy(lines = d.lines.map { if (it.key == key) transform(it) else it }) }

    fun removeLine(key: Long) = _draft.update { d -> d.copy(lines = d.lines.filterNot { it.key == key }) }

    fun move(fromKey: Any, toKey: Any) = _draft.update { d ->
        val from = d.lines.indexOfFirst { it.key == fromKey }
        val to = d.lines.indexOfFirst { it.key == toKey }
        if (from < 0 || to < 0) d else d.copy(lines = d.lines.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun add(exercise: ExerciseEntity) = _draft.update { d ->
        d.copy(
            lines = d.lines + DraftLine(
                key = nextKey++,
                exercise = exercise,
                targetSets = exercise.defaultTargetSets,
                repsMin = exercise.defaultRepsMin,
                repsMax = exercise.defaultRepsMax,
                durationSec = exercise.defaultDurationSec,
                optional = false,
                restSec = null,
            ),
        )
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val d = _draft.value
        if (d.template.name.isBlank()) return@launch
        templates.save(
            d.template.copy(name = d.template.name.trim(), code = d.template.code.trim()),
            d.lines.map {
                TemplateExerciseEntity(
                    templateId = d.template.id,
                    exerciseId = it.exercise.id,
                    position = 0,
                    targetSets = it.targetSets,
                    targetRepsMin = it.repsMin,
                    targetRepsMax = it.repsMax,
                    targetDurationSec = it.durationSec,
                    optional = it.optional,
                    restSec = it.restSec,
                )
            },
        )
        onDone()
    }
}

@Composable
fun TemplateEditScreen(onDone: () -> Unit, viewModel: TemplateEditViewModel = hiltViewModel()) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to -> viewModel.move(from.key, to.key) }

    Scaffold(
        topBar = {
            FormeTopBar(if (viewModel.isNew) "Nouveau modèle" else "Modifier le modèle", onDone) {
                TextButton(onClick = { viewModel.save(onDone) }, enabled = draft.template.name.isNotBlank()) { Text("Enregistrer") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { picking = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Exercice") })
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.template.code,
                    onValueChange = viewModel::recode,
                    label = { Text("Code") },
                    singleLine = true,
                    modifier = Modifier.weight(0.3f),
                )
                OutlinedTextField(
                    value = draft.template.name,
                    onValueChange = viewModel::rename,
                    label = { Text("Nom") },
                    singleLine = true,
                    modifier = Modifier.weight(0.7f),
                )
            }
            Text(
                "Glisser ☰ pour réordonner. Les séances passées ne sont jamais modifiées.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp, 8.dp),
            )
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
                items(draft.lines, key = { it.key }) { line ->
                    ReorderableItem(reorderState, key = line.key) { dragging ->
                        LineCard(
                            line = line,
                            elevated = dragging,
                            handle = {
                                IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Déplacer")
                                }
                            },
                            onChange = { transform -> viewModel.updateLine(line.key, transform) },
                            onRemove = { viewModel.removeLine(line.key) },
                        )
                    }
                }
                item { androidx.compose.foundation.layout.Spacer(Modifier.padding(48.dp)) }
            }
        }
    }

    if (picking) {
        ExercisePickerDialog(catalog, onPick = { viewModel.add(it); picking = false }, onDismiss = { picking = false })
    }
}

@Composable
private fun LineCard(
    line: DraftLine,
    elevated: Boolean,
    handle: @Composable () -> Unit,
    onChange: ((DraftLine) -> DraftLine) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (elevated) 8.dp else 1.dp),
    ) {
        Column(Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                handle()
                Text(line.exercise.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, contentDescription = "Retirer") }
            }
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                SmallStepper(
                    "Séries",
                    "${line.targetSets}",
                    { onChange { it.copy(targetSets = (it.targetSets - 1).coerceAtLeast(1)) } },
                    { onChange { it.copy(targetSets = it.targetSets + 1) } },
                )
                when (line.exercise.type) {
                    ExerciseType.WEIGHTED, ExerciseType.BODYWEIGHT -> {
                        SmallStepper(
                            "Reps min",
                            "${line.repsMin ?: "-"}",
                            { onChange { it.copy(repsMin = ((it.repsMin ?: 10) - 1).coerceAtLeast(1)) } },
                            {
                                onChange {
                                    val min = (it.repsMin ?: 10) + 1
                                    it.copy(repsMin = min, repsMax = maxOf(min, it.repsMax ?: min))
                                }
                            },
                        )
                        SmallStepper(
                            "Reps max",
                            "${line.repsMax ?: "-"}",
                            {
                                onChange {
                                    val max = ((it.repsMax ?: 12) - 1).coerceAtLeast(1)
                                    it.copy(repsMax = max, repsMin = minOf(max, it.repsMin ?: max))
                                }
                            },
                            { onChange { it.copy(repsMax = (it.repsMax ?: 12) + 1) } },
                        )
                    }
                    ExerciseType.TIMED, ExerciseType.CARDIO -> {
                        val step = if (line.exercise.type == ExerciseType.CARDIO) 30 else 5
                        SmallStepper(
                            if (line.exercise.perSide) "Durée/côté" else "Durée",
                            Fmt.duration(line.durationSec ?: 0),
                            { onChange { it.copy(durationSec = ((it.durationSec ?: 0) - step).coerceAtLeast(step)) } },
                            { onChange { it.copy(durationSec = (it.durationSec ?: 0) + step) } },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallStepper(
                    "Repos",
                    line.restSec?.let { Fmt.mmss(it) } ?: "défaut",
                    { onChange { it.copy(restSec = it.restSec?.minus(15)?.takeIf { s -> s >= 15 }) } },
                    { onChange { it.copy(restSec = (it.restSec ?: 75) + 15) } },
                )
                Text("Optionnel", Modifier.weight(1f).padding(start = 16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                Switch(
                    checked = line.optional,
                    onCheckedChange = { v -> onChange { it.copy(optional = v) } },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

/** Catalogue picker shared by the template editor and the running session. */
@Composable
fun ExercisePickerDialog(exercises: List<ExerciseEntity>, onPick: (ExerciseEntity) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un exercice") },
        text = {
            LazyColumn {
                items(exercises, key = { it.id }) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = { Text(exercise.muscleGroup.label) },
                        modifier = Modifier.clickable { onPick(exercise) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
