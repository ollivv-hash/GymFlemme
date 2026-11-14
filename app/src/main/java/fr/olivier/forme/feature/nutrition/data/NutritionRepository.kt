package fr.olivier.forme.feature.nutrition.data

import fr.olivier.forme.feature.hydration.domain.HydrationMath
import fr.olivier.forme.feature.hydration.health.IntakeMirror
import kotlin.math.roundToInt

import androidx.room.withTransaction
import fr.olivier.forme.core.db.FormeDatabase
import fr.olivier.forme.feature.heart.data.SessionHeartEntity
import fr.olivier.forme.feature.heart.domain.HeartZones
import fr.olivier.forme.feature.heart.domain.KcalSource
import fr.olivier.forme.feature.nutrition.domain.DishIngredient
import fr.olivier.forme.feature.nutrition.domain.DishMath
import fr.olivier.forme.feature.nutrition.domain.EnergyMath
import fr.olivier.forme.feature.nutrition.domain.FoodSource
import fr.olivier.forme.feature.nutrition.domain.Macros
import fr.olivier.forme.feature.nutrition.domain.MealProtein
import fr.olivier.forme.feature.nutrition.domain.MealType
import fr.olivier.forme.feature.nutrition.domain.NutritionMath
import fr.olivier.forme.feature.nutrition.domain.Per100
import fr.olivier.forme.feature.sport.data.SessionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Normalizer
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** A search hit: one of "my" foods, or a Ciqual reference not used yet. */
sealed interface FoodChoice {
    val name: String
    val detail: String?
    val per100: Per100?

    data class Mine(val food: FoodEntity) : FoodChoice {
        override val name get() = food.name
        override val detail get() = listOfNotNull(food.brand, food.source.label).joinToString(" · ")
        override val per100 get() = food.per100()
    }

    data class Ciqual(val food: CiqualFoodEntity) : FoodChoice {
        override val name get() = food.name
        override val detail get() = "Ciqual · ${food.groupName}"
        override val per100 get() = food.proteinPer100g?.let { Per100(it, food.kcalPer100g, food.carbsPer100g, food.fatPer100g) }
    }
}

fun FoodEntity.per100() = Per100(proteinPer100g, kcalPer100g, carbsPer100g, fatPer100g)
fun MealEntryEntity.macros() = Macros(proteinG, kcal, carbsG, fatG)
fun MealEntryEntity.protein() = MealProtein(date, mealType, proteinG, isWhey)

/** Basal metabolic rate (kcal/day): Mifflin-St Jeor when every measure is known, else a weight-based estimate. */
fun ProfileEntity.bmrPerDay(weightKg: Double?, currentYear: Int): Double? {
    val weight = weightKg ?: return null
    val age = birthYear?.let { HeartZones.age(it, currentYear) }
    val sex = sex
    val height = heightCm
    return if (age != null && sex != null && height != null) EnergyMath.bmr(weight, height, age, sex) else weight * EnergyMath.BMR_PER_KG_FALLBACK
}

/** Calories of sessions to add to a day's target, basal metabolism removed (see [EnergyMath.netSessionKcal]). */
fun List<SessionHeartEntity>.netKcal(bmrPerDay: Double?): Double? =
    mapNotNull { h ->
        h.kcal?.let { kcal ->
            val measured = h.series.bpm.count { it > 0 } * h.series.intervalSec
            EnergyMath.netSessionKcal(kcal, h.kcalSource == KcalSource.WATCH, measured, bmrPerDay)
        }
    }.takeIf { it.isNotEmpty() }?.sum()

/** Rest-day calorie target advised from the goal and the activity (V2.25); null without a goal or a measure. */
fun ProfileEntity.advisedRestDayKcal(weightKg: Double?, currentYear: Int): Int? {
    val goal = nutritionGoal ?: return null
    return EnergyMath.advisedBase(weightKg, heightCm, birthYear?.let { HeartZones.age(it, currentYear) }, sex, goal, activityLevel)
}

