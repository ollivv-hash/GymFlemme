package fr.olivier.forme.feature.badges.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import fr.olivier.forme.feature.badges.domain.Award
import fr.olivier.forme.feature.badges.domain.Badge
import fr.olivier.forme.feature.badges.domain.BadgeHistory
import fr.olivier.forme.feature.badges.domain.Badges
import fr.olivier.forme.feature.badges.domain.Challenge
import fr.olivier.forme.feature.badges.domain.ChallengeEligibility
import fr.olivier.forme.feature.badges.domain.Challenges
import fr.olivier.forme.feature.badges.domain.SessionFact
import fr.olivier.forme.feature.badges.domain.Tier
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.sport.domain.ExerciseType
import fr.olivier.forme.feature.sport.domain.RecordsCalculator
import fr.olivier.forme.feature.sport.domain.SessionSets
import fr.olivier.forme.feature.sport.domain.WorkSet
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** An award as stored. */
fun BadgeEntity.award(): Award? = Badge.byId(id)?.let { badge -> Award(badge, Tier.of(tier)) }

/**
 * Badges earned (V2.17; tiers, points and level V2.18), recomputed from the history after a session, a creatine dose,
 * a meal and at startup. Idempotent: an award is stored once (insert ignored when known) and never removed.
 *
 * - First run of V2.18: everything the history already holds is stored unseen and shown once in "Tes badges déjà
 *   gagnés".
 * - Afterwards: an award whose day is today or yesterday is announced; older ones (restored backup) are stored
 *   silently, with the day they were really earned.
 */
