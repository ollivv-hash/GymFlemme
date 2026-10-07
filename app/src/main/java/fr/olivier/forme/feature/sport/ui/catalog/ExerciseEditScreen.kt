package fr.olivier.forme.feature.sport.ui.catalog

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
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
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import fr.olivier.forme.feature.library.data.LibraryRepository
import fr.olivier.forme.feature.library.domain.LibraryLabels
import fr.olivier.forme.feature.library.ui.LibraryThumb
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.PhotoThumb
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.components.SmallStepper
import fr.olivier.forme.ui.navigation.ExerciseEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    private val exerciseId = savedStateHandle.toRoute<ExerciseEditRoute>().exerciseId
    val isNew = exerciseId == 0L

    private val _draft = MutableStateFlow(
        ExerciseEntity(name = "", muscleGroup = MuscleGroup.AUTRE, type = ExerciseType.WEIGHTED, defaultRepsMin = 10, defaultRepsMax = 12),
    )
    val draft = _draft.asStateFlow()

    /** Library entry illustrating the exercise (V2.9). */
    val illustration = _draft.map { it.libraryId }.distinctUntilChanged().flatMapLatest { id -> id?.let(library::observe) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var originalPhoto: String? = null

    init {
        if (!isNew) {
            viewModelScope.launch {
                repository.get(exerciseId)?.let {
                    originalPhoto = it.photoUri
                    _draft.value = it
                }
            }
        }
    }

    fun update(transform: (ExerciseEntity) -> ExerciseEntity) = _draft.update(transform)

    fun pickPhoto(uri: Uri) = viewModelScope.launch {
        repository.importPhoto(uri)?.let { path ->
            val previous = _draft.value.photoUri
            if (previous != originalPhoto) repository.deletePhoto(previous)
            _draft.update { it.copy(photoUri = path) }
        }
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val exercise = _draft.value.copy(name = _draft.value.name.trim())
        if (exercise.name.isEmpty()) return@launch
        repository.save(exercise)
        if (originalPhoto != null && originalPhoto != exercise.photoUri) repository.deletePhoto(originalPhoto)
        onDone()
    }
}

private val increments = listOf(0.5, 1.0, 1.25, 2.0, 2.5, 5.0)

