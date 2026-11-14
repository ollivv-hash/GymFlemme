package fr.olivier.forme.feature.hydration.data

import androidx.room.withTransaction
import fr.olivier.forme.core.db.FormeDatabase
import fr.olivier.forme.feature.hydration.domain.DailyGoal
import fr.olivier.forme.feature.hydration.health.IntakeMirror
import fr.olivier.forme.feature.hydration.domain.GoalSettings
import fr.olivier.forme.feature.hydration.domain.HydrationMath
import fr.olivier.forme.feature.nutrition.data.MealEntryEntity
import fr.olivier.forme.feature.nutrition.data.per100
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Today's hydration: [countedMl] against [goal]. */
data class WaterToday(val date: LocalDate, val countedMl: Int, val goal: DailyGoal) {
    val reached get() = countedMl >= goal.totalMl
    val remainingMl get() = (goal.totalMl - countedMl).coerceAtLeast(0)
}

/** An intake just recorded; [goalJustReached]: it reached today's goal, for the first time today. */
data class AddedIntake(val id: Long, val volumeMl: Int, val goalJustReached: Boolean, val today: WaterToday)

/**
 * Drinks, containers and intakes (V2.29). The goal of each day is frozen in `water_day`: today's row follows the
 * settings, the weight and the sessions; a past row never changes (see [WaterDayEntity]).
 */
