package fr.olivier.forme.feature.badges.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/** A badge earned (V2.17): [id] is a Badge name, [earnedAt] the day its condition was met, [seen] once announced. */
@Entity(tableName = "badge")
data class BadgeEntity(
    @PrimaryKey val id: String,
    val earnedAt: LocalDate,
    val seen: Boolean,
)

/** A working set of a finished session, with what the records need (seed sessions included: they are the baseline). */
data class BadgeSetRow(
    val sessionId: Long,
    val startedAt: Instant,
    val isSeed: Boolean,
    val exerciseId: Long,
    val loadKg: Double?,
    val reps: Int?,
    val targetRepsMin: Int?,
)

data class ExerciseDoneRow(val exerciseId: Long, val startedAt: Instant)

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badge ORDER BY earnedAt, id")
    fun observeAll(): Flow<List<BadgeEntity>>

    @Query("SELECT * FROM badge")
    suspend fun getAll(): List<BadgeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(badges: List<BadgeEntity>)

    @Query("UPDATE badge SET seen = 1 WHERE id IN (:ids)")
    suspend fun markSeen(ids: List<String>)

    @Query("DELETE FROM badge")
    suspend fun deleteAll()

    @Query("SELECT startedAt FROM session WHERE endedAt IS NOT NULL AND isSeed = 0")
    suspend fun sessionStarts(): List<Instant>

    @Query(
        """SELECT s.id AS sessionId, s.startedAt AS startedAt, s.isSeed AS isSeed, e.exerciseId AS exerciseId,
                  e.loadKg AS loadKg, e.reps AS reps,
                  (SELECT MIN(l.targetRepsMin) FROM session_exercise l
                   WHERE l.sessionId = e.sessionId AND l.exerciseId = e.exerciseId) AS targetRepsMin
           FROM set_entry e JOIN session s ON s.id = e.sessionId
           WHERE e.isWarmup = 0 AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt""",
    )
    suspend fun workingSets(): List<BadgeSetRow>

    @Query(
        """SELECT c.exerciseId AS exerciseId, s.startedAt AS startedAt
           FROM cardio_entry c JOIN session s ON s.id = c.sessionId
           WHERE s.endedAt IS NOT NULL AND s.isSeed = 0""",
    )
    suspend fun cardioDone(): List<ExerciseDoneRow>

    @Query("SELECT date FROM creatine_entry")
    suspend fun creatineDays(): List<LocalDate>
}
