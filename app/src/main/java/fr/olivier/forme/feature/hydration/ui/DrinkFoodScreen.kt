package fr.olivier.forme.feature.hydration.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.R
import fr.olivier.forme.feature.hydration.data.HydrationRepository
import fr.olivier.forme.feature.nutrition.data.FoodChoice
import fr.olivier.forme.feature.nutrition.data.FoodEntity
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.per100
import fr.olivier.forme.feature.nutrition.domain.FoodSource
import fr.olivier.forme.feature.nutrition.ui.FoodRow
import fr.olivier.forme.ui.components.FormeTopBar
import fr.olivier.forme.ui.components.SectionTitle
import fr.olivier.forme.ui.navigation.DrinkFoodRoute
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

const val DRINK_FOOD_SKIP_TAG = "drink_food_skip"
const val DRINK_FOOD_SEARCH_TAG = "drink_food_search"

/** The product giving the calories of a drink (V2.29): Ciqual, my foods, or a barcode scanned. */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class DrinkFoodViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val hydration: HydrationRepository,
    private val nutrition: NutritionRepository,
) : ViewModel() {
    val drinkId = savedStateHandle.toRoute<DrinkFoodRoute>().drinkId

    val drinkName: StateFlow<String> = flow { emit(hydration.drink(drinkId)?.name.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _query = MutableStateFlow("")
    val query = _query.asStateFlow()

    private fun FoodEntity.isDish() = source == FoodSource.DISH

    val results = _query.debounce(250).mapLatest { if (it.trim().length >= 2) nutrition.search(it) else emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val favorites = nutrition.favorites().map { list -> list.filterNot { it.isDish() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recents = nutrition.recents().map { list -> list.filterNot { it.isDish() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(text: String) {
        _query.value = text
    }

    fun choose(choice: FoodChoice, then: () -> Unit) = viewModelScope.launch {
        hydration.linkFood(drinkId, nutrition.resolve(choice).id)
        then()
    }

    fun choose(food: FoodEntity, then: () -> Unit) = viewModelScope.launch {
        hydration.linkFood(drinkId, food.id)
        then()
    }

    fun skip(then: () -> Unit) = viewModelScope.launch {
        hydration.dontCountCalories(drinkId)
        then()
    }
}

/**
 * First intake of a caloric drink with no product (V2.29): the product is searched, or scanned; "Ne pas compter les
 * calories" makes the drink non-caloric. Back without a choice: the intake is not added.
 */
@Composable
fun DrinkFoodScreen(onDone: () -> Unit, onScan: (drinkId: Long) -> Unit, viewModel: DrinkFoodViewModel = hiltViewModel()) {
    val name by viewModel.drinkName.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    Scaffold(topBar = { FormeTopBar(stringResource(R.string.drink_food_title, name), onDone) }) { padding ->
        LazyColumn(Modifier.padding(padding)) {
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.drink_food_intro))
                    OutlinedTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        label = { Text(stringResource(R.string.drink_food_search)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag(DRINK_FOOD_SEARCH_TAG),
                    )
                    OutlinedButton(onClick = { onScan(viewModel.drinkId) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.drink_food_scan))
                    }
                    TextButton(onClick = { viewModel.skip(onDone) }, modifier = Modifier.testTag(DRINK_FOOD_SKIP_TAG)) {
                        Text(stringResource(R.string.drink_food_skip))
                    }
                }
            }
            if (query.trim().length >= 2) {
                if (results.isEmpty()) item { Text(stringResource(R.string.drink_food_none), Modifier.padding(16.dp)) }
                items(results, key = { it.choiceKey() }) { choice ->
                    FoodRow(choice.name, choice.detail, choice.per100, favorite = (choice as? FoodChoice.Mine)?.food?.favorite == true) {
                        viewModel.choose(choice, onDone)
                    }
                }
            } else {
                if (favorites.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.drink_food_favorites), Modifier.padding(horizontal = 16.dp)) }
                    items(favorites, key = { "f${it.id}" }) { food ->
                        FoodRow(food.name, food.brand, food.per100(), favorite = true) { viewModel.choose(food, onDone) }
                    }
                }
                item { SectionTitle(stringResource(R.string.drink_food_recents), Modifier.padding(horizontal = 16.dp)) }
                if (recents.isEmpty()) item { Text(stringResource(R.string.drink_food_hint), Modifier.padding(horizontal = 16.dp)) }
                items(recents, key = { "r${it.id}" }) { food ->
                    FoodRow(food.name, food.brand, food.per100(), favorite = food.favorite) { viewModel.choose(food, onDone) }
                }
            }
        }
    }
}

private fun FoodChoice.choiceKey() = when (this) {
    is FoodChoice.Mine -> "m${food.id}"
    is FoodChoice.Ciqual -> "c${food.code}"
}
