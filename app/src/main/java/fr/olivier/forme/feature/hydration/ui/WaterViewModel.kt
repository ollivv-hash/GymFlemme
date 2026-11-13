package fr.olivier.forme.feature.hydration.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.hydration.data.AddedIntake
import fr.olivier.forme.feature.hydration.data.ContainerEntity
import fr.olivier.forme.feature.hydration.data.DrinkEntity
import fr.olivier.forme.feature.hydration.data.HydrationRepository
import fr.olivier.forme.feature.hydration.data.IntakeSource
import fr.olivier.forme.feature.hydration.data.IntakeWithDrink
import fr.olivier.forme.feature.hydration.data.WaterToday
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** One bar of the 7-day chart: [goalMl] null when that day's goal is unknown (nothing recorded). */
data class WaterBar(val date: LocalDate, val countedMl: Int, val goalMl: Int?)

/** Told to the screen showing the snackbar. */
sealed interface WaterEvent {
    /** [goalJustReached]: today's goal reached by this intake, the first time today. */
    data class Added(val id: Long, val volumeMl: Int, val goalJustReached: Boolean, val goalMl: Int) : WaterEvent
    data object Failed : WaterEvent
}

/** Water screen and home tile (V2.29). */
@HiltViewModel
class WaterViewModel @Inject constructor(private val repository: HydrationRepository, clock: Clock) : ViewModel() {
    private fun <T> Flow<T>.state(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val date: LocalDate = repository.today()
    val zone: ZoneId = clock.zone

    val today: StateFlow<WaterToday?> = repository.observeToday(date).state(null)
    val intakes: StateFlow<List<IntakeWithDrink>> = repository.observeIntakes(date).state(emptyList())
    val drinks: StateFlow<List<DrinkEntity>> = repository.drinks.map { all -> all.filterNot { it.archived } }.state(emptyList())

    /** Every drink, archived ones included (history of an archived drink, edit dialog). */
    val allDrinks: StateFlow<List<DrinkEntity>> = repository.drinks.state(emptyList())
    val containers: StateFlow<List<ContainerEntity>> = repository.containers.map { all -> all.filterNot { it.archived } }.state(emptyList())

    val week: StateFlow<List<WaterBar>> = combine(
        repository.observeDailyTotals(date.minusDays(6), date),
        repository.observeDays(date.minusDays(6), date),
        repository.observeToday(date),
    ) { totals, days, today ->
        val counted = totals.associate { it.date to it.countedMl }
        val goals = days.associate { it.date to it.goalMl }
        (6 downTo 0).map { back ->
            val day = date.minusDays(back.toLong())
            WaterBar(day, counted[day] ?: 0, if (day == date) today.goal.totalMl else goals[day])
        }
    }.state(emptyList())

    private val _events = MutableSharedFlow<WaterEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<WaterEvent> = _events

    private fun launchAdd(block: suspend () -> AddedIntake?) = viewModelScope.launch {
        val added = runCatching { block() }.getOrNull()
        _events.emit(added?.let { WaterEvent.Added(it.id, it.volumeMl, it.goalJustReached, it.today.goal.totalMl) } ?: WaterEvent.Failed)
    }

    fun add(drinkId: Long, volumeMl: Int, source: IntakeSource) = launchAdd { repository.add(drinkId, volumeMl, source) }

    /** "+" of the home tile: the default container with its drink. */
    fun addDefault() = launchAdd { repository.addDefault(IntakeSource.HOME_TILE) }

    fun update(id: Long, drinkId: Long, volumeMl: Int, at: Instant) = viewModelScope.launch { repository.update(id, drinkId, volumeMl, at) }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }

    /** "Annuler" of the snackbar: the intake and its meal entry go. */
    fun undo(id: Long) = delete(id)
}