/**
 * Rest-day calorie target: personalised, else advised; null without a goal or missing measures, and under 16
 * (V2.25: no calorie target for a minor).
 */
fun ProfileEntity.restDayKcal(weightKg: Double?, currentYear: Int): Int? {
    if (nutritionGoal == null || EnergyMath.isMinor(birthYear, currentYear)) return null
    return kcalTargetOverride ?: advisedRestDayKcal(weightKg, currentYear)
}

/** Protein target of a day (g): personalised in grams (V2.25), else g/kg × the last weight. */
fun ProfileEntity.proteinTargetG(weightKg: Double?): Double? =
    proteinTargetOverrideG?.toDouble() ?: NutritionMath.proteinTarget(weightKg, proteinTargetGPerKg)

@Singleton
class NutritionRepository @Inject constructor(
    private val db: FormeDatabase,
    private val profileDao: ProfileDao,
    private val ciqualDao: CiqualDao,
    private val foodDao: FoodDao,
    private val mealDao: MealDao,
    private val supplementDao: SupplementDao,
    private val sessionDao: SessionDao,
    private val off: OpenFoodFactsClient,
    private val clock: Clock,
) {
    /** V2.29: Health Connect copy of the drinks linked to meal entries (none in tests built by hand). */
    @set:Inject
    var intakeMirror: IntakeMirror = IntakeMirror.NONE

    fun today(): LocalDate = LocalDate.now(clock)

    // --- Profile ---------------------------------------------------------------------------

    val profile: Flow<ProfileEntity> = profileDao.observe().map { it ?: ProfileEntity() }
    suspend fun currentProfile(): ProfileEntity = profileDao.get() ?: ProfileEntity()
    suspend fun saveProfile(profile: ProfileEntity) = profileDao.upsert(profile.copy(id = 1))

    // --- Meals -----------------------------------------------------------------------------

    fun observeDay(date: LocalDate): Flow<List<MealEntryEntity>> = mealDao.observeDay(date)

    /** Entries from [from] to [to], both included (protein habits, weekly summary). */
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<MealEntryEntity>> = mealDao.observeRange(from, to)

    fun now(): LocalTime = LocalTime.now(clock)

    /** True when a real workout session started on [date] (whey timing). */
    fun observeTrainingOn(date: LocalDate): Flow<Boolean> {
        val zone = clock.zone
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return sessionDao.observeSessionCount(from, to).map { it > 0 }
    }

    /** Returns the id of the meal entry (V2.26: "Annuler" after a dish added in one tap). */
    suspend fun addFood(food: FoodEntity, date: LocalDate, mealType: MealType, grams: Double): Long = db.withTransaction {
        val macros = NutritionMath.forQuantity(food.per100(), grams)
        val entryId = mealDao.insert(
            MealEntryEntity(
                date = date,
                mealType = mealType,
                foodId = food.id,
                name = food.brand?.let { "${food.name} ($it)" } ?: food.name,
                quantityG = grams,
                proteinG = macros.proteinG,
                kcal = macros.kcal,
                carbsG = macros.carbsG,
                fatG = macros.fatG,
                eatenAt = clock.instant(),
            ),
        )
        foodDao.update(food.copy(lastUsedAt = clock.instant()))
        if (food.source == FoodSource.DISH) db.dishDao().countUse(food.id) // V2.26: "most used"
        entryId
    }

    /** "Annuler" right after a dish was added: the entry goes, and its use is not counted. */
    suspend fun undoAdd(entryId: Long, food: FoodEntity) = db.withTransaction {
        mealDao.delete(entryId)
        if (food.source == FoodSource.DISH) db.dishDao().uncountUse(food.id)
    }

    /** V2.26: the dishes of Mes plats. */
    fun dishes(): Flow<List<DishSummary>> = db.dishDao().observeSummaries()

    /** A Ciqual reference becomes one of "my" foods on first use (recents / favourites). */
    suspend fun adopt(ciqual: CiqualFoodEntity): FoodEntity {
        foodDao.findBySource(FoodSource.CIQUAL, ciqual.code.toString())?.let { return it }
        val food = FoodEntity(
            source = FoodSource.CIQUAL,
            sourceCode = ciqual.code.toString(),
            name = ciqual.name,
            kcalPer100g = ciqual.kcalPer100g,
            proteinPer100g = ciqual.proteinPer100g ?: 0.0,
            carbsPer100g = ciqual.carbsPer100g,
            fatPer100g = ciqual.fatPer100g,
        )
        return food.copy(id = foodDao.insert(food))
    }

    suspend fun resolve(choice: FoodChoice): FoodEntity = when (choice) {
        is FoodChoice.Mine -> foodDao.get(choice.food.id) ?: choice.food
        is FoodChoice.Ciqual -> adopt(choice.food)
    }

    /** Changes the quantity of an entry; values scale with the original snapshot. */
    suspend fun updateQuantity(entry: MealEntryEntity, grams: Double) = db.withTransaction {
        if (entry.quantityG <= 0 || grams <= 0) return@withTransaction
        val r = grams / entry.quantityG
        // V2.29: the entry of a drink brings its intake along (1 g = 1 ml, within 10 – 2 000 ml).
        db.hydrationDao().getIntakeByMeal(entry.id)?.let { intake ->
            val moved = intake.copy(volumeMl = grams.roundToInt().coerceIn(HydrationMath.FREE_MIN_ML, HydrationMath.FREE_MAX_ML))
            db.hydrationDao().updateIntake(moved)
            intakeMirror.upsert(moved)
        }
        mealDao.update(
            entry.copy(
                quantityG = grams,
                proteinG = entry.proteinG * r,
                kcal = entry.kcal?.times(r),
                carbsG = entry.carbsG?.times(r),
                fatG = entry.fatG?.times(r),
            ),
        )
    }

    /** V2.29: the intake of a drink linked to this entry goes with it (foreign key, cascade), in Health Connect too. */
    suspend fun deleteEntry(id: Long) {
        val intake = db.hydrationDao().getIntakeByMeal(id)
        mealDao.delete(id)
        intake?.let { intakeMirror.delete(it.id) }
    }

    /** One whey dose, from the label values of the profile; null if whey is not configured. */
    suspend fun addWheyDose(date: LocalDate, mealType: MealType): Long? {
        val p = currentProfile()
        val protein = p.wheyProteinPer100g ?: return null
        val macros = NutritionMath.forQuantity(Per100(protein, p.wheyKcalPer100g, p.wheyCarbsPer100g, p.wheyFatPer100g), p.wheyScoopG)
        return mealDao.insert(
            MealEntryEntity(
                date = date,
                mealType = mealType,
                foodId = null,
                name = "Whey",
                quantityG = p.wheyScoopG,
                proteinG = macros.proteinG,
                kcal = macros.kcal,
                carbsG = macros.carbsG,
                fatG = macros.fatG,
                isWhey = true,
                eatenAt = clock.instant(),
            ),
        )
    }

    // --- Foods -----------------------------------------------------------------------------

    fun favorites(): Flow<List<FoodEntity>> = foodDao.observeFavorites()
    fun recents(limit: Int = 30): Flow<List<FoodEntity>> = foodDao.observeRecents(limit)
    fun allFoods(): Flow<List<FoodEntity>> = foodDao.observeAll()
    suspend fun food(id: Long): FoodEntity? = foodDao.get(id)

    suspend fun setFavorite(food: FoodEntity, favorite: Boolean) = foodDao.update(food.copy(favorite = favorite))

    suspend fun saveFood(food: FoodEntity): Long =
        if (food.id == 0L) foodDao.insert(food) else food.id.also { foodDao.update(food) }

    /** Past meals keep their snapshot (foreign key is set to null). */
    suspend fun deleteFood(id: Long) = foodDao.delete(id)

    /** "My" foods first, then Ciqual references not already adopted. */
    suspend fun search(text: String, limit: Int = 50): List<FoodChoice> {
        val query = text.trim()
        if (query.length < 2) return emptyList()
        val mine = foodDao.search(query, limit)
        val adoptedCodes = mine.filter { it.source == FoodSource.CIQUAL }.mapNotNull { it.sourceCode }.toSet()
        val ciqual = ftsQuery(query)?.let { ciqualDao.search(it, limit) }.orEmpty()
            .filterNot { it.code.toString() in adoptedCodes }
        return mine.map { FoodChoice.Mine(it) } + ciqual.map { FoodChoice.Ciqual(it) }
    }

    // --- Barcode ---------------------------------------------------------------------------

    sealed interface BarcodeResult {
        data class Known(val food: FoodEntity) : BarcodeResult
        data object NotFound : BarcodeResult
        data object Offline : BarcodeResult
        data class Failed(val message: String) : BarcodeResult
        /** Found on Open Food Facts but without protein value: must be completed by hand. */
        data class Incomplete(val product: OffProduct) : BarcodeResult
    }

    /** Local cache first (works offline), then Open Food Facts; found products are cached. */
    suspend fun lookupBarcode(barcode: String): BarcodeResult {
        foodDao.findBySource(FoodSource.OFF, barcode)?.let { return BarcodeResult.Known(it) }
        foodDao.findBySource(FoodSource.CUSTOM, barcode)?.let { return BarcodeResult.Known(it) }
        return when (val result = off.product(barcode)) {
            is OffResult.Found -> {
                val p = result.product
                val protein = p.proteinPer100g ?: return BarcodeResult.Incomplete(p)
                val food = FoodEntity(
                    source = FoodSource.OFF,
                    sourceCode = barcode,
                    name = p.name,
                    brand = p.brand,
                    kcalPer100g = p.kcalPer100g,
                    proteinPer100g = protein,
                    carbsPer100g = p.carbsPer100g,
                    fatPer100g = p.fatPer100g,
                    defaultPortionG = p.servingG,
                )
                BarcodeResult.Known(food.copy(id = foodDao.insert(food)))
            }
            OffResult.NotFound -> BarcodeResult.NotFound
            OffResult.Offline -> BarcodeResult.Offline
            is OffResult.Failed -> BarcodeResult.Failed(result.message)
        }
    }

    // --- Dishes (V2.21) ---------------------------------------------------------------------

    suspend fun dish(foodId: Long): Dish? {
        val food = foodDao.get(foodId) ?: return null
        val dish = db.dishDao().get(foodId) ?: return null
        return Dish(food, dish, db.dishDao().ingredients(foodId).map { it.toDomain() })
    }

    /**
     * Creates ([foodId] 0) or updates a dish: its food gets the values per 100 g of the ingredients (of the cooked
     * dish when [cookedWeightG] is given) and one serving as its portion. Returns the food's id.
     */
    suspend fun saveDish(
        foodId: Long,
        name: String,
        ingredients: List<DishIngredient>,
        cookedWeightG: Double?,
        servings: Int,
    ): Long = db.withTransaction {
        val per100 = DishMath.per100(ingredients, cookedWeightG) ?: Per100(0.0, null, null, null)
        val base = foodDao.get(foodId)
            ?: FoodEntity(source = FoodSource.DISH, name = name, proteinPer100g = 0.0, lastUsedAt = clock.instant()) // on top of "Récents"
        val id = saveFood(
            base.copy(
                name = name.trim(),
                kcalPer100g = per100.kcal,
                proteinPer100g = per100.proteinG,
                carbsPer100g = per100.carbsG,
                fatPer100g = per100.fatG,
                defaultPortionG = DishMath.portion(ingredients, cookedWeightG, servings).takeIf { it > 0 },
            ),
        )
        val useCount = db.dishDao().get(id)?.useCount ?: 0 // an edit keeps the count
        db.dishDao().upsert(DishEntity(id, cookedWeightG?.takeIf { it > 0 }, servings.coerceAtLeast(1), useCount))
        db.dishDao().deleteIngredients(id)
        db.dishDao().insertIngredients(ingredients.mapIndexed { i, it -> it.toEntity(id, i) })
        id
    }

    /** A meal of the journal as ingredients of a new dish (whey left apart: it keeps its own count). */
    suspend fun mealAsIngredients(date: LocalDate, mealType: MealType): List<DishIngredient> =
        mealDao.getDay(date).filter { it.mealType == mealType && !it.isWhey && it.quantityG > 0 }.map { e ->
            val f = 100.0 / e.quantityG
            DishIngredient(e.name, e.quantityG, Per100(e.proteinG * f, e.kcal?.times(f), e.carbsG?.times(f), e.fatG?.times(f)))
        }

    // --- Supplements (V2.20; creatine alone until 2.19) -------------------------------------

    val supplements: Flow<List<SupplementEntity>> = supplementDao.observeAll()
    suspend fun supplementList(): List<SupplementEntity> = supplementDao.getAll()
    suspend fun supplement(id: Long): SupplementEntity? = supplementDao.get(id)

    /** Ids of the supplements taken on [date]. */
    fun supplementsTaken(date: LocalDate): Flow<Set<Long>> = supplementDao.observeDay(date).map { day -> day.map { it.supplementId }.toSet() }
    suspend fun isSupplementTaken(id: Long, date: LocalDate): Boolean = supplementDao.getIntake(id, date) != null

    suspend fun setSupplementTaken(id: Long, date: LocalDate, taken: Boolean) {
        if (taken) {
            val supplement = supplementDao.get(id) ?: return
            supplementDao.upsertIntake(SupplementIntakeEntity(supplementId = id, date = date, amount = supplement.amount, takenAt = clock.instant()))
        } else {
            supplementDao.deleteIntake(id, date)
        }
    }

    suspend fun updateSupplement(supplement: SupplementEntity) = supplementDao.update(supplement)

    /** A supplement of the user's own, switched on, at the end of the list; returns its id. */
    suspend fun addSupplement(name: String, amount: Double, unit: SupplementUnit): Long = supplementDao.insert(
        SupplementEntity(name = name.trim(), amount = amount, unit = unit, active = true, sortOrder = supplementDao.nextSortOrder()),
    )

    /** Only the user's own supplements can be deleted (with the days they were taken). */
    suspend fun deleteSupplement(id: Long) {
        if (supplementDao.get(id)?.code == null) supplementDao.delete(id)
    }

    suspend fun creatine(): SupplementEntity? = supplementDao.getByCode(SupplementEntity.CREATINE)

    /** Days creatine was taken, latest first (streak, badges). */
    fun creatineDates(): Flow<List<LocalDate>> = supplementDao.observeCreatineDates()

    suspend fun isCreatineTaken(date: LocalDate): Boolean = creatine()?.let { isSupplementTaken(it.id, date) } == true

    suspend fun setCreatineTaken(date: LocalDate, taken: Boolean) {
        creatine()?.let { setSupplementTaken(it.id, date, taken) }
    }

    companion object {
        /**
         * Builds an FTS prefix query from free text: "poulet filet" → `poulet* filet*` (all words required).
         * Accents are removed to match the unicode61 tokenizer with remove_diacritics.
         */
        fun ftsQuery(text: String): String? {
            val plain = Normalizer.normalize(text.lowercase().replace("œ", "oe").replace("æ", "ae"), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
            val words = plain.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }
            return words.takeIf { it.isNotEmpty() }?.joinToString(" ") { "$it*" }
        }
    }
}
