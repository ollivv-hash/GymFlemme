package fr.olivier.forme.feature.ai.data

import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.ai.domain.AiDay
import fr.olivier.forme.feature.ai.domain.AiExerciseHistory
import fr.olivier.forme.feature.ai.domain.AiExerciseSession
import fr.olivier.forme.feature.ai.domain.AiHistory
import fr.olivier.forme.feature.ai.domain.AiNutritionDay
import fr.olivier.forme.feature.ai.domain.AiWeighIn
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
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
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
import fr.olivier.forme.feature.library.data.LibraryRepository
import fr.olivier.forme.feature.sport.domain.MuscleStats
import fr.olivier.forme.feature.sport.domain.Muscles
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
    private val library: LibraryRepository,
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
        val rows = sessions.observeWorkingSetsSince(weekStart.minusWeeks(HISTORY_WEEKS - 1L).atStartOfDay(zone).toInstant()).first()
        val sets = rows.map { TrainedSet(it.startedAt.atZone(zone).toLocalDate(), it.muscleGroup, it.type) }
        val musclesById = library.observeAll().first().associate { it.id to Muscles(it.primaryList, it.secondaryList + it.alsoTargetsList) }
        val muscles = MuscleStats.week(rows.zip(sets) { row, set -> set to row.libraryId?.let(musclesById::get) }, weekStart)
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
            muscles = muscles,
            muscleMinSets = goals.muscleMinSets,
            muscleMaxSets = goals.muscleMaxSets,
            nutrition = nutritionWeek.observe(weekStart).first(),
            measuredSessions = hearts.size,
            sessionKcal = hearts.sumOf { it.kcal ?: 0.0 }.roundToInt(),
        )

        val history = AiHistory(
            exercises = aiSessions.flatMap { it.report.exercises }
                .filter { ex -> ex.type != ExerciseType.CARDIO && ex.sets.any { !it.isWarmup } }
                .distinctBy { it.exerciseId }
                .map { ex -> exerciseHistory(ex.exerciseId, ex.name.substringBefore(" ("), ex.type, ex.perSide, date) },
            weighIns = averages.filter { !it.date.isBefore(date.minusDays(HISTORY_DAYS_WEIGHT)) }.map { avg ->
                AiWeighIn(avg.date, weighIns.first { it.date == avg.date }.weightKg, avg.weightKg)
            },
            nutritionDays = nutritionDays(date, profileEntity, lastWeight, restDayKcal),
            proteinTargetG = proteinTarget,
            weeklySets = (HISTORY_WEEKS - 1 downTo 0).map { back ->
                val start = weekStart.minusWeeks(back.toLong())
                start to WeeklyStats.week(sets, weekSessionDates, start).setsByGroup
            },
        )
        return AiDay(date, LocalDateTime.now(clock), profile, aiSessions, aiNutrition, week, history)
    }

    /** Last sessions (up to [date]) of an exercise: best set and estimated 1RM. */
    private suspend fun exerciseHistory(exerciseId: Long, name: String, type: ExerciseType, perSide: Boolean, date: LocalDate): AiExerciseHistory {
        val zone = clock.zone
        val rows = sessions.observeExerciseHistory(exerciseId).first()
            .filter { !it.isSeed && !it.startedAt.atZone(zone).toLocalDate().isAfter(date) }
        val bySession = rows.groupBy { it.sessionId }.values.toList().takeLast(HISTORY_SESSIONS)
        return AiExerciseHistory(
            name = name,
            sessions = bySession.map { session ->
                val work = session.map { it.toWorkSet() }
                val best = work.maxWith(compareBy({ it.loadKg ?: 0.0 }, { it.reps ?: 0 }, { it.durationSec ?: 0 }))
                AiExerciseSession(
                    date = session.first().startedAt.atZone(zone).toLocalDate(),
                    bestSet = SessionTextFormatter.setLabel(type, perSide, best.copy(rir = null)),
                    oneRepMaxKg = if (type == ExerciseType.WEIGHTED) OneRepMax.best(work) else null,
                )
            },
        )
    }

    /** The last 7 logged days up to [date]: protein, calories and each day's calorie target (sessions included). */
    private suspend fun nutritionDays(date: LocalDate, profile: ProfileEntity, weight: Double?, restDayKcal: Int?): List<AiNutritionDay> {
        val from = date.minusDays(6)
        val zone = clock.zone
        val bmr = profile.bmrPerDay(weight, date.year)
        val sessionKcal = heart.observeRange(from, date.plusDays(1)).first()
            .groupBy { it.seriesStart.atZone(zone).toLocalDate() }
            .mapValues { (_, list) -> list.netKcal(bmr) }
        return nutrition.observeRange(from, date).first().groupBy { it.date }.toSortedMap().map { (day, entries) ->
            AiNutritionDay(
                date = day,
                proteinG = entries.sumOf { it.proteinG },
                kcal = entries.sumOf { it.kcal ?: 0.0 },
                kcalTarget = EnergyMath.target(restDayKcal, sessionKcal[day])?.totalKcal,
            )
        }
    }

    private companion object {
        const val HISTORY_SESSIONS = 8
        const val HISTORY_WEEKS = 4
        const val HISTORY_DAYS_WEIGHT = 30L
    }
}
