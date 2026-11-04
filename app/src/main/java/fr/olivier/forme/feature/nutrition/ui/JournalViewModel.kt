package fr.olivier.forme.feature.nutrition.ui

import fr.olivier.forme.feature.badges.data.BadgeRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.nutrition.data.bmrPerDay
import fr.olivier.forme.feature.nutrition.data.macros
import fr.olivier.forme.feature.nutrition.data.netKcal
import fr.olivier.forme.feature.nutrition.data.protein
import fr.olivier.forme.feature.nutrition.data.restDayKcal
import fr.olivier.forme.feature.nutrition.domain.CreatineStreak
import fr.olivier.forme.feature.nutrition.domain.DayTotals
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.nutrition.domain.ProteinAdvice
import fr.olivier.forme.feature.nutrition.domain.ProteinPlan
import fr.olivier.forme.feature.nutrition.domain.WheyAdvice
import fr.olivier.forme.feature.nutrition.domain.WheyMoment
import fr.olivier.forme.feature.nutrition.reminder.SupplementReminder
import fr.olivier.forme.feature.nutrition.data.SupplementEntity
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
    /** Active supplements (V2.20), in their order, and whether each was taken that day. */
    val supplements: List<SupplementDay> = emptyList(),
    val creatineStreak: Int = 0,
    /** Protein advice of the day (today only, with a target). */
    val proteinPlan: ProteinPlan? = null,
    /** Rest-day calorie target; null without a nutrition goal. */
    val restDayKcal: Int? = null,
    val wheyDoses: Int = 0,
    /** Last recorded body weight (floor of the fat target). */
    val weightKg: Double? = null,
) {
    fun entriesOf(type: MealType) = entries.filter { it.mealType == type }
    val wheyConfigured get() = profile.wheyProteinPer100g != null
    val proteinPerDoseG: Double? get() = profile.wheyProteinPer100g?.let { it * profile.wheyScoopG / 100 }

    /** A first dose is always offered; a second one only when the advice asks for it. */
    val canAddWhey: Boolean
        get() = wheyDoses < ProteinAdvice.MAX_DOSES && (wheyDoses == 0 || (proteinPlan?.doses ?: 0) > 0)
}

data class SupplementDay(val supplement: SupplementEntity, val taken: Boolean)

/** Shared by the Nutrition journal and the home card. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repository: NutritionRepository,
    private val reminder: SupplementReminder,
    weights: BodyWeightRepository,
    heart: HeartRepository,
    private val badges: BadgeRepository,
) : ViewModel() {
    private val today = repository.today()
    private val date = MutableStateFlow(today)
    private val lastWeight = weights.observeAll().map { list -> list.maxByOrNull { it.date }?.weightKg }

    val ui: StateFlow<JournalUi> = date.flatMapLatest { d ->
        val history = repository.observeRange(d.minusDays(ProteinAdvice.LOOKBACK_DAYS), d.minusDays(1))
            .map { list -> ProteinAdvice.usualProtein(list.map { it.protein() }) }
        val day = combine(
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
                creatineStreak = CreatineStreak.days(creatineDates, repository.today()),
                restDayKcal = profile.restDayKcal(weight, d.year),
                wheyDoses = entries.count { it.isWhey },
                weightKg = weight,
            )
        }
        val supplements = combine(repository.supplements, repository.supplementsTaken(d)) { list, taken ->
            list.filter { it.active }.map { SupplementDay(it, it.id in taken) }
        }
        combine(day, history, supplements) { dayUi, usual, supplementDays ->
            val ui = dayUi.copy(supplements = supplementDays)
            val target = ui.proteinTargetG
            if (!ui.isToday || target == null) return@combine ui
            val upcoming = ProteinAdvice.upcoming(ui.entries.map { it.protein() }, usual, repository.now())
            ui.copy(proteinPlan = ProteinAdvice.plan(ui.totals.proteinG, target, upcoming, ui.proteinPerDoseG, ui.wheyDoses))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUi(today, true))

    /** Calories spent in the day's sessions beyond basal metabolism (V2.1, net since 2.3.2); null when none was measured. */
    val sessionKcal: StateFlow<Double?> = date.flatMapLatest { d ->
        combine(heart.observeDay(d), repository.profile, lastWeight) { list, profile, weight ->
            list.netKcal(profile.bmrPerDay(weight, d.year))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shiftDay(days: Long) = date.update { (it.plusDays(days)).coerceAtMost(repository.today()) }

    fun setSupplement(supplement: SupplementEntity, taken: Boolean) = viewModelScope.launch {
        repository.setSupplementTaken(supplement.id, date.value, taken)
        if (taken && date.value == repository.today()) reminder.dismissNotification(supplement.id)
        if (taken && supplement.isCreatine) evaluateBadges() // Créatine fidèle
    }

    /** Whey goes after the session on training days, otherwise with breakfast (morning) or as a snack. */
    fun addWhey() = viewModelScope.launch {
        val meal = when {
            ui.value.wheyMoment == WheyMoment.AFTER_SESSION -> MealType.SNACK
            LocalTime.now().hour < 11 -> MealType.BREAKFAST
            else -> MealType.SNACK
        }
        repository.addWheyDose(date.value, meal)
        evaluateBadges()
    }

    fun updateQuantity(entry: MealEntryEntity, grams: Double) = viewModelScope.launch {
        repository.updateQuantity(entry, grams)
        evaluateBadges()
    }

    /** V2.18: a badge unlocked by an entry (creatine, protein target) is announced at once. */
    private suspend fun evaluateBadges() {
        runCatching { badges.evaluate() }.onFailure { android.util.Log.e("Forme", "Badge evaluation failed", it) }
    }
    fun delete(entry: MealEntryEntity) = viewModelScope.launch { repository.deleteEntry(entry.id) }
}
