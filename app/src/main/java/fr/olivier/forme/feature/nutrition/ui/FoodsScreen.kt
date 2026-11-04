package fr.olivier.forme.feature.nutrition.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.nutrition.data.FoodEntity
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.per100
import fr.olivier.forme.feature.nutrition.domain.FoodSource
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.ui.components.ConfirmDialog
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.parseDecimal
import fr.olivier.forme.ui.navigation.FoodEditRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

@HiltViewModel
class FoodsViewModel @Inject constructor(private val repository: NutritionRepository) : ViewModel() {
    val foods = repository.allFoods().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun toggleFavorite(food: FoodEntity) = viewModelScope.launch { repository.setFavorite(food, !food.favorite) }
    fun delete(food: FoodEntity) = viewModelScope.launch { repository.deleteFood(food.id) }
}

const val NEW_FOOD_TAG = "new_food"
const val NEW_DISH_TAG = "new_dish"

/** "Mes aliments": custom foods, scanned products and Ciqual foods already used. */
@Composable
fun FoodsScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onEditDish: (Long) -> Unit = {},
    viewModel: FoodsViewModel = hiltViewModel(),
) {
    val foods by viewModel.foods.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<FoodEntity?>(null) }
    Scaffold(
        topBar = { FormeTopBar("Mes aliments", onBack) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // V2.21: a dish of one's own, made of ingredients.
                ExtendedFloatingActionButton(
                    onClick = { onEditDish(0) },
                    modifier = Modifier.padding(bottom = 12.dp).testTag(NEW_DISH_TAG),
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Plat") },
                )
                ExtendedFloatingActionButton(containerColor = MaterialTheme.colorScheme.tertiary, contentColor = MaterialTheme.colorScheme.onTertiary, onClick = { onEdit(0) }, modifier = Modifier.testTag(NEW_FOOD_TAG), icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Aliment") })
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            if (foods.isEmpty()) item { Text("Aucun aliment. Ils apparaissent ici dès leur première utilisation.", Modifier.padding(16.dp)) }
            items(foods, key = { it.id }) { food ->
                ListItem(
                    modifier = Modifier.clickable { if (food.source == FoodSource.DISH) onEditDish(food.id) else onEdit(food.id) },
                    headlineContent = { Text(food.name) },
                    supportingContent = {
                        Text(
                            listOfNotNull(food.brand, food.source.label, macrosLine(food.proteinPer100g, food.kcalPer100g, null, null) + " /100 g")
                                .joinToString(" · "),
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.toggleFavorite(food) }) {
                                Text(if (food.favorite) "★" else "☆", style = MaterialTheme.typography.headlineSmall)
                            }
                            IconButton(onClick = { deleting = food }) { Icon(Icons.Filled.Delete, "Supprimer") }
                        }
                    },
                )
            }
        }
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

data class FoodForm(
    val name: String = "",
    val brand: String = "",
    val protein: String = "",
    val kcal: String = "",
    val carbs: String = "",
    val fat: String = "",
    val portion: String = "",
    val favorite: Boolean = false,
)

@HiltViewModel
class FoodEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: NutritionRepository,
    private val clock: Clock,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<FoodEditRoute>()
    val isNew = route.foodId == 0L
    val barcode = route.barcode.ifEmpty { null }
    private var original: FoodEntity? = null
    private val _form = MutableStateFlow(FoodForm(name = route.name))
    val form = _form.asStateFlow()

    init {
        if (!isNew) {
            viewModelScope.launch {
                repository.food(route.foodId)?.let { f ->
                    original = f
                    fun t(v: Double?) = v?.let { Fmt.number(it, 2) }.orEmpty()
                    _form.value = FoodForm(f.name, f.brand.orEmpty(), t(f.proteinPer100g), t(f.kcalPer100g), t(f.carbsPer100g), t(f.fatPer100g), t(f.defaultPortionG), f.favorite)
                }
            }
        }
    }

    fun update(transform: (FoodForm) -> FoodForm) = _form.update(transform)
    /** A name and the protein value are required. */
    fun canSave(form: FoodForm) = form.name.isNotBlank() && parseDecimal(form.protein) != null

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val f = _form.value
        val protein = parseDecimal(f.protein) ?: return@launch
        val base = original ?: FoodEntity(
            source = FoodSource.CUSTOM,
            sourceCode = barcode,
            name = "",
            proteinPer100g = 0.0,
            lastUsedAt = clock.instant(), // new foods show on top of "Récents"
        )
        repository.saveFood(
            base.copy(
                name = f.name.trim(),
                brand = f.brand.trim().ifEmpty { null },
                proteinPer100g = protein,
                kcalPer100g = parseDecimal(f.kcal),
                carbsPer100g = parseDecimal(f.carbs),
                fatPer100g = parseDecimal(f.fat),
                defaultPortionG = parseDecimal(f.portion),
                favorite = f.favorite,
            ),
        )
        onDone()
    }
}

@Composable
fun FoodEditScreen(onDone: () -> Unit, viewModel: FoodEditViewModel = hiltViewModel()) {
    val f by viewModel.form.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            FormeTopBar(if (viewModel.isNew) "Nouvel aliment" else "Modifier l'aliment", onDone) {
                // Reads the observed form: the button follows the typing (it stayed disabled when read from the ViewModel).
                TextButton(onClick = { viewModel.save(onDone) }, enabled = viewModel.canSave(f)) { Text("Enregistrer") }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            viewModel.barcode?.let { Text("Code-barres : $it", style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(f.name, { v -> viewModel.update { it.copy(name = v) } }, label = { Text("Nom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.brand, { v -> viewModel.update { it.copy(brand = v) } }, label = { Text("Marque (optionnel)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Text("Valeurs pour 100 g (étiquette)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Row {
                NumberField("Protéines (g) *", f.protein, Modifier.weight(1f).padding(end = 4.dp)) { v -> viewModel.update { it.copy(protein = v) } }
                NumberField("Énergie (kcal)", f.kcal, Modifier.weight(1f).padding(start = 4.dp)) { v -> viewModel.update { it.copy(kcal = v) } }
            }
            Row(Modifier.padding(top = 8.dp)) {
                NumberField("Glucides (g)", f.carbs, Modifier.weight(1f).padding(end = 4.dp)) { v -> viewModel.update { it.copy(carbs = v) } }
                NumberField("Lipides (g)", f.fat, Modifier.weight(1f).padding(start = 4.dp)) { v -> viewModel.update { it.copy(fat = v) } }
            }
            NumberField("Portion habituelle (g, optionnel)", f.portion, Modifier.fillMaxWidth().padding(top = 8.dp)) { v -> viewModel.update { it.copy(portion = v) } }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Favori", Modifier.weight(1f))
                Switch(checked = f.favorite, onCheckedChange = { v -> viewModel.update { it.copy(favorite = v) } })
            }
            Spacer(Modifier.height(24.dp)) // Saving is done with "Enregistrer" in the top bar.
        }
    }
}

