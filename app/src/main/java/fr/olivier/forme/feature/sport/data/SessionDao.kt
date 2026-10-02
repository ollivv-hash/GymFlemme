package fr.olivier.forme.feature.sport.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    // --- Sessions ---------------------------------------------------------------------------

    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("SELECT * FROM session WHERE id = :id")
    suspend fun getSession(id: Long): SessionEntity?

    @Query("SELECT * FROM session WHERE id = :id")
    fun observeSession(id: Long): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?

    @Query("SELECT * FROM session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<SessionEntity?>

    @Query("SELECT * FROM session WHERE endedAt IS NOT NULL AND isSeed = 0 ORDER BY startedAt DESC")
    fun observeFinishedSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM session WHERE endedAt IS NOT NULL AND isSeed = 0 ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentSessions(limit: Int): Flow<List<SessionEntity>>

    /** Template ids of finished sessions, most recent first (seed included: it positions the rotation). */
    @Query("SELECT templateId FROM session WHERE endedAt IS NOT NULL AND templateId IS NOT NULL ORDER BY endedAt DESC")
    fun observeFinishedTemplateIds(): Flow<List<Long>>

    /** Previous finished, non-seed session of the same template, started before [before]. */
    @Query(
        """SELECT * FROM session WHERE templateId = :templateId AND endedAt IS NOT NULL AND isSeed = 0
           AND startedAt < :beforeMillis ORDER BY startedAt DESC LIMIT 1""",
    )
    suspend fun getPreviousSessionOfTemplate(templateId: Long, beforeMillis: Long): SessionEntity?

    /** Real sessions (running or finished) started in [fromMillis, toMillis). */
    @Query("SELECT COUNT(*) FROM session WHERE isSeed = 0 AND startedAt >= :fromMillis AND startedAt < :toMillis")
    fun observeSessionCount(fromMillis: Long, toMillis: Long): Flow<Int>

    @Query("DELETE FROM session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM session")
    suspend fun getAllSessions(): List<SessionEntity>

    @Query("DELETE FROM session")
    suspend fun deleteAllSessions()

    // --- Session exercises ------------------------------------------------------------------

    @Insert
    suspend fun insertSessionExercise(line: SessionExerciseEntity): Long

    @Insert
    suspend fun insertSessionExercises(lines: List<SessionExerciseEntity>)

    @Update
    suspend fun updateSessionExercises(lines: List<SessionExerciseEntity>)

    @Query("UPDATE session_exercise SET status = :status WHERE id = :id")
    suspend fun setSessionExerciseStatus(id: Long, status: fr.olivier.forme.feature.sport.domain.SessionExerciseStatus)

    @Transaction
    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId ORDER BY position")
    fun observeSessionLines(sessionId: Long): Flow<List<SessionLineWithExercise>>

    @Transaction
    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId ORDER BY position")
    suspend fun getSessionLines(sessionId: Long): List<SessionLineWithExercise>

    @Query("SELECT * FROM session_exercise WHERE sessionId = :sessionId AND exerciseId = :exerciseId ORDER BY position LIMIT 1")
    suspend fun getSessionExercise(sessionId: Long, exerciseId: Long): SessionExerciseEntity?

    @Query("DELETE FROM session_exercise WHERE id = :id")
    suspend fun deleteSessionExercise(id: Long)

    @Query("SELECT * FROM session_exercise")
    suspend fun getAllSessionExercises(): List<SessionExerciseEntity>

    // --- Sets -------------------------------------------------------------------------------

    @Insert
    suspend fun insertSet(set: SetEntryEntity): Long

    @Insert
    suspend fun insertSets(sets: List<SetEntryEntity>)

    @Query("DELETE FROM set_entry WHERE id = :id")
    suspend fun deleteSet(id: Long)

    @Query("DELETE FROM set_entry WHERE sessionId = :sessionId AND exerciseId = :exerciseId")
    suspend fun deleteSetsOf(sessionId: Long, exerciseId: Long)

    @Query("SELECT * FROM set_entry WHERE sessionId = :sessionId ORDER BY completedAt")
    fun observeSets(sessionId: Long): Flow<List<SetEntryEntity>>

    @Query("SELECT * FROM set_entry WHERE sessionId = :sessionId ORDER BY completedAt")
    suspend fun getSets(sessionId: Long): List<SetEntryEntity>

    /**
     * Working sets of [exerciseId] in the most recent finished session containing it,
     * excluding [excludeSessionId]. The seed session is included only when [includeSeed].
     */
    @Query(
        """SELECT * FROM set_entry WHERE exerciseId = :exerciseId AND isWarmup = 0 AND sessionId = (
             SELECT s.id FROM session s JOIN set_entry e ON e.sessionId = s.id
             WHERE e.exerciseId = :exerciseId AND e.isWarmup = 0 AND s.id != :excludeSessionId
               AND s.endedAt IS NOT NULL AND (:includeSeed OR s.isSeed = 0)
             ORDER BY s.startedAt DESC LIMIT 1)
           ORDER BY setIndex""",
    )
    suspend fun getLastWorkingSets(exerciseId: Long, excludeSessionId: Long, includeSeed: Boolean): List<SetEntryEntity>

    /** All working sets of an exercise in finished sessions (seed included, flagged), oldest first. */
    @Query(
        """SELECT e.sessionId, s.startedAt, s.isSeed, e.setIndex, e.loadKg, e.reps, e.durationSec, e.rir,
             (SELECT MIN(se.targetRepsMin) FROM session_exercise se
               WHERE se.sessionId = e.sessionId AND se.exerciseId = e.exerciseId) AS targetRepsMin
           FROM set_entry e JOIN session s ON s.id = e.sessionId
           WHERE e.exerciseId = :exerciseId AND e.isWarmup = 0 AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt, e.setIndex""",
    )
    fun observeExerciseHistory(exerciseId: Long): Flow<List<ExerciseSetRow>>

    @Query(
        """SELECT e.sessionId, s.startedAt, s.isSeed, e.setIndex, e.loadKg, e.reps, e.durationSec, e.rir,
             (SELECT MIN(se.targetRepsMin) FROM session_exercise se
               WHERE se.sessionId = e.sessionId AND se.exerciseId = e.exerciseId) AS targetRepsMin
           FROM set_entry e JOIN session s ON s.id = e.sessionId
           WHERE e.exerciseId = :exerciseId AND e.isWarmup = 0 AND s.endedAt IS NOT NULL AND s.id != :excludeSessionId
           ORDER BY s.startedAt, e.setIndex""",
    )
    suspend fun getExerciseHistory(exerciseId: Long, excludeSessionId: Long): List<ExerciseSetRow>

    @Query("SELECT * FROM set_entry")
    suspend fun getAllSets(): List<SetEntryEntity>

    // --- Cardio -----------------------------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCardio(entry: CardioEntryEntity): Long

    @Insert
    suspend fun insertCardios(entries: List<CardioEntryEntity>)

    @Query("DELETE FROM cardio_entry WHERE id = :id")
    suspend fun deleteCardio(id: Long)

    @Query("SELECT * FROM cardio_entry WHERE sessionId = :sessionId")
    fun observeCardio(sessionId: Long): Flow<List<CardioEntryEntity>>

    @Query("SELECT * FROM cardio_entry WHERE sessionId = :sessionId")
    suspend fun getCardio(sessionId: Long): List<CardioEntryEntity>

    @Query(
        """SELECT c.* FROM cardio_entry c JOIN session s ON s.id = c.sessionId
           WHERE c.exerciseId = :exerciseId AND s.id != :excludeSessionId AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt DESC LIMIT 1""",
    )
    suspend fun getLastCardio(exerciseId: Long, excludeSessionId: Long): CardioEntryEntity?

    @Query(
        """SELECT c.* FROM cardio_entry c JOIN session s ON s.id = c.sessionId
           WHERE c.exerciseId = :exerciseId AND s.endedAt IS NOT NULL ORDER BY s.startedAt""",
    )
    fun observeCardioHistory(exerciseId: Long): Flow<List<CardioEntryEntity>>

    @Query("SELECT * FROM cardio_entry")
    suspend fun getAllCardio(): List<CardioEntryEntity>
}
