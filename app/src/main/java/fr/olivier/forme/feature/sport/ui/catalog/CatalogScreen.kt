package fr.olivier.forme.feature.sport.ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.ExerciseRepository
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.library.data.LibraryExerciseEntity
import fr.olivier.forme.feature.library.data.LibraryRepository
import fr.olivier.forme.feature.library.ui.LibraryThumb
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.PhotoThumb
import fr.olivier.forme.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(private val repository: ExerciseRepository, library: LibraryRepository) : ViewModel() {
    val exercises = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val library = library.observeAll().map { list -> list.associateBy { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    fun setArchived(id: Long, archived: Boolean) = viewModelScope.launch { repository.setArchived(id, archived) }
}

@Composable
fun CatalogScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onHistory: (Long) -> Unit,
    onSheet: (Long) -> Unit = {},
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    var showArchived by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        topBar = {
            FormeTopBar("Catalogue", onBack) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    Text("Archivés ")
                    Switch(checked = showArchived, onCheckedChange = { showArchived = it })
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                onClick = { onEdit(0) },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Exercice") },
            )
        },
    ) { padding ->
        val visible = exercises.filter { showArchived || !it.archived }
        LazyColumn(Modifier.padding(padding)) {
            visible.groupBy { if (it.archived) null else it.muscleGroup }.forEach { (group, list) ->
                item(key = "h-${group?.name ?: "archived"}") {
                    SectionTitle(group?.label ?: "Archivés", Modifier.padding(horizontal = 16.dp))
                }
                items(list, key = { it.id }) { exercise ->
                    ExerciseRow(
                        exercise,
                        illustration = exercise.libraryId?.let { library[it] },
                        onSheet = { onSheet(exercise.id) },
                        onClick = { onEdit(exercise.id) },
                        onHistory = { onHistory(exercise.id) },
                        onToggleArchive = { viewModel.setArchived(exercise.id, !exercise.archived) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: ExerciseEntity,
    illustration: LibraryExerciseEntity?,
    onSheet: () -> Unit,
    onClick: () -> Unit,
    onHistory: () -> Unit,
    onToggleArchive: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        // Library illustration (opens the sheet), else the machine photo.
        leadingContent = when {
            illustration != null -> { { LibraryThumb(illustration, 48.dp, Modifier.clickable(onClick = onSheet)) } }
            exercise.photoUri != null -> { { PhotoThumb(exercise.photoUri, 48.dp) } }
            else -> null
        },
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            Text(
                listOfNotNull(
                    exercise.type.label,
                    "pas ${Fmt.kg(exercise.loadIncrementKg)}".takeIf { exercise.type.name == "WEIGHTED" },
                    exercise.settingsNote,
                    "sans illustration".takeIf { illustration == null && !exercise.archived },
                ).joinToString(" · "),
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onHistory) { Icon(Icons.Filled.DateRange, contentDescription = "Historique") }
                IconButton(onClick = onToggleArchive) {
                    Icon(
                        if (exercise.archived) Icons.Filled.Refresh else Icons.Filled.Delete,
                        contentDescription = if (exercise.archived) "Désarchiver" else "Archiver",
                    )
                }
            }
        },
    )
}
