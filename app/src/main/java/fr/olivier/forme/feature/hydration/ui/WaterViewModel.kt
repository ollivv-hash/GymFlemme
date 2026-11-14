package fr.olivier.forme.feature.hydration.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.badges.data.BadgeRepository
import fr.olivier.forme.feature.hydration.data.AddedIntake
import fr.olivier.forme.feature.hydration.data.ContainerEntity
import fr.olivier.forme.feature.hydration.data.DrinkEntity
import fr.olivier.forme.feature.hydration.data.HydrationRepository
import fr.olivier.forme.feature.hydration.data.HydrationSettings
import fr.olivier.forme.feature.hydration.data.HydrationSettingsRepository
import fr.olivier.forme.feature.hydration.reminder.HydrationReminder
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

    /** A caloric drink with no product yet: the screen opens the choice of its product (the intake waits). */
    data class NeedsFood(val drinkId: Long) : WaterEvent
}

/** An intake waiting for the product of its drink. */
private data class PendingIntake(val drinkId: Long, val volumeMl: Int, val source: IntakeSource)

/** Water screen and home tile (V2.29). */
@HiltViewModel
class WaterViewModel @Inject constructor(
    private val repository: HydrationRepository,
    private val badges: BadgeRepository,
    private val settings: HydrationSettingsRepository,
    private val reminder: HydrationReminder,
    clock: Clock,
) : ViewModel() {
    private fun <T> Flow<T>.state(initial: T): StateFlow<T> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val date: LocalDate = repository.today()
    val zone: ZoneId = clock.zone

    val today: StateFlow<WaterToday?> = repository.observeToday(date).state(null)

    /** Hydration settings; null until read (the first-use card waits for them). */
    val hydrationSettings: StateFlow<HydrationSettings?> = settings.settings.state(null)

    /** First-use card: "C'est parti", reminders on or off as ticked; the reminders start (or stay off). */
    fun introStart(remindersOn: Boolean) = viewModelScope.launch {
        settings.setIntroSeen(remindersOn)
        reminder.reschedule()
    }

    /** "Plus tard": the card goes, no reminder (they can be switched on in Réglages › Hydratation). */
    fun introLater() = viewModelScope.launch {
        settings.setIntroSeen(remindersOn = false)
        reminder.reschedule()
    }
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
        // V2.29: hydration badges and challenge, announced like the others.
        if (added != null) runCatching { badges.evaluate() }
        _events.emit(added?.let { WaterEvent.Added(it.id, it.volumeMl, it.goalJustReached, it.today.goal.totalMl) } ?: WaterEvent.Failed)
    }

    private var pending: PendingIntake? = null

    /** Adds the intake, unless its drink is caloric with no product yet: its choice opens first ([WaterEvent.NeedsFood]). */
    fun add(drinkId: Long, volumeMl: Int, source: IntakeSource) = viewModelScope.launch {
        if (repository.needsFood(drinkId)) {
            pending = PendingIntake(drinkId, volumeMl, source)
            _events.emit(WaterEvent.NeedsFood(drinkId))
        } else {
            launchAdd { repository.add(drinkId, volumeMl, source) }
        }
    }

    /** "+" of the home tile: the default container with its drink. */
    fun addDefault() = viewModelScope.launch {
        val (drinkId, ml) = repository.defaultChoice() ?: return@launch _events.emit(WaterEvent.Failed)
        add(drinkId, ml, IntakeSource.HOME_TILE)
    }

    /**
     * Back from the choice of a product: the waiting intake is added once its drink has a product (or no longer counts
     * calories); left without a choice, it is dropped.
     */
    fun resumePending() = viewModelScope.launch {
        val waiting = pending ?: return@launch
        pending = null
        if (!repository.needsFood(waiting.drinkId)) launchAdd { repository.add(waiting.drinkId, waiting.volumeMl, waiting.source) }
    }

    fun update(id: Long, drinkId: Long, volumeMl: Int, at: Instant) = viewModelScope.launch { repository.update(id, drinkId, volumeMl, at) }

    fun delete(id: Long) = viewModelScope.launch { repository.delete(id) }

    /** "Annuler" of the snackbar: the intake and its meal entry go. */
    fun undo(id: Long) = delete(id)
}
