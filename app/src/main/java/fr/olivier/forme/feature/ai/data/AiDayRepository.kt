package fr.olivier.forme.feature.ai.data

import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.ai.domain.AiDay
import fr.olivier.forme.feature.ai.domain.AiExerciseExtra
import fr.olivier.forme.feature.ai.domain.AiHeart
import fr.olivier.forme.feature.ai.domain.AiMeal
import fr.olivier.forme.feature.ai.domain.AiNutrition
import fr.olivier.forme.feature.ai.domain.AiProfile
import fr.olivier.forme.feature.ai.domain.AiSession
import fr.olivier.forme.feature.ai.domain.AiWeek
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.bodyweight.domain.MovingAverage
import fr.olivier.forme.feature.bodyweight.domain.WeightPoint
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.heart.domain.HeartZones
import fr.olivier.forme.feature.heart.domain.KcalSource
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.NutritionWeekSource
import fr.olivier.forme.feature.nutrition.data.bmrPerDay
import fr.olivier.forme.feature.nutrition.data.macros
import fr.olivier.forme.feature.nutrition.data.netKcal
import fr.olivier.forme.feature.nutrition.data.restDayKcal
import fr.olivier.forme.feature.nutrition.domain.EnergyMath
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.toSessionSets
import fr.olivier.forme.feature.sport.data.toWorkSet
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.domain.OneRepMax
import fr.olivier.forme.feature.sport.domain.PlateauDetector
import fr.olivier.forme.feature.sport.domain.SessionTextFormatter
import fr.olivier.forme.feature.sport.domain.TrainedSet
import fr.olivier.forme.feature.sport.domain.WeeklyStats
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Gathers a day's data (profile, sessions, nutrition, week) for the AI analysis file. */
@Singleton
class AiDayRepository @Inject constructor(
    private val sessions: SessionRepository,
    private val heart: HeartRepository,
    private val nutrition: NutritionRepository,
    private val nutritionWeek: NutritionWeekSource,
    private val weights: BodyWeightRepository,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    suspend fun day(date: LocalDate): AiDay {
        val zone = clock.zone
        val profileEntity = nutrition.currentProfile()
        val weighIns = weights.observeAll().first().filter { !it.date.isAfter(date) }
        val lastWeight = weighIns.maxByOrNull { it.date }?.weightKg
        val averages = MovingAverage.sevenDays(weighIns.map { WeightPoint(it.date, it.weightKg) })
        val average = averages.lastOrNull()
        val monthAgo = averages.lastOrNull { !it.date.isAfter(date.minusWeeks(4)) }
        val heartProfile = heart.heartProfile()
        val proteinTarget = NutritionMath.proteinTarget(lastWeight, profileEntity.proteinTargetGPerKg)
        val restDayKcal = profileEntity.restDayKcal(lastWeight, date.year)

        val profile = AiProfile(
            age = profileEntity.birthYear?.let { HeartZones.age(it, date.year) },
            sex = profileEntity.sex?.label?.lowercase(),
            heightCm = profileEntity.heightCm,
            weightKg = lastWeight,
            weightAverage7Kg = average?.weightKg,
            weightChange4wKg = if (average != null && monthAgo != null && monthAgo.date != average.date) average.weightKg - monthAgo.weightKg else null,
            goal = profileEntity.nutritionGoal?.label?.lowercase(),
            proteinTargetG = proteinTarget,
            restDayKcal = restDayKcal,
            hrMax = heartProfile.hrMax,
        )

        val daySessions = sessions.observeFinished().first()
            .filter { it.startedAt.atZone(zone).toLocalDate() == date }
            .sortedBy { it.startedAt }
        val aiSessions = daySessions.mapNotNull { s ->
            val report = sessions.buildReport(s.id) ?: return@mapNotNull null
            val extras = report.exercises.filter { ex -> ex.type == ExerciseType.WEIGHTED && ex.sets.any { !it.isWarmup } }.map { ex ->
                val history = sessions.observeExerciseHistory(ex.exerciseId).first().filter { !it.startedAt.isAfter(s.startedAt) }
                val before = history.filter { it.sessionId != s.id && it.startedAt.isBefore(s.startedAt) }
                val last = before.groupBy { it.sessionId }.values.lastOrNull()
                AiExerciseExtra(
                    exerciseId = ex.exerciseId,
                    lastTime = last?.joinToString(" · ") { SessionTextFormatter.setLabel(ex.type, ex.perSide, it.toWorkSet()) },
                    oneRepMaxKg = OneRepMax.best(ex.sets.filterNot { it.isWarmup }.map { it.work }),
                    plateauSessions = PlateauDetector.detect(history.toSessionSets(), 0.0)?.sessions,
                )
            }
            val h = heart.get(s.id)
            AiSession(
                report = report,
                heart = h?.let {
                    AiHeart(
                        avgBpm = it.avgBpm,
                        maxBpm = it.maxBpm,
                        zoneMinutes = it.zoneSeconds.map { sec -> (sec / 60.0).roundToInt() },
                        kcal = it.kcal?.roundToInt(),
                        kcalLabel = when (it.kcalSource) {
                            KcalSource.WATCH -> KcalSource.WATCH.label
                            KcalSource.ESTIMATE -> "estimation par la FC"
                            null -> null
                        },
                    )
                },
                extras = extras,
            )
        }

        val entries = nutrition.observeDay(date).first()
        val sessionKcal = heart.observeDay(date).first().netKcal(profileEntity.bmrPerDay(lastWeight, date.year))
        val aiNutrition = AiNutrition(
            totals = NutritionMath.dayTotals(entries.map { it.macros() }),
            proteinTargetG = proteinTarget,
            kcalTarget = EnergyMath.target(restDayKcal, sessionKcal),
            meals = MealType.entries.mapNotNull { type ->
                val meal = entries.filter { it.mealType == type }
                if (meal.isEmpty()) {
                    null
                } else {
                    AiMeal(
                        label = type.label,
                        proteinG = meal.sumOf { it.proteinG },
                        kcal = meal.sumOf { it.kcal ?: 0.0 },
                        foods = meal.map { "${it.name} ${Fmt.number(it.quantityG, 0)} g" },
                    )
                }
            },
            wheyDoses = entries.count { it.isWhey },
            creatineTaken = nutrition.isCreatineTaken(date),
        )

        val weekStart = WeeklyStats.weekStart(date)
        val goals = settings.trainingGoals.first()
        val sets = sessions.observeWorkingSetsSince(weekStart.atStartOfDay(zone).toInstant()).first()
            .map { TrainedSet(it.startedAt.atZone(zone).toLocalDate(), it.muscleGroup, it.type) }
        val weekSessionDates = sessions.observeFinished().first().map { it.startedAt.atZone(zone).toLocalDate() }
        val stats = WeeklyStats.week(sets, weekSessionDates, weekStart)
        val hearts = heart.observeRange(weekStart, weekStart.plusDays(7)).first()
        val week = AiWeek(
            weekStart = weekStart,
            sessions = stats.sessions,
            sessionsGoal = goals.sessionsPerWeek,
            setsByGroup = stats.setsByGroup,
            minSets = goals.minSets,
            maxSets = goals.maxSets,
            nutrition = nutritionWeek.observe(weekStart).first(),
            measuredSessions = hearts.size,
            sessionKcal = hearts.sumOf { it.kcal ?: 0.0 }.roundToInt(),
        )

        return AiDay(date, LocalDateTime.now(clock), profile, aiSessions, aiNutrition, week)
    }
}
