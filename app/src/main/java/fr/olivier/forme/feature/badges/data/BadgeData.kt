package fr.olivier.forme.feature.badges.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.MuscleGroup
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

/**
 * A badge earned (V2.17; tiers V2.18): [id] is a Badge name, [tier] its tier number (0 for a badge without tiers),
 * [earnedAt] the day its condition was met, [seen] once announced. Never deleted, except by a restore.
 */
@Entity(tableName = "badge", primaryKeys = ["id", "tier"])
data class BadgeEntity(
    val id: String,
    val tier: Int,
    val earnedAt: LocalDate,
    val seen: Boolean,
)

/**
 * The challenge of a month (V2.19): [month] as "2026-10", [challenge] a Challenge name, drawn once and kept (the draw
 * depends on the data of the moment); [completedAt] the day it was met, [seen] once announced.
 */
@Entity(tableName = "monthly_challenge")
data class ChallengeEntity(
    @PrimaryKey val month: String,
    val challenge: String,
    val completedAt: LocalDate? = null,
    val seen: Boolean = false,
)

data class RowerCandidateRow(val name: String, val libraryId: String?)

/** A finished real session holding at least one set or cardio entry. */
data class BadgeSessionRow(val id: Long, val startedAt: Instant, val endedAt: Instant, val templateId: Long?)

/** A working set of a finished session, with what the records need (seed sessions included: they are the baseline). */
data class BadgeSetRow(
    val sessionId: Long,
    val startedAt: Instant,
    val isSeed: Boolean,
    val exerciseId: Long,
    val type: ExerciseType,
    val muscleGroup: MuscleGroup,
    val loadKg: Double?,
    val reps: Int?,
    val targetRepsMin: Int?,
)

/** A cardio entry of a finished real session, and whether its exercise is the rower. */
data class BadgeCardioRow(val exerciseId: Long, val startedAt: Instant, val distanceM: Int?, val name: String, val libraryId: String?)

data class DayProteinRow(val date: LocalDate, val proteinG: Double)

data class DayWeightRow(val date: LocalDate, val weightKg: Double)

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badge ORDER BY earnedAt, id, tier")
    fun observeAll(): Flow<List<BadgeEntity>>

    @Query("SELECT * FROM badge")
    suspend fun getAll(): List<BadgeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(badges: List<BadgeEntity>)

    @Query("UPDATE badge SET seen = 1 WHERE seen = 0")
    suspend fun markAllSeen()

    @Query("DELETE FROM badge")
    suspend fun deleteAll()

    @Query(
        """SELECT s.id AS id, s.startedAt AS startedAt, s.endedAt AS endedAt, s.templateId AS templateId FROM session s
           WHERE s.endedAt IS NOT NULL AND s.isSeed = 0
             AND (EXISTS (SELECT 1 FROM set_entry e WHERE e.sessionId = s.id)
                  OR EXISTS (SELECT 1 FROM cardio_entry c WHERE c.sessionId = s.id))""",
    )
    suspend fun sessions(): List<BadgeSessionRow>

    @Query(
        """SELECT s.id AS sessionId, s.startedAt AS startedAt, s.isSeed AS isSeed, e.exerciseId AS exerciseId,
                  x.type AS type, x.muscleGroup AS muscleGroup, e.loadKg AS loadKg, e.reps AS reps,
                  (SELECT MIN(l.targetRepsMin) FROM session_exercise l
                   WHERE l.sessionId = e.sessionId AND l.exerciseId = e.exerciseId) AS targetRepsMin
           FROM set_entry e JOIN session s ON s.id = e.sessionId JOIN exercise x ON x.id = e.exerciseId
           WHERE e.isWarmup = 0 AND s.endedAt IS NOT NULL
           ORDER BY s.startedAt""",
    )
    suspend fun workingSets(): List<BadgeSetRow>

    @Query(
        """SELECT c.exerciseId AS exerciseId, s.startedAt AS startedAt, c.distanceM AS distanceM, x.name AS name,
                  x.libraryId AS libraryId
           FROM cardio_entry c JOIN session s ON s.id = c.sessionId JOIN exercise x ON x.id = c.exerciseId
           WHERE s.endedAt IS NOT NULL AND s.isSeed = 0""",
    )
    suspend fun cardio(): List<BadgeCardioRow>

    @Query(
        """SELECT i.date FROM supplement_intake i JOIN supplement s ON s.id = i.supplementId
           WHERE s.code = 'creatine'""",
    )
    suspend fun creatineDays(): List<LocalDate>

    @Query("SELECT date, SUM(proteinG) AS proteinG FROM meal_entry GROUP BY date")
    suspend fun proteinByDay(): List<DayProteinRow>

    @Query("SELECT date, weightKg FROM body_weight_entry ORDER BY date")
    suspend fun weights(): List<DayWeightRow>

    @Query("SELECT proteinTargetGPerKg FROM profile WHERE id = 1")
    suspend fun proteinGPerKg(): Double?

    @Query("SELECT id FROM workout_template WHERE orderInRotation IS NOT NULL AND archived = 0")
    suspend fun rotation(): List<Long>

    // --- Monthly challenges (V2.19) ---

    @Query("SELECT * FROM monthly_challenge ORDER BY month")
    fun observeChallenges(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM monthly_challenge ORDER BY month")
    suspend fun getChallenges(): List<ChallengeEntity>

    @Query("SELECT * FROM monthly_challenge WHERE month = :month")
    suspend fun getChallenge(month: String): ChallengeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertChallenges(challenges: List<ChallengeEntity>)

    @Query("UPDATE monthly_challenge SET completedAt = :day, seen = :seen WHERE month = :month AND completedAt IS NULL")
    suspend fun completeChallenge(month: String, day: LocalDate, seen: Boolean)

    @Query("UPDATE monthly_challenge SET seen = 1 WHERE seen = 0")
    suspend fun markAllChallengesSeen()

    @Query("DELETE FROM monthly_challenge")
    suspend fun deleteAllChallenges()

    @Query("SELECT name, libraryId FROM exercise WHERE archived = 0")
    suspend fun activeExercises(): List<RowerCandidateRow>

    /** Changes with every table the badges and challenges are computed from (progress refresh). */
    @Query(
        """SELECT (SELECT COUNT(*) FROM session WHERE endedAt IS NOT NULL) + (SELECT COUNT(*) FROM set_entry)
                  + (SELECT COUNT(*) FROM cardio_entry) + (SELECT COUNT(*) FROM meal_entry)
                  + (SELECT COUNT(*) FROM supplement_intake i JOIN supplement s ON s.id = i.supplementId
                     WHERE s.code = 'creatine') + (SELECT COUNT(*) FROM body_weight_entry)
                  + (SELECT COUNT(*) FROM workout_template)""",
    )
    fun observeHistorySize(): Flow<Long>

    @Query("SELECT COUNT(*) FROM meal_entry WHERE date BETWEEN :from AND :to")
    suspend fun mealCount(from: LocalDate, to: LocalDate): Int
}
