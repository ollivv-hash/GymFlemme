package fr.olivier.forme.feature.library.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.library.data.LibraryExerciseEntity
import fr.olivier.forme.feature.library.data.LibraryRepository
import fr.olivier.forme.feature.library.domain.LibraryLabels
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.ExerciseRepository
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.PhotoThumb
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.navigation.ExerciseSheetRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SheetUi(val exercise: ExerciseEntity?, val library: LibraryExerciseEntity?, val loaded: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseSheetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    exercises: ExerciseRepository,
    library: LibraryRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<ExerciseSheetRoute>()

    val ui = (if (route.exerciseId > 0) exercises.observe(route.exerciseId) else flowOf(null))
        .flatMapLatest { exercise ->
            val id = exercise?.libraryId ?: route.libraryId.takeIf { it.isNotEmpty() }
            (if (id == null) flowOf(null) else library.observe(id)).map { SheetUi(exercise, it, loaded = true) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SheetUi(null, null, loaded = false))
}

/**
 * Exercise sheet (V2.9): animated illustration from the library, muscles, equipment and movement, then the user's
 * own landmarks and machine photo when it is an exercise of the catalogue.
 */
@Composable
fun ExerciseSheetScreen(onBack: () -> Unit, onEdit: ((Long) -> Unit)?, viewModel: ExerciseSheetViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val exercise = ui.exercise
    val entry = ui.library
    Scaffold(
        topBar = {
            FormeTopBar(exercise?.name ?: entry?.name ?: "Fiche", onBack) {
                if (exercise != null && onEdit != null) TextButton(onClick = { onEdit(exercise.id) }) { Text("Modifier") }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (entry != null) {
                ExerciseIllustration(entry)
                Text(AI_NOTICE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                LibraryDetails(entry, exercise)
            } else if (ui.loaded) {
                Text(
                    "Pas d'illustration pour cet exercice. Choisis-en une dans « Modifier » → Illustration.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            if (exercise != null) MyLandmarks(exercise)
            Spacer(Modifier.height(24.dp))
        }
    }
}

const val AI_NOTICE = "Illustration générée par IA, à titre indicatif. Vérifie le mouvement auprès d'une source qualifiée."

@Composable
private fun LibraryDetails(entry: LibraryExerciseEntity, exercise: ExerciseEntity?) {
    Text(entry.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    if (exercise != null && exercise.name != entry.name) {
        Text("Dans la bibliothèque · ${exercise.name} dans ton catalogue", style = MaterialTheme.typography.bodySmall)
    }
    entry.difference?.let { Text("Variante : $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
    val group = exercise?.muscleGroup ?: LibraryLabels.muscleGroupOf(entry)
    Line("Groupe", group.label)
    Line("Matériel", LibraryLabels.equipmentOf(entry))

    SectionTitle("Muscles")
    MuscleChips("Principaux", entry.primaryList)
    MuscleChips("Secondaires", entry.secondaryList)
    MuscleChips("Sollicite aussi", entry.alsoTargetsList)

    SectionTitle("Mouvement")
    Line("Niveau", LibraryLabels.levels[entry.level] ?: entry.level)
    Line("Type", LibraryLabels.patterns[entry.movementPattern] ?: entry.movementPattern)
    Line("Catégorie", LibraryLabels.categories[entry.category] ?: entry.category)
    Line("Impact", LibraryLabels.impacts[entry.impact] ?: entry.impact)
    Line("Où", entry.suitableForList.joinToString(", ") { LibraryLabels.places[it] ?: it })
    Line("Vue", LibraryLabels.views[entry.view] ?: entry.view)
    if (entry.unilateral) {
        Text(
            "Un côté à la fois : l'illustration n'en montre qu'un, fais les deux.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    if (entry.isWarmUp) Text("Souvent utilisé en échauffement.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun MyLandmarks(exercise: ExerciseEntity) {
    val notes = listOfNotNull(exercise.settingsNote?.let { "Réglages" to it }, exercise.loadNote?.let { "Charge" to it })
    if (notes.isEmpty() && exercise.photoUri == null) return
    SectionTitle("Mes repères")
    notes.forEach { (label, text) -> Line(label, text) }
    exercise.photoUri?.let { PhotoThumb(it, 200.dp, Modifier.padding(top = 8.dp)) }
}

@Composable
private fun Line(label: String, value: String) {
    if (value.isBlank()) return
    Text("$label : $value", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
}

@Composable
private fun MuscleChips(label: String, keys: List<String>) {
    if (keys.isEmpty()) return
    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.forEach { AssistChip(onClick = {}, label = { Text(LibraryLabels.muscle(it)) }) }
    }
}
