package fr.olivier.forme.feature.nutrition.ui

import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.R
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import fr.olivier.forme.feature.nutrition.domain.DishItem
import fr.olivier.forme.feature.nutrition.domain.DishList
import fr.olivier.forme.feature.nutrition.domain.DishSort
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
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
    private val settings: fr.olivier.forme.core.settings.SettingsRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<AddFoodRoute>()
    val date: LocalDate = LocalDate.ofEpochDay(route.epochDay)
    val meal: MealType = MealType.valueOf(route.meal)

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    private val allResults = _query.debounce(250).mapLatest { repository.search(it) }

    /** Aliments (V2.26): no dish, except those matching the search, shown apart at the top ([dishResults]). */
    val results = allResults.map { list -> list.filterNot { it.isDish() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dishResults = allResults.map { list -> list.filter { it.isDish() }.map { (it as FoodChoice.Mine).food } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = repository.favorites().map { it.filterNot(FoodEntity::isDish) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recents = repository.recents().map { it.filterNot(FoodEntity::isDish) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Mes plats (V2.26), the tab opened first. */
    private val _tab = MutableStateFlow(AddFoodTab.DISHES)
    val tab = _tab.asStateFlow()
    private val _dishQuery = MutableStateFlow("")
    val dishQuery = _dishQuery.asStateFlow()
    val dishSort = settings.dishSort.stateIn(viewModelScope, SharingStarted.Eagerly, DishSort.MOST_USED)
    val dishes = combine(repository.dishes(), _dishQuery, settings.dishSort) { list, q, sort ->
        DishList.arrange(list, sort, q) { DishItem(it.food.id, it.food.name, it.food.favorite, it.useCount, it.food.lastUsedAt) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setTab(tab: AddFoodTab) {
        _tab.value = tab
    }

    fun setDishQuery(text: String) {
        _dishQuery.value = text
    }

    fun setDishSort(sort: DishSort) = viewModelScope.launch { settings.setDishSort(sort) }

    /** Long press or the pin: a pinned dish is a favourite (one marker for both). */
    fun togglePin(food: FoodEntity) = viewModelScope.launch { repository.setFavorite(food, !food.favorite) }

    /** One tap: one portion of the dish, at once; "Annuler" in the message. */
    fun quickAdd(food: FoodEntity) = viewModelScope.launch {
        val grams = food.defaultPortionG ?: 100.0
        val entryId = repository.addFood(food, date, meal, grams)
        runCatching { badges.evaluate() }
        _added.send(Added("${food.name} ajouté à ${meal.label.lowercase()}", entryId, food))
    }

    fun undo(added: Added) = viewModelScope.launch { repository.undoAdd(added.entryId, added.food) }

    /** A dish added in one tap, for "Annuler". */
    data class Added(val text: String, val entryId: Long, val food: FoodEntity)

    private val _added = Channel<Added>(Channel.BUFFERED)
    val added = _added.receiveAsFlow()

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

    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val dishResults by viewModel.dishResults.collectAsStateWithLifecycle()
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    LaunchedEffect(Unit) {
        viewModel.added.collect { added ->
            val result = snackbar.showSnackbar(added.text, actionLabel = resources.getString(R.string.dishes_undo), duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undo(added)
        }
    }

    Scaffold(
        topBar = { FormeTopBar(viewModel.meal.label, onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            // V2.26: Mes plats (first), Aliments, Scanner.
            TabRow(selectedTabIndex = tab.ordinal) {
                Tab(selected = tab == AddFoodTab.DISHES, onClick = { viewModel.setTab(AddFoodTab.DISHES) }, text = { Text(stringResource(R.string.tab_my_dishes)) })
                Tab(selected = tab == AddFoodTab.FOODS, onClick = { viewModel.setTab(AddFoodTab.FOODS) }, text = { Text(stringResource(R.string.tab_foods)) })
                Tab(selected = false, onClick = { onScan(viewModel.date, viewModel.meal) }, text = { Text(stringResource(R.string.tab_scanner)) })
            }
            if (tab == AddFoodTab.DISHES) {
                DishesTab(viewModel, onCreateDish, onEdit = { viewModel.select(it) })
                return@Column
            }
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                label = { Text("Rechercher un aliment (Ciqual, mes aliments)") },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Filled.Clear, "Effacer") }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyColumn {
                if (query.trim().length >= 2) {
                    // A dish whose name matches: apart, at the top.
                    if (dishResults.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.tab_my_dishes), Modifier.padding(horizontal = 16.dp)) }
                        items(dishResults, key = { "d${it.id}" }) { food ->
                            FoodRow(food.name, food.brand, food.per100(), favorite = food.favorite) { viewModel.select(food) }
                        }
                        item { SectionTitle(stringResource(R.string.tab_foods), Modifier.padding(horizontal = 16.dp)) }
                    }
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


/** The tabs of Ajouter (V2.26): Scanner opens the scanner. */
enum class AddFoodTab { DISHES, FOODS }

private fun FoodEntity.isDish() = source == fr.olivier.forme.feature.nutrition.domain.FoodSource.DISH
private fun FoodChoice.isDish() = (this as? FoodChoice.Mine)?.food?.isDish() == true

const val DISH_ROW_TAG = "dish_row"

/**
 * Mes plats (V2.26): search among one's dishes, pinned ones (= favourites) first, then most used / recent / A → Z.
 * A tap adds one portion at once ("Annuler" in the message), the pencil opens the quantity, a long press pins.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DishesTab(viewModel: AddFoodViewModel, onCreateDish: () -> Unit, onEdit: (FoodEntity) -> Unit) {
    val query by viewModel.dishQuery.collectAsStateWithLifecycle()
    val sort by viewModel.dishSort.collectAsStateWithLifecycle()
    val dishes by viewModel.dishes.collectAsStateWithLifecycle()
    OutlinedTextField(
        value = query,
        onValueChange = viewModel::setDishQuery,
        label = { Text(stringResource(R.string.dishes_search)) },
        singleLine = true,
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { viewModel.setDishQuery("") }) { Icon(Icons.Filled.Clear, "Effacer") }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp),
    ) {
        listOf(DishSort.MOST_USED to R.string.dishes_sort_used, DishSort.RECENT to R.string.dishes_sort_recent, DishSort.ALPHABETICAL to R.string.dishes_sort_alpha)
            .forEach { (s, label) -> FilterChip(selected = sort == s, onClick = { viewModel.setDishSort(s) }, label = { Text(stringResource(label)) }) }
    }
    val list = dishes ?: return
    LazyColumn {
        if (list.isEmpty()) {
            item {
                Text(
                    stringResource(if (query.isBlank()) R.string.dishes_empty else R.string.dishes_none_found),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        items(list, key = { "dish${it.food.id}" }) { dish ->
            val food = dish.food
            val portion = food.defaultPortionG ?: 100.0
            val m = NutritionMath.forQuantity(food.per100(), portion)
            val pinLabel = stringResource(if (food.favorite) R.string.dishes_unpin else R.string.dishes_pin)
            ListItem(
                headlineContent = { Text(food.name) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.dishes_portion,
                            m.kcal?.let { Fmt.number(it, 0) } ?: "—",
                            Fmt.number(m.proteinG, 0),
                            Fmt.number(portion, 0),
                        ),
                    )
                },
                leadingContent = {
                    IconButton(onClick = { viewModel.togglePin(food) }) {
                        Icon(
                            painterResource(if (food.favorite) R.drawable.ic_pin_filled else R.drawable.ic_pin),
                            contentDescription = pinLabel,
                            tint = if (food.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                trailingContent = {
                    IconButton(onClick = { onEdit(food) }) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = stringResource(R.string.dishes_quantity, food.name))
                    }
                },
                modifier = Modifier
                    .testTag(DISH_ROW_TAG)
                    .combinedClickable(
                        onClickLabel = stringResource(R.string.dishes_add_one),
                        onLongClickLabel = pinLabel,
                        onLongClick = { viewModel.togglePin(food) },
                        onClick = { viewModel.quickAdd(food) },
                    ),
            )
        }
        item {
            TextButton(onClick = onCreateDish, modifier = Modifier.padding(8.dp)) { Text(stringResource(R.string.dishes_create)) }
        }
    }
}
