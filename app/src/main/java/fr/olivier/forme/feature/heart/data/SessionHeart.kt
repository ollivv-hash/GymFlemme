package fr.olivier.forme.feature.heart.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import fr.olivier.forme.feature.heart.domain.HeartSeries
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.heart.domain.KcalSource
import fr.olivier.forme.feature.sport.data.SessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * Heart rate of one session (V2.1): the resampled curve (see [HeartSeries]) and its summary, frozen when computed so
 * that later profile changes do not rewrite past sessions. Deleted with its session.
 */
@Entity(
    tableName = "session_heart",
    foreignKeys = [ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)],
)
data class SessionHeartEntity(
    @PrimaryKey val sessionId: Long,
    val source: HeartSource,
    val seriesStart: Instant,
    val intervalSec: Int,
    /** Encoded [HeartSeries]: "72,75,0,80", 0 = no data. */
    val bpmSeries: String,
    val avgBpm: Int,
    val minBpm: Int,
    val maxBpm: Int,
    /** Maximum heart rate the zones were computed with; null when the profile had no age nor override. */
    val hrMax: Int?,
    val zone1Sec: Int,
    val zone2Sec: Int,
    val zone3Sec: Int,
    val zone4Sec: Int,
    val zone5Sec: Int,
    val kcal: Double?,
    val kcalSource: KcalSource?,
) {
    val series: HeartSeries get() = HeartSeries.decode(seriesStart, intervalSec, bpmSeries)
    val zoneSeconds: List<Int> get() = listOf(zone1Sec, zone2Sec, zone3Sec, zone4Sec, zone5Sec)
}

@Dao
interface SessionHeartDao {
    @Query("SELECT * FROM session_heart WHERE sessionId = :sessionId")
    fun observe(sessionId: Long): Flow<SessionHeartEntity?>

    @Query("SELECT * FROM session_heart WHERE sessionId = :sessionId")
    suspend fun get(sessionId: Long): SessionHeartEntity?

    /** Calories of the sessions started in [fromMillis, toMillis), for the nutrition day balance. */
    @Query(
        "SELECT h.* FROM session_heart h JOIN session s ON s.id = h.sessionId " +
            "WHERE s.startedAt >= :fromMillis AND s.startedAt < :toMillis AND s.isSeed = 0",
    )
    fun observeStartedBetween(fromMillis: Long, toMillis: Long): Flow<List<SessionHeartEntity>>

    @Query("SELECT * FROM session_heart")
    suspend fun getAll(): List<SessionHeartEntity>

    @Upsert
    suspend fun upsert(entity: SessionHeartEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entities: List<SessionHeartEntity>)
}
