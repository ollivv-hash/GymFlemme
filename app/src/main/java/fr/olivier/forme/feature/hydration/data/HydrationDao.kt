package fr.olivier.forme.feature.hydration.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface HydrationDao {
    // --- Drinks ---

    @Query("SELECT * FROM drink ORDER BY sortOrder, id")
    fun observeDrinks(): Flow<List<DrinkEntity>>

    @Query("SELECT * FROM drink ORDER BY sortOrder, id")
    suspend fun getDrinks(): List<DrinkEntity>

    @Query("SELECT * FROM drink WHERE id = :id")
    suspend fun getDrink(id: Long): DrinkEntity?

    @Query("SELECT * FROM drink WHERE code = :code")
    suspend fun getDrinkByCode(code: String): DrinkEntity?

    @Insert
    suspend fun insertDrink(drink: DrinkEntity): Long

    @Insert
    suspend fun insertDrinks(drinks: List<DrinkEntity>)

    @Update
    suspend fun updateDrink(drink: DrinkEntity)

    @Update
    suspend fun updateDrinks(drinks: List<DrinkEntity>)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM drink")
    suspend fun nextDrinkSortOrder(): Int

    // --- Containers ---

    @Query("SELECT * FROM container ORDER BY sortOrder, id")
    fun observeContainers(): Flow<List<ContainerEntity>>

    @Query("SELECT * FROM container ORDER BY sortOrder, id")
    suspend fun getContainers(): List<ContainerEntity>

    @Query("SELECT * FROM container WHERE id = :id")
    suspend fun getContainer(id: Long): ContainerEntity?

    @Query("SELECT * FROM container WHERE isDefault = 1 AND archived = 0 LIMIT 1")
    suspend fun getDefaultContainer(): ContainerEntity?

    @Insert
    suspend fun insertContainer(container: ContainerEntity): Long

    @Insert
    suspend fun insertContainers(containers: List<ContainerEntity>)

    @Update
    suspend fun updateContainer(container: ContainerEntity)

    @Update
    suspend fun updateContainers(containers: List<ContainerEntity>)

    @Query("UPDATE container SET isDefault = (id = :id)")
    suspend fun setDefaultContainer(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM container")
    suspend fun nextContainerSortOrder(): Int

    // --- Intakes ---

    @Insert
    suspend fun insertIntake(intake: WaterIntakeEntity): Long

    @Insert
    suspend fun insertIntakes(intakes: List<WaterIntakeEntity>)

    @Update
    suspend fun updateIntake(intake: WaterIntakeEntity)

    @Query("DELETE FROM water_intake WHERE id = :id")
    suspend fun deleteIntake(id: Long)

    @Query("SELECT * FROM water_intake WHERE id = :id")
    suspend fun getIntake(id: Long): WaterIntakeEntity?

    @Query("SELECT * FROM water_intake WHERE linkedMealEntryId = :mealEntryId")
    suspend fun getIntakeByMeal(mealEntryId: Long): WaterIntakeEntity?

    @Transaction
    @Query("SELECT * FROM water_intake WHERE date = :date ORDER BY timestamp DESC, id DESC")
    fun observeDay(date: LocalDate): Flow<List<IntakeWithDrink>>

    @Query("SELECT * FROM water_intake WHERE date = :date ORDER BY timestamp")
    suspend fun getIntakes(date: LocalDate): List<WaterIntakeEntity>

    /** Latest intake time, epoch millis (reminders: not right after a drink). */
    @Query("SELECT MAX(timestamp) FROM water_intake")
    suspend fun lastIntakeAt(): Long?

    @Query(
        """SELECT date, CAST(ROUND(SUM(volumeMl * coefficientApplied)) AS INTEGER) AS countedMl FROM water_intake
           WHERE date >= :from AND date <= :to GROUP BY date ORDER BY date""",
    )
    fun observeDailyTotals(from: LocalDate, to: LocalDate): Flow<List<DailyWater>>

    @Query(
        """SELECT date, CAST(ROUND(SUM(volumeMl * coefficientApplied)) AS INTEGER) AS countedMl FROM water_intake
           WHERE date >= :from AND date <= :to GROUP BY date ORDER BY date""",
    )
    suspend fun dailyTotals(from: LocalDate, to: LocalDate): List<DailyWater>

    /** Every day with an intake (badges, statistics). */
    @Query("SELECT date, CAST(ROUND(SUM(volumeMl * coefficientApplied)) AS INTEGER) AS countedMl FROM water_intake GROUP BY date ORDER BY date")
    suspend fun allDailyTotals(): List<DailyWater>

    @Query("SELECT * FROM water_intake")
    suspend fun getAllIntakes(): List<WaterIntakeEntity>

    // --- Days (frozen goals) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(day: WaterDayEntity)

    @Insert
    suspend fun insertDays(days: List<WaterDayEntity>)

    @Query("SELECT * FROM water_day WHERE date = :date")
    suspend fun getWaterDay(date: LocalDate): WaterDayEntity?

    @Query("SELECT * FROM water_day WHERE date = :date")
    fun observeWaterDay(date: LocalDate): Flow<WaterDayEntity?>

    @Query("SELECT * FROM water_day WHERE date >= :from AND date <= :to ORDER BY date")
    fun observeDays(from: LocalDate, to: LocalDate): Flow<List<WaterDayEntity>>

    @Query("SELECT * FROM water_day ORDER BY date")
    suspend fun getAllDays(): List<WaterDayEntity>

    // --- Other modules (read only) ---

    @Query("SELECT weightKg FROM body_weight_entry ORDER BY date DESC LIMIT 1")
    fun observeLatestWeight(): Flow<Double?>

    @Query("SELECT weightKg FROM body_weight_entry ORDER BY date DESC LIMIT 1")
    suspend fun latestWeight(): Double?

    /** Weight of [date] or the last one before; the first one ever when none is that old. */
    @Query(
        """SELECT weightKg FROM body_weight_entry
           ORDER BY CASE WHEN date <= :date THEN 0 ELSE 1 END, CASE WHEN date <= :date THEN -date ELSE date END LIMIT 1""",
    )
    suspend fun weightOn(date: LocalDate): Double?

    /** Real sessions (not the seed) started between [fromMillis] included and [toMillis] excluded. */
    @Query("SELECT COUNT(*) FROM session WHERE isSeed = 0 AND startedAt >= :fromMillis AND startedAt < :toMillis")
    suspend fun countSessionsStarted(fromMillis: Long, toMillis: Long): Int

    // --- Restore ---

    @Query("DELETE FROM water_intake")
    suspend fun deleteAllIntakes()

    @Query("DELETE FROM water_day")
    suspend fun deleteAllDays()

    @Query("DELETE FROM container")
    suspend fun deleteAllContainers()

    @Query("DELETE FROM drink")
    suspend fun deleteAllDrinks()
}
