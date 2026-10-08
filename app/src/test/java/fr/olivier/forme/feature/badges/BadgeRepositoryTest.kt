package fr.olivier.forme.feature.badges

import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.db.SeedData
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.testDatabase
import fr.olivier.forme.feature.badges.data.BadgeRepository
import fr.olivier.forme.feature.badges.domain.Badge
import fr.olivier.forme.feature.sport.data.SessionRepository
import fr.olivier.forme.feature.sport.data.SetEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate

/** V2.17: badges of the history are stored silently, new ones are announced once; records start after the seed. */
@RunWith(AndroidJUnit4::class)
class BadgeRepositoryTest {
    private val clock = TestClock()
    private val db = testDatabase(clock)
    private val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
    private val badges = BadgeRepository(db.badgeDao(), clock)

    @After
    fun close() = db.close()

    private suspend fun chestPressSession(loadKg: Double) {
        val id = sessions.start(SeedData.templateC.id)
        clock.advance(Duration.ofMinutes(2))
        sessions.logSet(SetEntryEntity(sessionId = id, exerciseId = SeedData.chestPress.id, setIndex = 0, loadKg = loadKg, reps = 10, completedAt = clock.instant()))
        clock.advance(Duration.ofMinutes(30))
        sessions.finish(id, 3, null)
    }

    @Test
    fun pastBadgesAreSilentNewOnesAnnouncedOnce() = runTest {
        // First session at the seed's load: "Pas mort", but no record (the seed is the baseline).
        chestPressSession(15.0)
        val firstDay = LocalDate.now(clock)
        clock.advance(Duration.ofDays(10))
        assertEquals(emptyList<Badge>(), badges.evaluate()) // earned 10 days ago: stored, not announced
        val stored = db.badgeDao().getAll().single()
        assertEquals(Badge.NOT_DEAD.name, stored.id)
        assertEquals(firstDay, stored.earnedAt)
        assertTrue(stored.seen)

        // Today: heavier than ever → first record, announced.
        chestPressSession(17.5)
        assertEquals(listOf(Badge.FIRST_RECORD), badges.evaluate())
        assertEquals(emptyList<Badge>(), badges.evaluate()) // already stored
        assertEquals(listOf(false), db.badgeDao().getAll().filter { it.id == Badge.FIRST_RECORD.name }.map { it.seen })

        badges.markSeen(listOf(Badge.FIRST_RECORD))
        assertTrue(badges.observeAll().first().all { it.seen })
    }
}
