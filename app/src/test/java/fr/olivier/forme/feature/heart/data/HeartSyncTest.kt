package fr.olivier.forme.feature.heart.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.db.SeedData
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.testDatabase
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.heart.domain.KcalInterval
import fr.olivier.forme.feature.heart.domain.KcalSource
import fr.olivier.forme.feature.sport.data.SessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class HeartSyncTest {
    private val clock = TestClock()
    private val db = testDatabase(clock)
    private val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
    private val heart = HeartRepository(db.sessionHeartDao(), db.sessionDao(), db.profileDao(), db.bodyWeightDao(), clock)
    private var data: HealthConnectData? = null
    private val sync = HeartSync(
        healthConnect = object : HealthConnectSource {
            override suspend fun read(from: Instant, to: Instant) = data
        },
        heart = heart,
        sessionDao = db.sessionDao(),
        clock = clock,
    )

    @After
    fun close() = db.close()

    /** A finished 2-minute session; returns its id and start. */
    private suspend fun session(): Pair<Long, Instant> {
        clock.advance(Duration.ofDays(1))
        val id = sessions.start(SeedData.templateC.id)
        val start = clock.instant()
        clock.advance(Duration.ofMinutes(2))
        sessions.finish(id, 4, null)
        return id to start
    }

    private fun samples(start: Instant, fromSec: Int, untilSec: Int, bpm: Int) =
        (fromSec until untilSec).map { start.plusSeconds(it.toLong()) to bpm }

    @Test
    fun healthConnectFillsTheLiveGapsThenWatchCaloriesEndTheSync() = runTest {
        val (id, start) = session()
        // Live curve only for the first minute.
        heart.saveSession(id, samples(start, 0, 60, 100), HeartSource.BLE)
        data = HealthConnectData(samples(start, 0, 120, 130), emptyList())

        assertEquals(SyncOutcome.RETRY, sync.sync(id)) // no calories yet
        val merged = heart.get(id)!!
        assertEquals(HeartSource.MIXED, merged.source)
        assertEquals(List(12) { 100 } + List(12) { 130 }, merged.series.bpm) // live wins, gap filled
        assertEquals(KcalSource.ESTIMATE.takeIf { merged.kcal != null }, merged.kcalSource)

        data = data!!.copy(activeKcal = listOf(KcalInterval(start, start.plusSeconds(120), 25.0)))
        assertEquals(SyncOutcome.DONE, sync.sync(id))
        assertEquals(25.0, heart.get(id)!!.kcal!!, 1e-9)
        assertEquals(KcalSource.WATCH, heart.get(id)!!.kcalSource)
    }

    @Test
    fun withoutLiveDataHealthConnectMakesTheWholeCurve() = runTest {
        val (id, start) = session()
        data = HealthConnectData(samples(start, 0, 120, 120), listOf(KcalInterval(start, start.plusSeconds(120), 18.0)))
        assertEquals(SyncOutcome.DONE, sync.sync(id))
        assertEquals(HeartSource.HEALTH_CONNECT, heart.get(id)!!.source)
    }

    @Test
    fun caloriesAloneNeedACurveAndUnusableHealthConnectStops() = runTest {
        val (id, start) = session()
        data = HealthConnectData(emptyList(), listOf(KcalInterval(start, start.plusSeconds(120), 18.0)))
        assertEquals(SyncOutcome.DONE, sync.sync(id))
        assertNull(heart.get(id))

        data = null // Health Connect unavailable or not allowed
        assertEquals(SyncOutcome.DONE, sync.sync(id))
    }

    @Test
    fun oldSessionsAreLeftAlone() = runTest {
        val (id, start) = session()
        data = HealthConnectData(samples(start, 0, 120, 120), emptyList())
        clock.advance(Duration.ofHours(25))
        assertEquals(SyncOutcome.DONE, sync.sync(id))
        assertNull(heart.get(id))
    }
}
