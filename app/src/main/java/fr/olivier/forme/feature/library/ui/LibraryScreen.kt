package fr.olivier.forme.feature.library.ui

import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.core.humor.QuipText
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import fr.olivier.forme.feature.library.data.LibraryExerciseEntity
import fr.olivier.forme.feature.library.data.LibraryRepository
import fr.olivier.forme.feature.library.domain.LibraryCatalog
import fr.olivier.forme.feature.library.domain.LibraryFilter
import fr.olivier.forme.feature.library.domain.LibraryLabels
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.ExerciseRepository
import fr.olivier.forme.ui.components.FormeTopBar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    library: LibraryRepository,
    private val exercises: ExerciseRepository,
) : ViewModel() {
    val entries = library.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Catalogue exercises (archived included) by the library entry they illustrate. */
    val inCatalogue = exercises.observeAll()
        .map { list -> list.filter { it.libraryId != null }.groupBy { it.libraryId!! }.mapValues { (_, v) -> v.first() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Creates the catalogue exercise, then hands its id to [then] (snackbar "Modifier"). */
    fun add(entry: LibraryExerciseEntity, then: (Long) -> Unit) = viewModelScope.launch {
        if (inCatalogue.value.containsKey(entry.id)) return@launch
        then(exercises.save(LibraryCatalog.newExercise(entry)))
    }
}

/**
 * Config → Bibliothèque (V2.10): the whole Open Exercise Illustrations library, searched by name or alias and
 * filtered by main muscle, equipment and place; an entry opens its sheet, "+" copies it into the catalogue.
 */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onSheet: (String) -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val all by viewModel.entries.collectAsStateWithLifecycle()
    val inCatalogue by viewModel.inCatalogue.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf<String?>(null) }
    var equipment by rememberSaveable { mutableStateOf<String?>(null) }
    var place by rememberSaveable { mutableStateOf<String?>(null) }
    val filter = LibraryFilter(query, muscle, equipment, place)
    val shown = remember(all, filter) { LibraryCatalog.filter(all, filter) }
    val muscles = remember(all) { LibraryCatalog.muscles(all) }
    val equipmentKeys = remember(all) { LibraryCatalog.equipment(all) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { FormeTopBar("Bibliothèque d'exercices", onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Rechercher (nom anglais ou alias)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            ) {
                FilterMenu("Muscle", muscle, muscles, LibraryLabels::muscle) { muscle = it }
                FilterMenu("Matériel", equipment, equipmentKeys, LibraryCatalog::equipmentLabel) { equipment = it }
                FilterMenu("Lieu", place, LibraryLabels.places.keys.toList(), { LibraryLabels.places[it] ?: it }) { place = it }
                if (filter.active) {
                    TextButton(onClick = { muscle = null; equipment = null; place = null }) { Text("Effacer") }
                }
            }
            if (all.isEmpty()) {
                // V2.15: the former start screen's lines, while the library loads (first launch).
                QuipText(Quip.LOADING, modifier = Modifier.padding(16.dp))
            } else {
                Text(
                    "${shown.size} exercice(s)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LazyColumn {
                items(shown, key = { it.id }) { entry ->
                    LibraryCatalogueRow(
                        entry = entry,
                        inCatalogue = inCatalogue[entry.id],
                        onOpen = { onSheet(entry.id) },
                        onAdd = {
                            viewModel.add(entry) { id ->
                                scope.launch {
                                    val result = snackbar.showSnackbar("« ${entry.name} » ajouté à ton catalogue", actionLabel = "Modifier")
                                    if (result == SnackbarResult.ActionPerformed) onEdit(id)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** A filter chip opening the list of its values; "Tous" clears it. */
@Composable
private fun FilterMenu(title: String, selected: String?, keys: List<String>, label: (String) -> String, onSelect: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { open = true },
            label = { Text(selected?.let(label) ?: title) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Tous") }, onClick = { onSelect(null); open = false })
            keys.forEach { key ->
                DropdownMenuItem(text = { Text(label(key)) }, onClick = { onSelect(key); open = false })
            }
        }
    }
}

@Composable
private fun LibraryCatalogueRow(entry: LibraryExerciseEntity, inCatalogue: ExerciseEntity?, onOpen: () -> Unit, onAdd: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        leadingContent = { LibraryThumb(entry, 56.dp) },
        headlineContent = { Text(entry.name) },
        supportingContent = {
            Column {
                Text(
                    listOfNotNull(
                        entry.primaryList.joinToString(", ") { LibraryLabels.muscle(it) },
                        LibraryLabels.equipmentOf(entry),
                        entry.imageOf?.let { "variante" },
                    ).joinToString(" · "),
                )
                if (inCatalogue != null) {
                    val name = inCatalogue.name.takeIf { it != entry.name }?.let { " (« $it »)" }.orEmpty()
                    val archived = if (inCatalogue.archived) ", archivé" else ""
                    Text("Déjà dans mon catalogue$name$archived", color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailingContent = {
            if (inCatalogue != null) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Déjà dans mon catalogue", tint = MaterialTheme.colorScheme.primary)
            } else {
                IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Ajouter à mon catalogue") }
            }
        },
    )
}
