package fr.olivier.forme.feature.nutrition.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import fr.olivier.forme.feature.nutrition.domain.DishIngredient
import fr.olivier.forme.feature.nutrition.domain.Per100

/**
 * A dish of one's own (V2.21): a food of source DISH whose values per 100 g come from its ingredients. Deleting the
 * food deletes the dish and its ingredients.
 */
@Entity(
    tableName = "dish",
    foreignKeys = [ForeignKey(entity = FoodEntity::class, parentColumns = ["id"], childColumns = ["foodId"], onDelete = ForeignKey.CASCADE)],
)
data class DishEntity(
    @PrimaryKey val foodId: Long,
    /** Weight of the whole cooked dish; null = the raw ingredients' weight. */
    val cookedWeightG: Double? = null,
    val servings: Int = 1,
    /** V2.26: times added to a meal (the "most used" order of Mes plats). */
    @ColumnInfo(defaultValue = "0") val useCount: Int = 0,
)

/**
 * An ingredient, with a copy of its name and values: a food deleted or changed later leaves the dish as it was.
 */
@Entity(
    tableName = "dish_ingredient",
    foreignKeys = [ForeignKey(entity = DishEntity::class, parentColumns = ["foodId"], childColumns = ["dishId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("dishId")],
)
data class DishIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dishId: Long,
    val position: Int,
    val name: String,
    val grams: Double,
    val kcalPer100g: Double? = null,
    val proteinPer100g: Double,
    val carbsPer100g: Double? = null,
    val fatPer100g: Double? = null,
)

fun DishIngredientEntity.toDomain() = DishIngredient(name, grams, Per100(proteinPer100g, kcalPer100g, carbsPer100g, fatPer100g))

fun DishIngredient.toEntity(dishId: Long, position: Int) = DishIngredientEntity(
    dishId = dishId, position = position, name = name, grams = grams,
    kcalPer100g = per100.kcal, proteinPer100g = per100.proteinG, carbsPer100g = per100.carbsG, fatPer100g = per100.fatG,
)

/** A dish with its food and ingredients, in their order. */
data class Dish(val food: FoodEntity, val dish: DishEntity, val ingredients: List<DishIngredient>)

@Dao
interface DishDao {
    @Query("SELECT * FROM dish WHERE foodId = :foodId")
    suspend fun get(foodId: Long): DishEntity?

    @Query("SELECT * FROM dish_ingredient WHERE dishId = :dishId ORDER BY position")
    suspend fun ingredients(dishId: Long): List<DishIngredientEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dish: DishEntity)

    @Query("DELETE FROM dish_ingredient WHERE dishId = :dishId")
    suspend fun deleteIngredients(dishId: Long)

    @Insert
    suspend fun insertIngredients(ingredients: List<DishIngredientEntity>)

    @Query("SELECT * FROM dish")
    suspend fun getAll(): List<DishEntity>

    @Insert
    suspend fun insertAll(dishes: List<DishEntity>)

    @Query("SELECT * FROM dish_ingredient")
    suspend fun getAllIngredients(): List<DishIngredientEntity>

    @Query("UPDATE dish SET useCount = useCount + 1 WHERE foodId = :foodId")
    suspend fun countUse(foodId: Long)

    @Query("UPDATE dish SET useCount = MAX(useCount - 1, 0) WHERE foodId = :foodId")
    suspend fun uncountUse(foodId: Long)

    /** V2.26: the dishes with their food, for Mes plats. */
    @Query("SELECT food.*, dish.useCount AS useCount FROM food JOIN dish ON dish.foodId = food.id WHERE food.source = 'DISH'")
    fun observeSummaries(): kotlinx.coroutines.flow.Flow<List<DishSummary>>
}

/** A dish in Mes plats (V2.26): its food (values, portion, pinned = favourite, last use) and how often it was added. */
data class DishSummary(@androidx.room.Embedded val food: FoodEntity, val useCount: Int)
