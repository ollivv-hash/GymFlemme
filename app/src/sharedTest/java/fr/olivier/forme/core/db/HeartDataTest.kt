package fr.olivier.forme.core.db

import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import fr.olivier.forme.feature.heart.data.HeartRepository
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.heart.domain.KcalSource
import fr.olivier.forme.feature.heart.domain.Sex
import fr.olivier.forme.feature.nutrition.data.ProfileEntity
import fr.olivier.forme.feature.sport.data.SessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class HeartDataTest {
    private val clock = TestClock()
    private val db = testDatabase(clock)
    private val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
    private val heart = HeartRepository(db.sessionHeartDao(), db.sessionDao(), db.profileDao(), db.bodyWeightDao(), clock)

    @After
    fun close() = db.close()

    /** A 10-minute session with one measurement per second at 120 bpm, then 150 bpm. */
    private suspend fun sessionWithHeartRate(): Pair<Long, List<Pair<java.time.Instant, Int>>> {
        clock.advance(Duration.ofDays(1))
        val id = sessions.start(SeedData.templateC.id)
        val start = db.sessionDao().getSession(id)!!.startedAt
        val samples = (0 until 600).map { s -> start.plusSeconds(s.toLong()) to if (s < 300) 120 else 150 }
        clock.advance(Duration.ofMinutes(10))
        sessions.finish(id, 4, null)
        return id to samples
    }

    @Test
    fun summaryUsesProfileAndEstimatesCalories() = runTest {
        db.profileDao().upsert(ProfileEntity(birthYear = 1980, sex = Sex.MALE))
        BodyWeightRepository(db.bodyWeightDao()).save(LocalDate.of(2026, 10, 1), 80.0)
        val (id, samples) = sessionWithHeartRate()

        val saved = heart.saveSession(id, samples, HeartSource.BLE)!!

        assertEquals(46, heart.heartProfile().age) // 2026 − 1980
        assertEquals(176, saved.hrMax)
        assertEquals(135, saved.avgBpm)
        assertEquals(120, saved.series.bpm.size) // 10 min / 5 s
        // 120 bpm = 68 % (Z2), 150 bpm = 85 % (Z4): 5 min each.
        assertEquals(listOf(0, 300, 0, 300, 0), saved.zoneSeconds)
        assertEquals(KcalSource.ESTIMATE, saved.kcalSource)
        assertEquals(saved, heart.observe(id).first())
        assertEquals(listOf(saved), heart.observeDay(LocalDate.ofInstant(samples.first().first, clock.zone)).first())
    }

    @Test
    fun watchCaloriesWinAndMissingProfileLeavesZonesEmpty() = runTest {
        val (id, samples) = sessionWithHeartRate()
        val saved = heart.saveSession(id, samples, HeartSource.HEALTH_CONNECT, watchKcal = 95.0)!!
        assertNull(saved.hrMax)
        assertEquals(listOf(0, 0, 0, 0, 0), saved.zoneSeconds)
        assertEquals(95.0, saved.kcal!!, 0.0)
        assertEquals(KcalSource.WATCH, saved.kcalSource)
    }

    @Test
    fun noMeasurementInTheSessionStoresNothing() = runTest {
        val (id, samples) = sessionWithHeartRate()
        assertNull(heart.saveSession(id, samples.map { (t, v) -> t.minus(Duration.ofHours(2)) to v }, HeartSource.BLE))
        assertNull(heart.get(id))
    }
}
