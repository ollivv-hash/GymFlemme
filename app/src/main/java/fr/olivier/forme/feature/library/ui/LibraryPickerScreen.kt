package fr.olivier.forme.feature.library.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.library.data.LibraryExerciseEntity
import fr.olivier.forme.feature.library.data.LibraryRepository
import fr.olivier.forme.feature.library.domain.LibraryLabels
import fr.olivier.forme.ui.components.FormeTopBar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryPickerViewModel @Inject constructor(repository: LibraryRepository) : ViewModel() {
    val entries = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

/** Choice of a library illustration for an exercise: search by name or alias, (i) to preview the sheet. */
@Composable
fun LibraryPickerScreen(
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    onPreview: (String) -> Unit,
    viewModel: LibraryPickerViewModel = hiltViewModel(),
) {
    val all by viewModel.entries.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(all, query) { LibraryRepository.search(all, query) }
    Scaffold(topBar = { FormeTopBar("Choisir une illustration", onBack) }) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Rechercher (nom anglais ou alias)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Text(
                "${shown.size} exercice(s)",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn {
                items(shown, key = { it.id }) { entry -> LibraryRow(entry, onClick = { onPick(entry.id) }, onInfo = { onPreview(entry.id) }) }
            }
        }
    }
}

@Composable
fun LibraryRow(entry: LibraryExerciseEntity, onClick: () -> Unit, onInfo: () -> Unit, trailingNote: String? = null) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { LibraryThumb(entry, 56.dp) },
        headlineContent = { Text(entry.name) },
        supportingContent = {
            Text(
                listOfNotNull(
                    entry.primaryList.joinToString(", ") { LibraryLabels.muscle(it) },
                    LibraryLabels.equipmentOf(entry),
                    entry.imageOf?.let { "variante" },
                    trailingNote,
                ).joinToString(" · "),
            )
        },
        trailingContent = { IconButton(onClick = onInfo) { Icon(Icons.Filled.Info, contentDescription = "Fiche") } },
    )
}