@Singleton
class BadgeRepository @Inject constructor(
    private val dao: BadgeDao,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    fun observeAll(): Flow<List<BadgeEntity>> = dao.observeAll()

    /** The awards of the history to show in the single recap "Tes badges déjà gagnés". */
    val retroPending: Flow<Boolean> = dataStore.data.map { it[RETRO_PENDING] == true }

    /** Last level announced (1 until a level-up was shown). */
    val announcedLevel: Flow<Int> = dataStore.data.map { it[LEVEL_ANNOUNCED] ?: 1 }

    fun observeChallenges(): Flow<List<ChallengeEntity>> = dao.observeChallenges()

    /** Emits when the history may have changed (the progress shown is recomputed). */
    fun observeHistoryChanges(): Flow<Long> = dao.observeHistorySize()

    fun currentMonth(): YearMonth = YearMonth.now(clock)

    /** Notification of the monthly challenge (V2.19), on by default. */
    val challengeNotification: Flow<Boolean> = dataStore.data.map { it[CHALLENGE_NOTIFICATION] ?: true }

    suspend fun setChallengeNotification(enabled: Boolean) {
        dataStore.edit { it[CHALLENGE_NOTIFICATION] = enabled }
    }

    /**
     * Stores the awards newly earned and the challenges newly met; returns the awards to announce. The challenge of
     * the current month is drawn first if needed; a challenge met is always announced.
     */
    suspend fun evaluate(): List<Award> = mutex.withLock {
        val history = history()
        val known = dao.getAll().map { it.id to it.tier }.toSet()
        val earned = Badges.earned(history).filterKeys { it.key() !in known }
        val firstRun = dataStore.data.first()[BACKFILLED] != true
        val today = LocalDate.now(clock)
        val announced = if (firstRun) earned.keys else earned.filterValues { !it.isBefore(today.minusDays(ANNOUNCE_DAYS)) }.keys
        if (earned.isNotEmpty()) {
            dao.insertAll(earned.map { (award, day) -> BadgeEntity(award.badge.name, award.key().second, day, seen = award !in announced) })
        }
        if (firstRun) {
            dataStore.edit {
                it[BACKFILLED] = true
                if (earned.isNotEmpty()) it[RETRO_PENDING] = true
            }
        }
        drawChallenge(YearMonth.from(today))
        dao.getChallenges().filter { it.completedAt == null }.forEach { entity ->
            val challenge = Challenge.byId(entity.challenge) ?: return@forEach
            Challenges.progress(challenge, YearMonth.parse(entity.month), history).completedOn?.let { day ->
                dao.completeChallenge(entity.month, day, seen = false)
            }
        }
        announced.sortedWith(compareBy({ it.badge.ordinal }, { it.tier?.number ?: 0 }))
    }

    /**
     * The challenge of [month], drawn and stored the first time: never last month's (the stored one, else the one its
     * draw would give), among the challenges the data allows.
     */
    suspend fun drawChallenge(month: YearMonth): Challenge? {
        dao.getChallenge(month.toString())?.let { return Challenge.byId(it.challenge) }
        val eligible = Challenges.eligible(eligibility(month))
        val previousMonth = month.minusMonths(1)
        val previous = dao.getChallenge(previousMonth.toString())?.let { Challenge.byId(it.challenge) }
            ?: Challenges.draw(previousMonth, eligible, null)
        val challenge = Challenges.draw(month, eligible, previous) ?: return null
        dao.insertChallenges(listOf(ChallengeEntity(month.toString(), challenge.name)))
        return dao.getChallenge(month.toString())?.let { Challenge.byId(it.challenge) }
    }

    suspend fun eligibility(month: YearMonth): ChallengeEligibility {
        val previous = month.minusMonths(1)
        return ChallengeEligibility(
            hasRower = dao.activeExercises().any { Badges.isRower(it.name, it.libraryId) },
            mealsLastMonth = dao.mealCount(previous.atDay(1), previous.atEndOfMonth()) > 0,
            rotationSize = dao.rotation().size,
        )
    }

    /** The announcement was read: every award and challenge is seen, the level reached is announced. */
    suspend fun acknowledge(level: Int) {
        dao.markAllSeen()
        dao.markAllChallengesSeen()
        dataStore.edit {
            it[LEVEL_ANNOUNCED] = level
            it[RETRO_PENDING] = false
        }
    }

    suspend fun history(): BadgeHistory {
        val zone = clock.zone
        fun Instant.day() = atZone(zone).toLocalDate()
        val sets = dao.workingSets()
        val real = sets.filterNot { it.isSeed }
        val cardio = dao.cardio()
        return BadgeHistory(
            today = LocalDate.now(clock),
            sessions = dao.sessions().map {
                SessionFact(it.startedAt.atZone(zone).toLocalDateTime(), it.endedAt.atZone(zone).toLocalDateTime(), it.templateId)
            },
            recordDays = recordDays(sets).map { it.day() },
            exercisesDone = real.map { it.exerciseId to it.startedAt.day() } + cardio.map { it.exerciseId to it.startedAt.day() },
            liftedKg = real.filter { it.type == ExerciseType.WEIGHTED }.mapNotNull { set ->
                val load = set.loadKg ?: return@mapNotNull null
                val reps = set.reps ?: return@mapNotNull null
                set.startedAt.day() to load * reps
            },
            rowedMetres = cardio.filter { Badges.isRower(it.name, it.libraryId) }.mapNotNull { row ->
                row.distanceM?.let { row.startedAt.day() to it }
            },
            creatineDays = dao.creatineDays(),
            proteinDays = Badges.proteinDays(
                dao.proteinByDay().map { it.date to it.proteinG },
                dao.weights().map { it.date to it.weightKg },
                dao.proteinGPerKg() ?: ProfileEntity.DEFAULT_PROTEIN_G_PER_KG,
            ),
            musclesDone = real.map { it.muscleGroup to it.startedAt.day() },
            rotation = dao.rotation().toSet(),
        )
    }

    /**
     * Starts of the real sessions beating a record of one of their exercises, once per exercise and session (seed
     * sessions only as a baseline).
     */
    private fun recordDays(sets: List<BadgeSetRow>) = sets.groupBy { it.exerciseId }.values.flatMap { rows ->
        val sessions = rows.groupBy { it.sessionId }.values.sortedBy { it.first().startedAt }
        sessions.mapIndexedNotNull { index, current ->
            val first = current.first()
            if (first.isSeed) return@mapIndexedNotNull null
            val previous = sessions.take(index).map { it.toSessionSets() }
            val records = RecordsCalculator.newRecords(current.toSessionSets(), previous)
            first.startedAt.takeIf { records.loadKg != null || records.volumeKg != null }
        }
    }

    private fun List<BadgeSetRow>.toSessionSets() =
        SessionSets(first().sessionId, first().targetRepsMin, map { WorkSet(loadKg = it.loadKg, reps = it.reps) })

    private fun Award.key() = badge.name to (tier?.number ?: 0)

    private companion object {
        /** Earned today or yesterday: announced; earlier: stored silently. */
        const val ANNOUNCE_DAYS = 1L
        val BACKFILLED = booleanPreferencesKey("badges_v2_backfilled")
        val RETRO_PENDING = booleanPreferencesKey("badges_retro_pending")
        val LEVEL_ANNOUNCED = intPreferencesKey("badges_level_announced")
        val CHALLENGE_NOTIFICATION = booleanPreferencesKey("challenge_notification")
    }
}
