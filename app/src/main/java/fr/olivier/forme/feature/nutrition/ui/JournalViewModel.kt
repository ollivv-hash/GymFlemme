package fr.olivier.forme.feature.nutrition.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.data.macros
import fr.olivier.forme.feature.nutrition.domain.CreatineStreak
import fr.olivier.forme.feature.nutrition.domain.DayTotals
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.nutrition.domain.WheyAdvice
import fr.olivier.forme.feature.nutrition.domain.WheyMoment
import fr.olivier.forme.feature.nutrition.reminder.CreatineReminder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class JournalUi(
    val date: LocalDate,
    val isToday: Boolean,
    val entries: List<MealEntryEntity> = emptyList(),
    val totals: DayTotals = NutritionMath.dayTotals(emptyList()),
    val proteinTargetG: Double? = null,
    val profile: ProfileEntity = ProfileEntity(),
    val wheyMoment: WheyMoment = WheyMoment.REST_DAY,
    val creatineTaken: Boolean = false,
    val creatineStreak: Int = 0,
) {
    fun entriesOf(type: MealType) = entries.filter { it.mealType == type }
    val wheyConfigured get() = profile.wheyProteinPer100g != null
}

/** Shared by the Nutrition journal and the home card. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val reminder: CreatineReminder,
    weights: BodyWeightRepository,
) : ViewModel() {
    private val today = repository.today()
    private val date = MutableStateFlow(today)
    private val lastWeight = weights.observeAll().map { list -> list.maxByOrNull { it.date }?.weightKg }

    val ui: StateFlow<JournalUi> = date.flatMapLatest { d ->
        combine(
            repository.observeDay(d),
            repository.profile,
            lastWeight,
            repository.observeTrainingOn(d),
            repository.creatineDates(),
        ) { entries, profile, weight, training, creatineDates ->
            JournalUi(
                date = d,
                isToday = d == repository.today(),
                entries = entries,
                totals = NutritionMath.dayTotals(entries.map { it.macros() }),
                proteinTargetG = NutritionMath.proteinTarget(weight, profile.proteinTargetGPerKg),
                profile = profile,
                wheyMoment = WheyAdvice.moment(entries.any { it.isWhey }, training),
                creatineTaken = d in creatineDates,
                creatineStreak = CreatineStreak.days(creatineDates, repository.today()),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUi(today, true))

    fun shiftDay(days: Long) = date.update { (it.plusDays(days)).coerceAtMost(repository.today()) }

    fun setCreatine(taken: Boolean) = viewModelScope.launch {
        repository.setCreatineTaken(date.value, taken)
        if (taken && date.value == repository.today()) reminder.dismissNotification()
    }

    /** Whey goes after the session on training days, otherwise with breakfast (morning) or as a snack. */
    fun addWhey() = viewModelScope.launch {
        val meal = when {
            ui.value.wheyMoment == WheyMoment.AFTER_SESSION -> MealType.SNACK
            LocalTime.now().hour < 11 -> MealType.BREAKFAST
            else -> MealType.SNACK
        }
        repository.addWheyDose(date.value, meal)
    }

    fun updateQuantity(entry: MealEntryEntity, grams: Double) = viewModelScope.launch { repository.updateQuantity(entry, grams) }
    fun delete(entry: MealEntryEntity) = viewModelScope.launch { repository.deleteEntry(entry.id) }
}
