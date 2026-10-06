package fr.olivier.forme.feature.widget

import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.bmrPerDay
import fr.olivier.forme.feature.nutrition.data.macros
import fr.olivier.forme.feature.nutrition.data.netKcal
import fr.olivier.forme.feature.nutrition.data.restDayKcal
import fr.olivier.forme.feature.nutrition.domain.EnergyMath
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.TemplateRepository
import fr.olivier.forme.feature.sport.domain.WeeklyStats
import fr.olivier.forme.feature.sport.ui.home.label
import fr.olivier.forme.feature.sport.ui.templates.title
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** What the home-screen widget shows (V2.6). */
data class WidgetData(
    /** Running session label, or the session the rotation proposes; null without any template. */
    val sessionTitle: String?,
    val sessionRunning: Boolean,
    val weekDone: Int,
    val weekGoal: Int,
    val kcal: Double,
    val kcalTarget: Int?,
    val proteinG: Double,
    val proteinTargetG: Double?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class WidgetDataSource @Inject constructor(
    private val sessions: SessionRepository,
    private val templates: TemplateRepository,
    private val settings: SettingsRepository,
    private val nutrition: NutritionRepository,
    private val heart: HeartRepository,
    private val weights: BodyWeightRepository,
    private val clock: Clock,
) {
    /** Today's data; [today] is re-read by the caller at midnight (the widget is refreshed then). */
    fun observe(today: LocalDate = LocalDate.now(clock)): Flow<WidgetData> {
        val lastWeight = weights.observeAll().map { list -> list.maxByOrNull { it.date }?.weightKg }
        val sport = combine(
            sessions.observeActive(),
            templates.observeProposed(),
            sessions.observeFinished(),
            settings.trainingGoals,
        ) { active, proposed, finished, goals ->
            val week = WeeklyStats.weekStart(today)
            Triple(
                active?.label() ?: proposed?.title(),
                active != null,
                finished.count { WeeklyStats.weekStart(it.startedAt.atZone(clock.zone).toLocalDate()) == week } to goals.sessionsPerWeek,
            )
        }
        val food = combine(nutrition.observeDay(today), nutrition.profile, lastWeight) { entries, profile, weight ->
            Triple(entries, profile, weight)
        }.flatMapLatest { (entries, profile, weight) ->
            val bmr = profile.bmrPerDay(weight, today.year)
            val base = profile.restDayKcal(weight, today.year)
            (if (base == null) flowOf(null) else heart.observeDay(today).map { it.netKcal(bmr) }).map { sessionKcal ->
                val totals = NutritionMath.dayTotals(entries.map { it.macros() })
                Triple(totals, EnergyMath.target(base, sessionKcal)?.totalKcal, NutritionMath.proteinTarget(weight, profile.proteinTargetGPerKg))
            }
        }
        return combine(sport, food) { (title, running, week), (totals, kcalTarget, proteinTarget) ->
            WidgetData(
                sessionTitle = title,
                sessionRunning = running,
                weekDone = week.first,
                weekGoal = week.second,
                kcal = totals.kcal,
                kcalTarget = kcalTarget,
                proteinG = totals.proteinG,
                proteinTargetG = proteinTarget,
            )
        }
    }
}
