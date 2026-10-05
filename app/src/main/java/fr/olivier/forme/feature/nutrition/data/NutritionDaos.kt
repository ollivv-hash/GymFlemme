package fr.olivier.forme.feature.nutrition.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun deleteAll()
}

@Dao
interface CiqualDao {
    @Query("SELECT COUNT(*) FROM ciqual_food")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(foods: List<CiqualFoodEntity>)

    @Query("DELETE FROM ciqual_food")
    suspend fun deleteAll()

    @Query("INSERT INTO ciqual_fts(ciqual_fts) VALUES('rebuild')")
    suspend fun rebuildIndex()

    /** [match] is an FTS expression, e.g. `poulet* filet*`. */
    @Query(
        """SELECT c.* FROM ciqual_food c JOIN ciqual_fts f ON c.code = f.rowid
           WHERE ciqual_fts MATCH :match ORDER BY length(c.name) LIMIT :limit""",
    )
    suspend fun search(match: String, limit: Int): List<CiqualFoodEntity>

    @Query("SELECT * FROM ciqual_food WHERE code = :code")
    suspend fun get(code: Long): CiqualFoodEntity?
}

@Dao
interface FoodDao {
    @Insert
    suspend fun insert(food: FoodEntity): Long

    @Insert
    suspend fun insertAll(foods: List<FoodEntity>)

    @androidx.room.Update
    suspend fun update(food: FoodEntity)

    @Query("SELECT * FROM food WHERE id = :id")
    suspend fun get(id: Long): FoodEntity?

    @Query("SELECT * FROM food WHERE source = :source AND sourceCode = :code")
    suspend fun findBySource(source: fr.olivier.forme.feature.nutrition.domain.FoodSource, code: String): FoodEntity?

    @Query("SELECT * FROM food WHERE favorite = 1 ORDER BY name COLLATE NOCASE")
    fun observeFavorites(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE lastUsedAt IS NOT NULL ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecents(limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM food WHERE name LIKE '%' || :text || '%' OR brand LIKE '%' || :text || '%' ORDER BY favorite DESC, lastUsedAt DESC LIMIT :limit")
    suspend fun search(text: String, limit: Int): List<FoodEntity>

    @Query("DELETE FROM food WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM food")
    suspend fun getAll(): List<FoodEntity>

    @Query("DELETE FROM food")
    suspend fun deleteAll()
}

@Dao
interface MealDao {
    @Insert
    suspend fun insert(entry: MealEntryEntity): Long

    @Insert
    suspend fun insertAll(entries: List<MealEntryEntity>)

    @androidx.room.Update
    suspend fun update(entry: MealEntryEntity)

    @Query("DELETE FROM meal_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM meal_entry WHERE date = :date ORDER BY eatenAt")
    fun observeDay(date: LocalDate): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meal_entry WHERE date = :date ORDER BY eatenAt")
    suspend fun getDay(date: LocalDate): List<MealEntryEntity>

    /** Entries from [from] to [to], both included. */
    @Query("SELECT * FROM meal_entry WHERE date >= :from AND date <= :to ORDER BY eatenAt")
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<MealEntryEntity>>

    @Query("SELECT * FROM meal_entry")
    suspend fun getAll(): List<MealEntryEntity>

    @Query("DELETE FROM meal_entry")
    suspend fun deleteAll()
}

@Dao
interface CreatineDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CreatineEntryEntity)

    @Insert
    suspend fun insertAll(entries: List<CreatineEntryEntity>)

    @Query("DELETE FROM creatine_entry WHERE date = :date")
    suspend fun deleteDay(date: LocalDate)

    @Query("SELECT * FROM creatine_entry WHERE date = :date")
    suspend fun getDay(date: LocalDate): CreatineEntryEntity?

    @Query("SELECT date FROM creatine_entry ORDER BY date DESC")
    fun observeDates(): Flow<List<LocalDate>>

    @Query("SELECT * FROM creatine_entry")
    suspend fun getAll(): List<CreatineEntryEntity>

    @Query("DELETE FROM creatine_entry")
    suspend fun deleteAll()
}
