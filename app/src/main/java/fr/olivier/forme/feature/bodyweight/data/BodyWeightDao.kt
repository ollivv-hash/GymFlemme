package fr.olivier.forme.feature.bodyweight.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BodyWeightDao {
    @Query("SELECT * FROM body_weight_entry ORDER BY date")
    fun observeAll(): Flow<List<BodyWeightEntity>>

    @Query("SELECT * FROM body_weight_entry ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): BodyWeightEntity?

    /** Same date → replaced (unique index on date). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BodyWeightEntity): Long

    @Insert
    suspend fun insertAll(entries: List<BodyWeightEntity>)

    @Query("DELETE FROM body_weight_entry WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM body_weight_entry")
    suspend fun getAll(): List<BodyWeightEntity>

    @Query("DELETE FROM body_weight_entry")
    suspend fun deleteAll()
}
