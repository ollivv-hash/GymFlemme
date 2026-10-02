package fr.olivier.forme.feature.sport.ui.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(private val repository: TemplateRepository) : ViewModel() {
    val templates = repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun setArchived(id: Long, archived: Boolean) = viewModelScope.launch { repository.setArchived(id, archived) }
}

fun WorkoutTemplateEntity.title() = if (code.isBlank()) name else "$code · $name"

@Composable
fun TemplatesScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDuplicate: (Long) -> Unit,
    onCreate: () -> Unit,
    onRotation: () -> Unit,
    viewModel: TemplatesViewModel = hiltViewModel(),
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { FormeTopBar("Modèles de séance", onBack) { TextButton(onClick = onRotation) { Text("Rotation") } } },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Modèle") })
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            templates.groupBy { it.archived }.toSortedMap().forEach { (archived, list) ->
                item(key = "h-$archived") {
                    SectionTitle(if (archived) "Archivés" else "Actifs", Modifier.padding(horizontal = 16.dp))
                }
                items(list, key = { it.id }) { template ->
                    ListItem(
                        modifier = Modifier.clickable { onEdit(template.id) },
                        headlineContent = { Text(template.title()) },
                        supportingContent = {
                            Text(template.orderInRotation?.let { "Rotation : position $it" } ?: "Hors rotation")
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { onDuplicate(template.id) }) {
                                    Icon(Icons.Filled.Share, contentDescription = "Dupliquer")
                                }
                                IconButton(onClick = { viewModel.setArchived(template.id, !template.archived) }) {
                                    Icon(
                                        if (template.archived) Icons.Filled.Refresh else Icons.Filled.Delete,
                                        contentDescription = if (template.archived) "Désarchiver" else "Archiver",
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
