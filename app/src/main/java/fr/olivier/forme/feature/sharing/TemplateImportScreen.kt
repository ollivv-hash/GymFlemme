package fr.olivier.forme.feature.sharing

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.R
import fr.olivier.forme.feature.sport.data.ExerciseDao
import fr.olivier.forme.feature.sport.data.ExerciseEntity
import fr.olivier.forme.feature.sport.data.TemplateExerciseEntity
import fr.olivier.forme.feature.sport.data.TemplateRepository
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.navigation.TemplateImportRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ImportState {
    data object Loading : ImportState
    data class Problem(val message: Int) : ImportState
    data class Preview(val file: SharedTemplateFile, val matches: List<ExerciseMatch>, val name: String) : ImportState
    data class Done(val templateId: Long, val name: String, val inRotation: Boolean = false) : ImportState
}

/** Reads a shared template (file or QR code) and imports it only when asked (V2.27): nothing is written before. */
@HiltViewModel
class TemplateImportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val templates: TemplateRepository,
    private val exercises: ExerciseDao,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<TemplateImportRoute>()
    private val _state = MutableStateFlow<ImportState>(ImportState.Loading)
    val state = _state.asStateFlow()
    private val _catalog = MutableStateFlow<List<ExerciseEntity>>(emptyList())
    val catalog = _catalog.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val result = when {
            route.payload != null -> TemplateShareCodec.fromQrPayload(route.payload)
            route.uri != null -> readFile(Uri.parse(route.uri))?.let(TemplateShareCodec::decode) ?: DecodeResult.Unreadable
            else -> DecodeResult.Unreadable
        }
        _state.value = when (result) {
            is DecodeResult.TooNew -> ImportState.Problem(R.string.import_too_new)
            DecodeResult.Unreadable -> ImportState.Problem(R.string.import_unreadable)
            is DecodeResult.Ok -> {
                val catalog = exercises.getAll()
                _catalog.value = catalog.filterNot { it.archived }.sortedBy { it.name.lowercase() }
                val names = templates.observeAll().first().map { it.name }
                ImportState.Preview(
                    result.file,
                    TemplateImport.match(result.file.template.exercises, catalog),
                    TemplateImport.importName(result.file.template.name, result.file.author, names),
                )
            }
        }
    }

    /** At most 500 kB: a template is a few kB. */
    private suspend fun readFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > 500_000) return@use null
                }
                out.toByteArray().decodeToString()
            }
        }.getOrNull()
    }

    fun rename(name: String) = _state.update { (it as? ImportState.Preview)?.copy(name = name.take(60)) ?: it }

    /** Another exercise of the catalogue for this line, or null: create the file's one. */
    fun choose(index: Int, exercise: ExerciseEntity?) = _state.update { s ->
        if (s !is ImportState.Preview) return@update s
        s.copy(
            matches = s.matches.mapIndexed { i, m ->
                if (i != index) m else m.copy(exercise = exercise, kind = if (exercise == null) MatchKind.NEW else MatchKind.CHOSEN)
            },
        )
    }

    fun import() = viewModelScope.launch {
        val s = _state.value as? ImportState.Preview ?: return@launch
        val name = s.name.trim().ifEmpty { s.file.template.name }
        val lines = s.matches.map { m ->
            val e = m.shared
            TemplateExerciseEntity(
                templateId = 0,
                exerciseId = 0,
                position = e.position,
                targetSets = e.targetSets,
                targetRepsMin = e.targetRepsMin,
                targetRepsMax = e.targetRepsMax,
                targetDurationSec = e.targetDurationSec,
                optional = e.optional,
                restSec = e.restSec,
                supersetGroup = e.supersetGroup,
            ) to (m.exercise ?: TemplateImport.newExercise(e))
        }
        val code = s.file.template.code?.trim()?.takeIf { it.isNotEmpty() }?.take(4) ?: name.take(1).uppercase()
        val id = templates.importTemplate(name, code, lines)
        _state.value = ImportState.Done(id, name)
    }

    fun addToRotation() = viewModelScope.launch {
        val s = _state.value as? ImportState.Done ?: return@launch
        templates.addToRotation(s.templateId)
        _state.value = s.copy(inRotation = true)
    }
}

