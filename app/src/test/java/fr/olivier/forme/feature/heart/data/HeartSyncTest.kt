package fr.olivier.forme.feature.heart.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.db.SeedData
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.testDatabase
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.heart.domain.KcalInterval
import fr.olivier.forme.feature.heart.domain.KcalSource
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.heart.domain.SourcedSample
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.sport.data.SessionRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

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

    private companion object {
        const val WATCH = "com.xiaomi.wearable"
    }

    /** A finished 2-minute session; returns its id and start. */
    private suspend fun session(): Pair<Long, Instant> {
        clock.advance(Duration.ofDays(1))
        val id = sessions.start(SeedData.templateC.id)
        val start = clock.instant()
        clock.advance(Duration.ofMinutes(2))
        sessions.finish(id, 4, null)
        return id to start
    }

    private fun samples(start: Instant, fromSec: Int, untilSec: Int, bpm: Int, origin: String = WATCH) =
        (fromSec until untilSec).map { SourcedSample(start.plusSeconds(it.toLong()), bpm, origin) }

    private fun kcal(start: Instant, endSec: Long, kcal: Double, origin: String = WATCH) =
        KcalInterval(start, start.plusSeconds(endSec), kcal, origin)

    @Test
    fun healthConnectFillsTheLiveGapsThenWatchCaloriesEndTheSync() = runTest {
        val (id, start) = session()
        // Live curve only for the first minute.
        heart.saveSession(id, samples(start, 0, 60, 100).map { it.time to it.bpm }, HeartSource.BLE)
        data = HealthConnectData(samples(start, 0, 120, 130), emptyList())

        assertEquals(SyncOutcome.RETRY, sync.sync(id)) // no calories yet
        val merged = heart.get(id)!!
        assertEquals(HeartSource.MIXED, merged.source)
        assertEquals(List(12) { 100 } + List(12) { 130 }, merged.series.bpm) // live wins, gap filled
        assertEquals(KcalSource.ESTIMATE.takeIf { merged.kcal != null }, merged.kcalSource)

        data = data!!.copy(activeKcal = listOf(kcal(start, 120, 25.0)))
        assertEquals(SyncOutcome.DONE, sync.sync(id))
        assertEquals(25.0, heart.get(id)!!.kcal!!, 1e-9)
        assertEquals(KcalSource.WATCH, heart.get(id)!!.kcalSource)
    }

    @Test
    fun withoutLiveDataHealthConnectMakesTheWholeCurve() = runTest {
        val (id, start) = session()
        data = HealthConnectData(samples(start, 0, 120, 120), listOf(kcal(start, 120, 18.0)))
        assertEquals(SyncOutcome.DONE, sync.sync(id))
        assertEquals(HeartSource.HEALTH_CONNECT, heart.get(id)!!.source)
    }

    @Test
    fun caloriesAloneNeedACurveAndUnusableHealthConnectStops() = runTest {
        val (id, start) = session()
        data = HealthConnectData(emptyList(), listOf(kcal(start, 120, 18.0)))
        assertEquals(SyncOutcome.DONE, sync.sync(id))
        assertNull(heart.get(id))

        data = null // Health Connect unavailable or not allowed
        assertEquals(SyncOutcome.DONE, sync.sync(id))
    }

    @Test
    fun onlyTheWatchSourceCounts() = runTest {
        val (id, start) = session()
        data = HealthConnectData(
            samples(start, 0, 120, 120) + samples(start, 0, 120, 160, origin = "com.fitbit.FitbitMobile"),
            listOf(kcal(start, 120, 20.0), kcal(start, 120, 30.0, origin = "com.fitbit.FitbitMobile"), kcal(start, 120, 9.0, origin = "android")),
        )
        assertEquals(SyncOutcome.DONE, sync.sync(id))
        val saved = heart.get(id)!!
        assertEquals(120, saved.maxBpm) // Fitbit's 160 bpm ignored
        assertEquals(20.0, saved.kcal!!, 1e-9) // not 59
    }

    @Test
    fun implausibleWatchCaloriesKeepTheEstimate() = runTest {
        db.profileDao().upsert(ProfileEntity(birthYear = LocalDate.now(clock).year - 46, sex = Sex.MALE))
        BodyWeightRepository(db.bodyWeightDao()).save(LocalDate.now(clock), 80.0)
        val (id, start) = session()
        heart.saveSession(id, samples(start, 0, 120, 130).map { it.time to it.bpm }, HeartSource.BLE)
        val estimate = heart.get(id)!!.kcal!! // Keytel, ~25 kcal for 2 min at 130 bpm
        assertEquals(KcalSource.ESTIMATE, heart.get(id)!!.kcalSource)

        // Outside a workout, Mi Fitness writes a few calories now and then: not a measure of the session.
        data = HealthConnectData(emptyList(), listOf(kcal(start, 120, 2.0)))
        assertEquals(SyncOutcome.RETRY, sync.sync(id))
        assertEquals(estimate, heart.get(id)!!.kcal!!, 1e-9)
        assertEquals(KcalSource.ESTIMATE, heart.get(id)!!.kcalSource)

        // A plausible figure (at least half the estimate) wins and ends the sync.
        data = HealthConnectData(emptyList(), listOf(kcal(start, 120, estimate * 0.8)))
        assertEquals(SyncOutcome.DONE, sync.sync(id))
        assertEquals(KcalSource.WATCH, heart.get(id)!!.kcalSource)
    }

    @Test
    fun implausibleWatchCaloriesStoredBeforeAreFixedEvenLater() = runTest {
        db.profileDao().upsert(ProfileEntity(birthYear = LocalDate.now(clock).year - 46, sex = Sex.MALE))
        BodyWeightRepository(db.bodyWeightDao()).save(LocalDate.now(clock), 80.0)
        val (id, start) = session()
        heart.saveSession(id, samples(start, 0, 120, 130).map { it.time to it.bpm }, HeartSource.BLE)
        val estimate = heart.get(id)!!.kcal!!
        // As stored by version 2.1 / 2.2, which took any watch figure.
        db.sessionHeartDao().upsert(heart.get(id)!!.copy(kcal = 2.0, kcalSource = KcalSource.WATCH))

        clock.advance(Duration.ofHours(30))
        data = null
        assertEquals(SyncOutcome.DONE, sync.sync(id))
        assertEquals(estimate, heart.get(id)!!.kcal!!, 1e-9)
        assertEquals(KcalSource.ESTIMATE, heart.get(id)!!.kcalSource)
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