@Composable
fun ExerciseEditScreen(
    onDone: () -> Unit,
    onPickIllustration: () -> Unit = {},
    onOpenSheet: (String) -> Unit = {},
    pickedLibraryId: String? = null,
    onPickConsumed: () -> Unit = {},
    viewModel: ExerciseEditViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val illustration by viewModel.illustration.collectAsStateWithLifecycle()
    LaunchedEffect(pickedLibraryId) {
        pickedLibraryId?.let { id ->
            viewModel.update { it.copy(libraryId = id) }
            onPickConsumed()
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::pickPhoto)
    }
    Scaffold(
        topBar = {
            FormeTopBar(if (viewModel.isNew) "Nouvel exercice" else "Modifier l'exercice", onDone) {
                TextButton(onClick = { viewModel.save(onDone) }, enabled = draft.name.isNotBlank()) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text("Nom") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("Groupe musculaire")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MuscleGroup.entries.forEach { group ->
                    FilterChip(
                        selected = draft.muscleGroup == group,
                        onClick = { viewModel.update { it.copy(muscleGroup = group) } },
                        label = { Text(group.label) },
                    )
                }
            }

            SectionTitle("Type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseType.entries.forEach { type ->
                    FilterChip(
                        selected = draft.type == type,
                        onClick = { viewModel.update { it.copy(type = type) } },
                        label = { Text(type.label) },
                    )
                }
            }
            if (draft.type == ExerciseType.TIMED) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Durée par côté", Modifier.weight(1f))
                    Switch(checked = draft.perSide, onCheckedChange = { v -> viewModel.update { it.copy(perSide = v) } })
                }
            }

            if (draft.type == ExerciseType.WEIGHTED || draft.type == ExerciseType.BODYWEIGHT) {
                SectionTitle("Incrément de charge")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    increments.forEach { inc ->
                        FilterChip(
                            selected = draft.loadIncrementKg == inc,
                            onClick = { viewModel.update { it.copy(loadIncrementKg = inc) } },
                            label = { Text(Fmt.kg(inc)) },
                        )
                    }
                }
            }

            SectionTitle("Cible par défaut")
            Text(
                "Utilisée quand l'exercice est ajouté à un modèle ou à une séance libre.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                SmallStepper(
                    "Séries",
                    "${draft.defaultTargetSets}",
                    { viewModel.update { it.copy(defaultTargetSets = (it.defaultTargetSets - 1).coerceAtLeast(1)) } },
                    { viewModel.update { it.copy(defaultTargetSets = it.defaultTargetSets + 1) } },
                )
                when (draft.type) {
                    ExerciseType.WEIGHTED, ExerciseType.BODYWEIGHT -> {
                        SmallStepper(
                            "Reps min",
                            "${draft.defaultRepsMin ?: "-"}",
                            { viewModel.update { it.copy(defaultRepsMin = ((it.defaultRepsMin ?: 10) - 1).coerceAtLeast(1)) } },
                            {
                                viewModel.update {
                                    val min = (it.defaultRepsMin ?: 10) + 1
                                    it.copy(defaultRepsMin = min, defaultRepsMax = maxOf(min, it.defaultRepsMax ?: min))
                                }
                            },
                        )
                        SmallStepper(
                            "Reps max",
                            "${draft.defaultRepsMax ?: "-"}",
                            {
                                viewModel.update {
                                    val max = ((it.defaultRepsMax ?: 12) - 1).coerceAtLeast(1)
                                    it.copy(defaultRepsMax = max, defaultRepsMin = minOf(max, it.defaultRepsMin ?: max))
                                }
                            },
                            { viewModel.update { it.copy(defaultRepsMax = (it.defaultRepsMax ?: 12) + 1) } },
                        )
                    }
                    ExerciseType.TIMED, ExerciseType.CARDIO -> {
                        val step = if (draft.type == ExerciseType.CARDIO) 30 else 5
                        SmallStepper(
                            "Durée",
                            Fmt.duration(draft.defaultDurationSec ?: 0),
                            { viewModel.update { it.copy(defaultDurationSec = ((it.defaultDurationSec ?: 0) - step).coerceAtLeast(step)) } },
                            { viewModel.update { it.copy(defaultDurationSec = (it.defaultDurationSec ?: 0) + step) } },
                        )
                    }
                }
            }

            SectionTitle("Notes")
            OutlinedTextField(
                value = draft.settingsNote.orEmpty(),
                onValueChange = { v -> viewModel.update { it.copy(settingsNote = v.ifEmpty { null }) } },
                label = { Text("Réglages (siège, cran…)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.loadNote.orEmpty(),
                onValueChange = { v -> viewModel.update { it.copy(loadNote = v.ifEmpty { null }) } },
                label = { Text("Note de charge (ex. chariot vide + X kg)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            SectionTitle("Illustration")
            val entry = illustration
            if (entry != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LibraryThumb(entry, 72.dp, Modifier.clickable { onOpenSheet(entry.id) })
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.titleSmall)
                        Text(LibraryLabels.equipmentOf(entry), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row {
                    TextButton(onClick = { onOpenSheet(entry.id) }) { Text("Voir la fiche") }
                    TextButton(onClick = onPickIllustration) { Text("Changer") }
                    TextButton(onClick = { viewModel.update { it.copy(libraryId = null) } }) { Text("Retirer") }
                }
            } else {
                OutlinedButton(onClick = onPickIllustration) { Text("Choisir dans la bibliothèque") }
            }

            SectionTitle("Photo")
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhotoThumb(draft.photoUri, 96.dp)
                if (draft.photoUri != null) Spacer(Modifier.width(12.dp))
                Column {
                    OutlinedButton(onClick = {
                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text(if (draft.photoUri == null) "Choisir une photo" else "Changer") }
                    if (draft.photoUri != null) {
                        TextButton(onClick = { viewModel.update { it.copy(photoUri = null) } }) { Text("Retirer") }
                    }
                }
            }

            Spacer(Modifier.height(24.dp)) // Saving is done with "Enregistrer" in the top bar.
        }
    }
}