const val IMPORT_BUTTON_TAG = "import_template"

/** Preview of a shared template (V2.27): its exercises, recognised or to create, before "Importer". */
@Composable
fun TemplateImportScreen(onDone: () -> Unit, viewModel: TemplateImportViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    Scaffold(topBar = { FormeTopBar(stringResource(R.string.import_title), onDone) }) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                ImportState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is ImportState.Problem -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(s.message), style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = onDone) { Text(stringResource(R.string.import_close)) }
                }
                is ImportState.Preview -> Preview(s, catalog, viewModel)
                is ImportState.Done -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.import_done, s.name), style = MaterialTheme.typography.titleMedium)
                    if (s.inRotation) {
                        Text(stringResource(R.string.import_in_rotation))
                    } else {
                        Text(stringResource(R.string.import_not_in_rotation), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = viewModel::addToRotation) { Text(stringResource(R.string.import_add_rotation)) }
                    }
                    OutlinedButton(onClick = onDone) { Text(stringResource(R.string.import_finish)) }
                }
            }
        }
    }
}

@Composable
private fun Preview(s: ImportState.Preview, catalog: List<ExerciseEntity>, viewModel: TemplateImportViewModel) {
    val recognised = s.matches.count { it.exercise != null }
    val toCreate = s.matches.filter { it.exercise == null }.map { it.shared.name }
    LazyColumn {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = s.name,
                    onValueChange = viewModel::rename,
                    label = { Text(stringResource(R.string.import_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                s.file.author?.let { Text(stringResource(R.string.import_author, it), style = MaterialTheme.typography.bodyMedium) }
                val summary = pluralStringResource(R.plurals.import_recognised, recognised, recognised) +
                    if (toCreate.isEmpty()) "" else ", " + pluralStringResource(R.plurals.import_to_create, toCreate.size, toCreate.size, toCreate.joinToString(", "))
                Text(summary, style = MaterialTheme.typography.titleSmall)
            }
        }
        itemsIndexed(s.matches) { index, m ->
            var menu by remember { mutableStateOf(false) }
            val e = m.shared
            val target = when {
                e.targetDurationSec != null -> "${e.targetSets} × ${Fmt.mmss(e.targetDurationSec)}"
                e.targetRepsMin != null -> "${e.targetSets} × ${e.targetRepsMin}${e.targetRepsMax?.takeIf { it != e.targetRepsMin }?.let { "–$it" } ?: ""}"
                else -> "${e.targetSets} séries"
            }
            ListItem(
                headlineContent = { Text(e.name) },
                supportingContent = {
                    Text(
                        listOfNotNull(
                            target,
                            e.restSec?.let { "repos ${Fmt.mmss(it)}" },
                            stringResource(R.string.import_optional).takeIf { e.optional },
                            m.exercise?.let { stringResource(R.string.import_uses, it.name) } ?: stringResource(R.string.import_new),
                        ).joinToString(" · "),
                    )
                },
                trailingContent = {
                    Box {
                        TextButton(onClick = { menu = true }) { Text(stringResource(R.string.import_change)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.import_create_it, e.name)) }, onClick = {
                                viewModel.choose(index, null)
                                menu = false
                            })
                            catalog.forEach { c ->
                                DropdownMenuItem(text = { Text(c.name) }, onClick = {
                                    viewModel.choose(index, c)
                                    menu = false
                                })
                            }
                        }
                    }
                },
            )
        }
        item {
            Row(Modifier.padding(16.dp)) {
                Button(onClick = viewModel::import, modifier = Modifier.fillMaxWidth().testTag(IMPORT_BUTTON_TAG)) {
                    Text(stringResource(R.string.import_button))
                }
            }
        }
    }
}
