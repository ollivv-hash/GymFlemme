package fr.olivier.forme.feature.nutrition.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
import fr.olivier.forme.feature.nutrition.domain.DishIngredient
import fr.olivier.forme.feature.nutrition.domain.DishMath
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.components.Stepper
import fr.olivier.forme.ui.components.parseDecimal
import fr.olivier.forme.ui.navigation.DishEditRoute
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class DishForm(
    val name: String = "",
    val ingredients: List<DishIngredient> = emptyList(),
    /** Cooked weight as typed (decimal comma allowed); empty = the raw ingredients. */
    val cooked: String = "",
    val servings: Int = 1,
) {
    val cookedWeightG: Double? get() = parseDecimal(cooked)?.takeIf { it > 0 }
    val canSave: Boolean get() = name.isNotBlank() && ingredients.isNotEmpty()
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DishEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NutritionRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<DishEditRoute>()
    val isNew = route.foodId == 0L
    private val _form = MutableStateFlow(DishForm())
    val form = _form.asStateFlow()
    private val _loaded = MutableStateFlow(isNew && route.meal.isEmpty())
    val loaded = _loaded.asStateFlow()

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()
    val results = _query.debounce(250).mapLatest { q -> repository.search(q).filterNot { it.isThisDish() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recents = repository.recents().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The food chosen in the picker, waiting for its grams. */
    private val _picked = MutableStateFlow<FoodEntity?>(null)
    val picked = _picked.asStateFlow()

    init {
        viewModelScope.launch {
            when {
                !isNew -> repository.dish(route.foodId)?.let { d ->
                    _form.value = DishForm(d.food.name, d.ingredients, d.dish.cookedWeightG?.let { Fmt.number(it, 0) }.orEmpty(), d.dish.servings)
                }
                // "Enregistrer comme plat" from a meal of the journal.
                route.meal.isNotEmpty() -> _form.value = DishForm(
                    ingredients = repository.mealAsIngredients(LocalDate.ofEpochDay(route.epochDay), MealType.valueOf(route.meal)),
                )
            }
            _loaded.value = true
        }
    }

    private fun FoodChoice.isThisDish() = this is FoodChoice.Mine && food.id == route.foodId

    fun update(transform: (DishForm) -> DishForm) = _form.update(transform)
    fun setQuery(text: String) {
        _query.value = text
    }

    fun pick(choice: FoodChoice) = viewModelScope.launch { _picked.value = repository.resolve(choice) }
    fun pick(food: FoodEntity) {
        _picked.value = food
    }
    fun dismissPicked() {
        _picked.value = null
    }

    fun addIngredient(food: FoodEntity, grams: Double) {
        _form.update { it.copy(ingredients = it.ingredients + DishIngredient(food.name, grams, food.per100())) }
        _picked.value = null
        _query.value = ""
    }

    fun setGrams(index: Int, grams: Double) = _form.update { f ->
        f.copy(ingredients = f.ingredients.mapIndexed { i, it -> if (i == index) it.copy(grams = grams) else it })
    }

    fun remove(index: Int) = _form.update { f -> f.copy(ingredients = f.ingredients.filterIndexed { i, _ -> i != index }) }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val f = _form.value
        if (!f.canSave) return@launch
        repository.saveDish(route.foodId, f.name, f.ingredients, f.cookedWeightG, f.servings)
        onDone()
    }
}

const val ADD_INGREDIENT_TAG = "add_ingredient"

/**
 * A dish of one's own (V2.21): name, ingredients with their grams, optional cooked weight and servings, with its
 * values computed live. Its ingredients are picked among the known foods (Ciqual, scanned, own) in the same
 * window; the dish is then added to a meal like any food, one serving proposed.
 */
@Composable
fun DishEditScreen(onDone: () -> Unit, viewModel: DishEditViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val picked by viewModel.picked.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Int?>(null) }
    BackHandler(enabled = picking) { picking = false }

    Scaffold(
        topBar = {
            FormeTopBar(if (picking) "Ajouter un ingrédient" else if (viewModel.isNew) "Nouveau plat" else "Modifier le plat", onBack = {
                if (picking) picking = false else onDone()
            }) {
                if (!picking) TextButton(enabled = form.canSave, onClick = { viewModel.save(onDone) }) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        if (picking) {
            IngredientPicker(viewModel, Modifier.padding(padding))
        } else {
            DishFormContent(form, viewModel, onAdd = { picking = true }, onEdit = { editing = it }, modifier = Modifier.padding(padding))
        }
    }

    picked?.let { food ->
        QuantityDialog(
            title = food.name,
            per100 = food.per100(),
            initialG = food.defaultPortionG ?: 100.0,
            portions = listOfNotNull(food.defaultPortionG, 50.0, 100.0, 150.0, 200.0),
            confirmLabel = "Ajouter",
            onConfirm = {
                viewModel.addIngredient(food, it)
                picking = false
            },
            onDismiss = viewModel::dismissPicked,
        )
    }
    editing?.let { index ->
        val ingredient = form.ingredients.getOrNull(index) ?: return@let
        QuantityDialog(
            title = ingredient.name,
            per100 = ingredient.per100,
            initialG = ingredient.grams,
            portions = listOf(50.0, 100.0, 150.0, 200.0),
            confirmLabel = "Modifier",
            onConfirm = {
                viewModel.setGrams(index, it)
                editing = null
            },
            onDismiss = { editing = null },
            extraAction = {
                TextButton(onClick = {
                    viewModel.remove(index)
                    editing = null
                }) { Text("Retirer du plat") }
            },
        )
    }
}

@Composable
private fun DishFormContent(
    form: DishForm,
    viewModel: DishEditViewModel,
    onAdd: () -> Unit,
    onEdit: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier.padding(horizontal = 16.dp)) {
        item {
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text("Nom du plat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { DishTotals(form) }
        item {
            OutlinedTextField(
                value = form.cooked,
                onValueChange = { v -> viewModel.update { it.copy(cooked = v) } },
                label = { Text("Poids total cuit (g, facultatif)") },
                supportingText = { Text("Si tu pèses ta part cuite : pâtes, riz… Sinon, le poids des ingrédients.") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        item {
            Stepper(
                label = "Nombre de portions",
                valueText = form.servings.toString(),
                onMinus = { viewModel.update { it.copy(servings = (it.servings - 1).coerceAtLeast(1)) } },
                onPlus = { viewModel.update { it.copy(servings = it.servings + 1) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        item { SectionTitle("Ingrédients") }
        if (form.ingredients.isEmpty()) item { Text("Aucun ingrédient pour l'instant.", style = MaterialTheme.typography.bodyMedium) }
        itemsIndexed(form.ingredients) { index, ingredient ->
            val m = NutritionMath.forQuantity(ingredient.per100, ingredient.grams)
            ListItem(
                modifier = Modifier.clickable { onEdit(index) },
                headlineContent = { Text(ingredient.name) },
                supportingContent = { Text("${Fmt.number(ingredient.grams, 0)} g · ${macrosLine(m.proteinG, m.kcal, m.carbsG, m.fatG)}") },
            )
        }
        item {
            OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag(ADD_INGREDIENT_TAG)) {
                Icon(Icons.Filled.Add, null)
                Text("Ajouter un ingrédient", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun DishTotals(form: DishForm) {
    val total = DishMath.totals(form.ingredients)
    val weight = DishMath.weight(form.ingredients, form.cookedWeightG)
    val per100 = DishMath.per100(form.ingredients, form.cookedWeightG)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Plat entier : ${Fmt.number(weight, 0)} g${if (form.cookedWeightG != null) " cuit" else ""}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(macrosLine(total.proteinG, total.kcal, total.carbsG, total.fatG), style = MaterialTheme.typography.bodyMedium)
            per100?.let { Text("Pour 100 g : " + macrosLine(it.proteinG, it.kcal, it.carbsG, it.fatG), style = MaterialTheme.typography.bodySmall) }
            if (form.servings > 1 && weight > 0) {
                val portion = DishMath.portion(form.ingredients, form.cookedWeightG, form.servings)
                val m = NutritionMath.forQuantity(per100!!, portion)
                Text(
                    "Une portion (${form.servings} en tout) : ${Fmt.number(portion, 0)} g · ${macrosLine(m.proteinG, m.kcal, null, null)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun IngredientPicker(viewModel: DishEditViewModel, modifier: Modifier = Modifier) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    Column(modifier) {
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
        Text(
            "Un produit scanné une fois est dans tes aliments : cherche-le ici.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn {
            if (query.trim().length >= 2) {
                if (results.isEmpty()) item { Text("Aucun résultat.", Modifier.padding(16.dp)) }
                items(results, key = { it.pickerKey() }) { choice ->
                    FoodRow(choice.name, choice.detail, choice.per100, favorite = false) { viewModel.pick(choice) }
                }
            } else {
                item { SectionTitle("Récents", Modifier.padding(horizontal = 16.dp)) }
                items(recents, key = { "r${it.id}" }) { food ->
                    FoodRow(food.name, food.brand, food.per100(), favorite = food.favorite) { viewModel.pick(food) }
                }
            }
        }
    }
}

private fun FoodChoice.pickerKey() = when (this) {
    is FoodChoice.Mine -> "m${food.id}"
    is FoodChoice.Ciqual -> "c${food.code}"
}
