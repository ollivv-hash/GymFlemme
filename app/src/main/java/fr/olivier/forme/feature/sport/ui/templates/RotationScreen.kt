package fr.olivier.forme.feature.sport.ui.templates

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.sport.data.TemplateRepository
import fr.olivier.forme.feature.sport.data.WorkoutTemplateEntity
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import javax.inject.Inject

@HiltViewModel
class RotationViewModel @Inject constructor(private val repository: TemplateRepository) : ViewModel() {
    val templates = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val proposed = repository.observeProposed().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun save(orderedIds: List<Long>) = viewModelScope.launch { repository.saveRotation(orderedIds) }
}

@Composable
fun RotationScreen(onBack: () -> Unit, viewModel: RotationViewModel = hiltViewModel()) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val proposed by viewModel.proposed.collectAsStateWithLifecycle()
    val active = templates.filterNot { it.archived }
    // Local order while dragging; persisted on every change.
    var rotation by remember { mutableStateOf<List<WorkoutTemplateEntity>>(emptyList()) }
    LaunchedEffect(active) {
        rotation = active.filter { it.orderInRotation != null }.sortedBy { it.orderInRotation }
    }
    val outside = active.filter { t -> rotation.none { it.id == t.id } }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val f = rotation.indexOfFirst { it.id == from.key }
        val t = rotation.indexOfFirst { it.id == to.key }
        if (f >= 0 && t >= 0) {
            rotation = rotation.toMutableList().apply { add(t, removeAt(f)) }
            viewModel.save(rotation.map { it.id })
        }
    }

    Scaffold(topBar = { FormeTopBar("Rotation", onBack) }) { padding ->
        // One scrolling list for the whole screen: headers, reorderable rotation, then templates outside it.
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item(key = "proposed") {
                Text(
                    "Prochaine séance proposée : ${proposed?.title() ?: "aucune"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            item(key = "in-header") { SectionTitle("Dans la rotation (glisser ☰ pour l'ordre)") }
            items(rotation, key = { it.id }) { template ->
                ReorderableItem(reorderState, key = template.id) {
                    Card(Modifier.padding(vertical = 4.dp)) {
                        ListItem(
                            leadingContent = {
                                IconButton(onClick = {}, modifier = Modifier.draggableHandle()) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Déplacer")
                                }
                            },
                            headlineContent = { Text(template.title()) },
                            trailingContent = {
                                Switch(checked = true, onCheckedChange = {
                                    viewModel.save(rotation.map { it.id } - template.id)
                                })
                            },
                        )
                    }
                }
            }
            item(key = "out-header") { SectionTitle("Hors rotation") }
            items(outside, key = { "out-${it.id}" }) { template ->
                ListItem(
                    headlineContent = { Text(template.title()) },
                    trailingContent = {
                        Switch(checked = false, onCheckedChange = { viewModel.save(rotation.map { it.id } + template.id) })
                    },
                )
            }
        }
    }
}
