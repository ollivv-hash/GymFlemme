package fr.olivier.forme.feature.nutrition.ui

import fr.olivier.forme.ui.components.ConfirmDialog
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import fr.olivier.forme.feature.badges.data.BadgeRepository
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import fr.olivier.forme.feature.nutrition.data.FoodChoice
import fr.olivier.forme.feature.nutrition.data.FoodEntity
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.per100
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.Per100
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.navigation.AddFoodRoute
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddFoodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NutritionRepository,
    private val badges: BadgeRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<AddFoodRoute>()
    val date: LocalDate = LocalDate.ofEpochDay(route.epochDay)
    val meal: MealType = MealType.valueOf(route.meal)

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    val results = _query.debounce(250).mapLatest { repository.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = repository.favorites().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recents = repository.recents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selected = MutableStateFlow<FoodEntity?>(null)
    val selected = _selected.asStateFlow()
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun setQuery(text: String) {
        _query.value = text
    }

    fun select(choice: FoodChoice) = viewModelScope.launch { _selected.value = repository.resolve(choice) }
    fun select(food: FoodEntity) {
        _selected.value = food
    }
    fun dismiss() {
        _selected.value = null
    }

    /** Same as Config → Mes aliments: meals already entered keep their values. */
    fun delete(food: FoodEntity) = viewModelScope.launch {
        repository.deleteFood(food.id)
        _selected.value = null
        _messages.send("« ${food.name} » supprimé")
    }

    fun toggleFavorite() = viewModelScope.launch {
        val food = _selected.value ?: return@launch
        val updated = food.copy(favorite = !food.favorite)
        repository.setFavorite(food, updated.favorite)
        _selected.value = updated
    }

    fun add(grams: Double) = viewModelScope.launch {
        val food = _selected.value ?: return@launch
        repository.addFood(food, date, meal, grams)
        runCatching { badges.evaluate() } // V2.18: Shaker d'or, announced at once
        _selected.value = null
        _messages.send("${food.name} ajouté à ${meal.label.lowercase()}")
    }
}

@Composable
fun AddFoodScreen(
    onBack: () -> Unit,
    onScan: (LocalDate, MealType) -> Unit,
    onCreateFood: () -> Unit,
    onCreateDish: () -> Unit = {},
    onEditFood: (FoodEntity) -> Unit = {},
    viewModel: AddFoodViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<FoodEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            FormeTopBar(viewModel.meal.label, onBack) {
                TextButton(onClick = { onScan(viewModel.date, viewModel.meal) }) { Text("Scanner") }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text("Rechercher un aliment (Ciqual, mes aliments)") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Filled.Clear, "Effacer") }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyColumn {
                if (query.trim().length >= 2) {
                    if (results.isEmpty()) item { Text("Aucun résultat.", Modifier.padding(16.dp)) }
                    items(results, key = { it.key() }) { choice ->
                        FoodRow(choice.name, choice.detail, choice.per100, favorite = (choice as? FoodChoice.Mine)?.food?.favorite == true) {
                            viewModel.select(choice)
                        }
                    }
                } else {
                    if (favorites.isNotEmpty()) {
                        item { SectionTitle("Favoris", Modifier.padding(horizontal = 16.dp)) }
                        items(favorites, key = { "f${it.id}" }) { food ->
                            FoodRow(food.name, food.brand, food.per100(), favorite = true) { viewModel.select(food) }
                        }
                    }
                    item { SectionTitle("Récents", Modifier.padding(horizontal = 16.dp)) }
                    if (recents.isEmpty()) item { Text("Tape au moins 2 lettres pour chercher.", Modifier.padding(horizontal = 16.dp)) }
                    items(recents, key = { "r${it.id}" }) { food ->
                        FoodRow(food.name, food.brand, food.per100(), favorite = food.favorite) { viewModel.select(food) }
                    }
                }
                item {
                    Row(Modifier.padding(8.dp)) {
                        TextButton(onClick = onCreateFood) { Text("Créer un aliment perso") }
                        TextButton(onClick = onCreateDish) { Text("Créer un plat") }
                    }
                }
            }
        }
    }

    selected?.let { food ->
        QuantityDialog(
            title = food.name,
            per100 = food.per100(),
            initialG = food.defaultPortionG ?: 100.0,
            portions = listOfNotNull(food.defaultPortionG, 50.0, 100.0, 150.0, 200.0),
            confirmLabel = "Ajouter",
            onConfirm = viewModel::add,
            onDismiss = viewModel::dismiss,
            extraAction = {
                Column {
                    TextButton(onClick = viewModel::toggleFavorite) {
                        Text(if (food.favorite) "★ Retirer des favoris" else "☆ Ajouter aux favoris")
                    }
                    // V2.19.2: the same actions as Config → Mes aliments.
                    Row {
                        TextButton(onClick = {
                            viewModel.dismiss()
                            onEditFood(food) // a dish opens its ingredients (V2.21)
                        }) { Text("Modifier") }
                        TextButton(onClick = {
                            viewModel.dismiss()
                            deleting = food
                        }) { Text("Supprimer") }
                    }
                }
            },
        )
    }
    deleting?.let { food ->
        ConfirmDialog(
            title = "Supprimer « ${food.name} » ?",
            text = "Les repas déjà saisis gardent leurs valeurs.",
            confirmLabel = "Supprimer",
            onConfirm = {
                viewModel.delete(food)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

private fun FoodChoice.key() = when (this) {
    is FoodChoice.Mine -> "m${food.id}"
    is FoodChoice.Ciqual -> "c${food.code}"
}

@Composable
fun FoodRow(name: String, detail: String?, per100: Per100?, favorite: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(name) },
        supportingContent = {
            Text(
                listOfNotNull(
                    detail,
                    per100?.let { macrosLine(it.proteinG, it.kcal, null, null) + " /100 g" } ?: "protéines inconnues",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        trailingContent = if (favorite) {
            { Icon(Icons.Filled.Star, "Favori", tint = MaterialTheme.colorScheme.tertiary) }
        } else {
            null
        },
    )
}

