package fr.olivier.forme.feature.badges.data

import fr.olivier.forme.feature.badges.domain.Badge
import fr.olivier.forme.feature.badges.domain.BadgeHistory
import fr.olivier.forme.feature.badges.domain.Badges
import fr.olivier.forme.feature.sport.domain.RecordsCalculator
import fr.olivier.forme.feature.sport.domain.SessionSets
import fr.olivier.forme.feature.sport.domain.WorkSet
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Badges earned (V2.17), recomputed from the history after a session, a creatine dose and at startup. A badge whose
 * day is today or yesterday is announced; older ones (history already there at the update, restored backup) are
 * stored silently, with the day they were really earned.
 */
@Singleton
class BadgeRepository @Inject constructor(
    private val dao: BadgeDao,
    private val clock: Clock,
) {
    fun observeAll(): Flow<List<BadgeEntity>> = dao.observeAll()

    /** Stores the badges newly earned; returns those to announce. */
    suspend fun evaluate(): List<Badge> {
        val known = dao.getAll().map { it.id }.toSet()
        val today = LocalDate.now(clock)
        val earned = Badges.earned(history()).filterKeys { it.name !in known }
        if (earned.isEmpty()) return emptyList()
        val recent = { day: LocalDate -> !day.isBefore(today.minusDays(ANNOUNCE_DAYS)) }
        dao.insertAll(earned.map { (badge, day) -> BadgeEntity(badge.name, day, seen = !recent(day)) })
        return earned.filterValues(recent).keys.sortedBy { it.ordinal }
    }

    suspend fun markSeen(badges: Collection<Badge>) {
        if (badges.isNotEmpty()) dao.markSeen(badges.map { it.name })
    }

    suspend fun history(): BadgeHistory {
        val zone = clock.zone
        val sets = dao.workingSets()
        val realSets = sets.filterNot { it.isSeed }
        return BadgeHistory(
            sessions = dao.sessionStarts().map { it.atZone(zone).toLocalDateTime() },
            recordDays = recordDays(sets).map { it.atZone(zone).toLocalDate() },
            exercisesDone = (realSets.map { it.exerciseId to it.startedAt } + dao.cardioDone().map { it.exerciseId to it.startedAt })
                .map { (exercise, at) -> exercise to at.atZone(zone).toLocalDate() },
            creatineDays = dao.creatineDays(),
        )
    }

    /** Starts of the real sessions beating a record of one of their exercises (seed sessions only as a baseline). */
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

    private companion object {
        /** Earned today or yesterday: announced; earlier: stored silently. */
        const val ANNOUNCE_DAYS = 1L
    }
}