@Singleton
class HydrationRepository @Inject constructor(
    private val db: FormeDatabase,
    private val dao: HydrationDao,
    private val settings: HydrationSettingsRepository,
    private val clock: Clock,
) {
    /** V2.29: Health Connect copy of the intakes (none in tests built by hand). */
    @set:Inject
    var mirror: IntakeMirror = IntakeMirror.NONE

    fun today(): LocalDate = LocalDate.now(clock)

    fun dateOf(instant: Instant): LocalDate = instant.atZone(clock.zone).toLocalDate() // LocalDate.ofInstant: API 34

    // --- Drinks and containers ---

    val drinks: Flow<List<DrinkEntity>> = dao.observeDrinks()
    val containers: Flow<List<ContainerEntity>> = dao.observeContainers()

    suspend fun drink(id: Long): DrinkEntity? = dao.getDrink(id)
    suspend fun water(): DrinkEntity? = dao.getDrinkByCode(DrinkEntity.WATER)?.takeIf { !it.archived }
        ?: dao.getDrinks().firstOrNull { !it.archived && it.hydrationCoefficient >= 1.0 && !it.isCaloric }
    suspend fun defaultContainer(): ContainerEntity? =
        dao.getDefaultContainer() ?: dao.getContainers().firstOrNull { !it.archived }

    /** A new drink goes last; an existing one is updated (the past intakes keep their coefficient). */
    suspend fun saveDrink(drink: DrinkEntity): Long {
        val clean = drink.copy(name = drink.name.trim(), hydrationCoefficient = HydrationMath.coerceCoefficient(drink.hydrationCoefficient))
        return if (clean.id == 0L) dao.insertDrink(clean.copy(sortOrder = dao.nextDrinkSortOrder())) else clean.id.also { dao.updateDrink(clean) }
    }

    suspend fun reorderDrinks(ordered: List<DrinkEntity>) = dao.updateDrinks(ordered.mapIndexed { i, d -> d.copy(sortOrder = i) })

    /** A new container goes last; [ContainerEntity.isDefault] makes it the only default one. */
    suspend fun saveContainer(container: ContainerEntity): Long = db.withTransaction {
        val clean = container.copy(name = container.name.trim(), volumeMl = container.volumeMl.coerceIn(HydrationMath.FREE_MIN_ML, HydrationMath.FREE_MAX_ML))
        val id = if (clean.id == 0L) dao.insertContainer(clean.copy(sortOrder = dao.nextContainerSortOrder())) else clean.id.also { dao.updateContainer(clean) }
        if (clean.isDefault && !clean.archived) dao.setDefaultContainer(id)
        id
    }

    suspend fun setDefaultContainer(id: Long) = dao.setDefaultContainer(id)

    suspend fun reorderContainers(ordered: List<ContainerEntity>) = dao.updateContainers(ordered.mapIndexed { i, c -> c.copy(sortOrder = i) })

    // --- Intakes ---

    fun observeIntakes(date: LocalDate): Flow<List<IntakeWithDrink>> = dao.observeDay(date)

    fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DailyWater>> = dao.observeDailyTotals(from, to)

    suspend fun dailyTotals(from: LocalDate, to: LocalDate): List<DailyWater> = dao.dailyTotals(from, to)

    /** Each day with an intake from [from] to [to], with its frozen goal (Week tab; statistics to come). */
    fun observeDaysWithGoal(from: LocalDate, to: LocalDate): Flow<List<WaterDayTotal>> = dao.observeDaysWithGoal(from, to)

    /** Frozen goals of the days from [from] to [to] (today's follows the settings). */
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<WaterDayEntity>> = dao.observeDays(from, to)

    /** Today's goal, live: last weight, settings, session started today. */
    fun observeGoal(date: LocalDate = today()): Flow<DailyGoal> = combine(
        dao.observeLatestWeight(),
        settings.goal,
        dao.observeWaterDay(date).map { it?.sessionDay == true }.distinctUntilChanged(),
    ) { weight, goal, sessionDay -> HydrationMath.goal(weight, goal, sessionDay) }.distinctUntilChanged()

    fun observeToday(date: LocalDate = today()): Flow<WaterToday> = combine(
        dao.observeDailyTotals(date, date).map { it.firstOrNull()?.countedMl ?: 0 }.distinctUntilChanged(),
        observeGoal(date),
    ) { counted, goal -> WaterToday(date, counted, goal) }

    suspend fun todayState(): WaterToday {
        val date = today()
        val counted = dao.dailyTotals(date, date).firstOrNull()?.countedMl ?: 0
        return WaterToday(date, counted, liveGoal(date))
    }

    /** Records [volumeMl] of [drinkId] at [at] (now by default); the coefficient of the drink is copied. */
    suspend fun add(drinkId: Long, volumeMl: Int, source: IntakeSource, at: Instant = clock.instant()): AddedIntake {
        val before = todayState()
        val ml = volumeMl.coerceIn(HydrationMath.FREE_MIN_ML, HydrationMath.FREE_MAX_ML)
        val id = db.withTransaction {
            val drink = dao.getDrink(drinkId) ?: error("Unknown drink $drinkId")
            val date = dateOf(at)
            val id = dao.insertIntake(
                WaterIntakeEntity(
                    timestamp = at,
                    date = date,
                    drinkId = drink.id,
                    volumeMl = ml,
                    coefficientApplied = drink.hydrationCoefficient,
                    linkedMealEntryId = mealEntryFor(drink, ml, at),
                    source = source,
                ),
            )
            freezeDay(date)
            id
        }
        dao.getIntake(id)?.let(mirror::upsert)
        val after = todayState()
        val reached = !before.reached && after.reached && settings.markGoalAnnounced(after.date)
        return AddedIntake(id, ml, reached, after)
    }

    // --- Link with the nutrition journal ---

    /** A caloric drink with no product chosen yet: its first intake asks for one (or "Ne pas compter les calories"). */
    suspend fun needsFood(drinkId: Long): Boolean = dao.getDrink(drinkId)?.let { it.isCaloric && it.linkedFoodId == null } == true

    /** The product giving the calories of [drinkId]; the past intakes keep their meal entries. */
    suspend fun linkFood(drinkId: Long, foodId: Long) {
        val drink = dao.getDrink(drinkId) ?: return
        dao.updateDrink(drink.copy(linkedFoodId = foodId, isCaloric = true))
    }

    /** Réglages › Mes boissons, "Délier": the next intake asks for a product again (past entries stay). */
    suspend fun unlinkFood(drinkId: Long) {
        val drink = dao.getDrink(drinkId) ?: return
        dao.updateDrink(drink.copy(linkedFoodId = null))
    }

    /** "Ne pas compter les calories": the drink is no longer caloric (it can be again in Réglages › Hydratation). */
    suspend fun dontCountCalories(drinkId: Long) {
        val drink = dao.getDrink(drinkId) ?: return
        dao.updateDrink(drink.copy(isCaloric = false, linkedFoodId = null))
    }

    /**
     * The meal entry of an intake of a caloric drink linked to a product: [volumeMl] counted as grams (1 ml = 1 g),
     * in the meal of that time (breakfast before 11:00…), values copied from the product. Null otherwise.
     */
    private suspend fun mealEntryFor(drink: DrinkEntity, volumeMl: Int, at: Instant): Long? {
        if (!drink.isCaloric) return null
        val food = drink.linkedFoodId?.let { db.foodDao().get(it) } ?: return null
        val macros = NutritionMath.forQuantity(food.per100(), volumeMl.toDouble())
        val time = at.atZone(clock.zone)
        return db.mealDao().insert(
            MealEntryEntity(
                date = time.toLocalDate(),
                mealType = MealType.at(time.toLocalTime()),
                foodId = food.id,
                name = food.brand?.let { "${food.name} ($it)" } ?: food.name,
                quantityG = volumeMl.toDouble(),
                proteinG = macros.proteinG,
                kcal = macros.kcal,
                carbsG = macros.carbsG,
                fatG = macros.fatG,
                eatenAt = at,
            ),
        )
    }

    /** The default container's drink and volume ("+" of the home tile). Null without any container or drink left. */
    suspend fun defaultChoice(): Pair<Long, Int>? {
        val container = defaultContainer() ?: return null
        val drink = dao.getDrink(container.defaultDrinkId)?.takeIf { !it.archived } ?: water() ?: return null
        return drink.id to container.volumeMl
    }

    /** The default container with its drink, at once (tests; the home tile goes through [needsFood] first). */
    suspend fun addDefault(source: IntakeSource): AddedIntake? = defaultChoice()?.let { (drinkId, ml) -> add(drinkId, ml, source) }

    /** Water of the notification actions. */
    suspend fun addWater(volumeMl: Int, source: IntakeSource): AddedIntake? = water()?.let { add(it.id, volumeMl, source) }

    suspend fun intake(id: Long): WaterIntakeEntity? = dao.getIntake(id)

    /**
     * Changes the drink, the volume or the time of an intake. A new drink brings its coefficient; the same drink
     * keeps the one copied at the time.
     */
    suspend fun update(id: Long, drinkId: Long, volumeMl: Int, at: Instant) {
        updateInTransaction(id, drinkId, volumeMl, at)
        dao.getIntake(id)?.let(mirror::upsert)
    }

    private suspend fun updateInTransaction(id: Long, drinkId: Long, volumeMl: Int, at: Instant) = db.withTransaction {
        val old = dao.getIntake(id) ?: return@withTransaction
        val ml = volumeMl.coerceIn(HydrationMath.FREE_MIN_ML, HydrationMath.FREE_MAX_ML)
        val drink = dao.getDrink(drinkId) ?: return@withTransaction
        val coefficient = if (drinkId == old.drinkId) old.coefficientApplied else drink.hydrationCoefficient
        val date = dateOf(at)
        // Its meal entry follows: same drink, the quantity and the time change; another drink, a new entry (or none).
        val mealId = if (drinkId == old.drinkId) {
            old.linkedMealEntryId?.also { mealId -> db.mealDao().get(mealId)?.let { db.mealDao().update(it.moved(ml, at, clock)) } }
        } else {
            // Unlinked first: deleting the entry would delete the intake with it (cascade).
            old.linkedMealEntryId?.let { mealId ->
                dao.updateIntake(old.copy(linkedMealEntryId = null))
                db.mealDao().delete(mealId)
            }
            mealEntryFor(drink, ml, at)
        }
        dao.updateIntake(old.copy(drinkId = drinkId, volumeMl = ml, timestamp = at, date = date, coefficientApplied = coefficient, linkedMealEntryId = mealId))
        freezeDay(date)
    }

    /** Deletes an intake and the meal entry linked to it. Returns the deleted intake (to undo nothing else). */
    suspend fun delete(id: Long): WaterIntakeEntity? = db.withTransaction {
        val intake = dao.getIntake(id) ?: return@withTransaction null
        dao.deleteIntake(id)
        intake.linkedMealEntryId?.let { db.mealDao().delete(it) }
        intake
    }?.also { mirror.delete(it.id) }

    // --- Goal of each day ---

    private suspend fun liveGoal(date: LocalDate): DailyGoal =
        HydrationMath.goal(dao.latestWeight(), settings.current().goal, dao.getWaterDay(date)?.sessionDay == true)

    /**
     * Today: writes the goal as it is now. A past day: written once if missing (weight of that day or before,
     * current settings), never changed afterwards.
     */
    suspend fun freezeDay(date: LocalDate) {
        val existing = dao.getWaterDay(date)
        if (date == today()) {
            val goal = liveGoal(date)
            dao.upsertDay(WaterDayEntity(date, goal.computedMl, goal.customMl, goal.bonusMl, existing?.sessionDay == true))
        } else if (existing == null) {
            val sessionDay = dao.countSessionsStarted(startOf(date), startOf(date.plusDays(1))) > 0
            val goal = HydrationMath.goal(dao.weightOn(date), settings.current().goal, sessionDay)
            dao.upsertDay(WaterDayEntity(date, goal.computedMl, goal.customMl, goal.bonusMl, sessionDay))
        }
    }

    /** Settings changed (goal, bonus) or a new weighing: today's row follows. */
    suspend fun refreshToday() = freezeDay(today())

    /** Réglages › Hydratation: the goal settings are saved, then today's row follows. */
    suspend fun setGoal(goal: GoalSettings) {
        settings.setGoal(goal)
        refreshToday()
    }

    /** A session started at [at]: the bonus of that day (once, whatever the number of sessions). */
    suspend fun onSessionStarted(at: Instant) = db.withTransaction {
        val date = dateOf(at)
        if (dao.getWaterDay(date)?.sessionDay == true) return@withTransaction
        val goalSettings = settings.current().goal
        val existing = dao.getWaterDay(date)
        val base = existing ?: run {
            val goal = HydrationMath.goal(dao.latestWeight(), goalSettings, sessionDay = false)
            WaterDayEntity(date, goal.computedMl, goal.customMl)
        }
        dao.upsertDay(base.copy(sessionDay = true, bonusMl = if (goalSettings.bonusEnabled) goalSettings.bonusMl else 0))
    }

    /**
     * A session started at [startedAt] was abandoned (and deleted). Without any set done, the bonus goes, unless
     * another session was started that day.
     */
    suspend fun onSessionAbandoned(startedAt: Instant, hadSets: Boolean) = db.withTransaction {
        if (hadSets) return@withTransaction
        val date = dateOf(startedAt)
        if (dao.countSessionsStarted(startOf(date), startOf(date.plusDays(1))) > 0) return@withTransaction
        val day = dao.getWaterDay(date) ?: return@withTransaction
        dao.upsertDay(day.copy(sessionDay = false, bonusMl = 0))
    }

    /** Goal of every day with a row, today's live (badges). */
    suspend fun goalsByDay(): Map<LocalDate, Int> {
        val today = today()
        return dao.getAllDays().associate { it.date to it.goalMl } + (today to liveGoal(today).totalMl)
    }

    suspend fun allDailyTotals(): List<DailyWater> = dao.allDailyTotals()

    suspend fun lastIntakeAt(): Instant? = dao.lastIntakeAt()?.let(Instant::ofEpochMilli)

    private fun startOf(date: LocalDate): Long = date.atStartOfDay(clock.zone).toInstant().toEpochMilli()
}

/** The meal entry of an intake whose volume or time changed: values scaled, same meal, the intake's day and time. */
internal fun MealEntryEntity.moved(volumeMl: Int, at: Instant, clock: Clock): MealEntryEntity {
    val r = if (quantityG > 0) volumeMl / quantityG else 1.0
    return copy(
        quantityG = volumeMl.toDouble(),
        proteinG = proteinG * r,
        kcal = kcal?.times(r),
        carbsG = carbsG?.times(r),
        fatG = fatG?.times(r),
        date = at.atZone(clock.zone).toLocalDate(),
        eatenAt = at,
    )
}
