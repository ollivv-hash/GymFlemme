package fr.olivier.forme.feature.hydration.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.hydration.data.ContainerEntity
import fr.olivier.forme.feature.hydration.data.DrinkEntity
import fr.olivier.forme.feature.hydration.data.HydrationRepository
import fr.olivier.forme.feature.hydration.data.HydrationSettings
import fr.olivier.forme.feature.hydration.data.HydrationSettingsRepository
import fr.olivier.forme.feature.hydration.data.ReminderSettings
import fr.olivier.forme.feature.hydration.domain.DailyGoal
import fr.olivier.forme.feature.hydration.domain.GoalSettings
import fr.olivier.forme.feature.hydration.health.HydrationHealthMirror
import fr.olivier.forme.feature.hydration.reminder.HydrationReminder
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Réglages › Hydratation (V2.29). */
@HiltViewModel
class HydrationSettingsViewModel @Inject constructor(
    private val repository: HydrationRepository,
    private val settings: HydrationSettingsRepository,
    private val reminder: HydrationReminder,
    private val health: HydrationHealthMirror,
    nutrition: NutritionRepository,
) : ViewModel() {
    private fun <T> Flow<T>.state(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val current: StateFlow<HydrationSettings?> = settings.settings.state(null)
    val goal: StateFlow<DailyGoal?> = repository.observeGoal().state(null)
    val drinks: StateFlow<List<DrinkEntity>> = repository.drinks.state(emptyList())
    val containers: StateFlow<List<ContainerEntity>> = repository.containers.state(emptyList())

    /** Names of the products linked to drinks. */
    val foodNames: StateFlow<Map<Long, String>> = nutrition.allFoods().map { foods -> foods.associate { it.id to it.name } }.state(emptyMap())

    fun setGoal(goal: GoalSettings) = viewModelScope.launch { repository.setGoal(goal) }

    fun setReminders(reminders: ReminderSettings) = viewModelScope.launch {
        settings.setReminders(reminders)
        reminder.reschedule()
    }

    // --- Health Connect ---

    fun healthConnectAvailable(): Boolean = health.available()

    suspend fun healthConnectGranted(): Boolean = runCatching { health.granted() }.getOrDefault(false)

    fun setHealthConnect(enabled: Boolean) = viewModelScope.launch { settings.setHealthConnectWrite(enabled) }

    // --- Containers ---

    fun saveContainer(container: ContainerEntity) = viewModelScope.launch { repository.saveContainer(container) }

    fun setDefaultContainer(id: Long) = viewModelScope.launch { repository.setDefaultContainer(id) }

    fun moveContainer(container: ContainerEntity, by: Int) = viewModelScope.launch {
        repository.reorderContainers(containers.value.moved(container, by))
    }

    // --- Drinks ---

    fun saveDrink(drink: DrinkEntity) = viewModelScope.launch { repository.saveDrink(drink) }

    fun unlinkFood(drinkId: Long) = viewModelScope.launch { repository.unlinkFood(drinkId) }

    fun moveDrink(drink: DrinkEntity, by: Int) = viewModelScope.launch {
        repository.reorderDrinks(drinks.value.moved(drink, by))
    }
}

/** [item] moved [by] places (-1 up, +1 down), within the list. */
internal fun <T> List<T>.moved(item: T, by: Int): List<T> {
    val from = indexOf(item)
    if (from < 0) return this
    val to = (from + by).coerceIn(0, size - 1)
    return toMutableList().apply { add(to, removeAt(from)) }
}
