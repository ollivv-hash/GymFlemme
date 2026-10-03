package fr.olivier.forme.feature.heart.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.db.SeedData
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.context
import fr.olivier.forme.core.db.testDatabase
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.sport.data.SessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration

@RunWith(AndroidJUnit4::class)
class HeartSessionRecorderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun sampleStoreRoundTripIgnoresBrokenLines() {
        val store = LiveSampleStore(tmp.root)
        store.append(7, 1_000, 72)
        store.append(7, 2_000, 75)
        File(tmp.root, "7.csv").appendText("garbage\n3000,\n")
        assertEquals(listOf(72, 75), store.read(7).map { it.second })
        assertEquals(1_000L, store.read(7).first().first.toEpochMilli())
        store.delete(7)
        assertTrue(store.read(7).isEmpty())
    }

    @Test
    fun withoutWatchTheSessionIsNotRecordedButStopStillSavesStoredSamples() = runTest {
        val clock = TestClock()
        val db = testDatabase(clock)
        val sessions = SessionRepository(db, db.sessionDao(), db.templateDao(), db.exerciseDao(), clock)
        val heart = HeartRepository(db.sessionHeartDao(), db.sessionDao(), db.profileDao(), db.bodyWeightDao(), clock)
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "s.preferences_pb") })
        val recorder = HeartSessionRecorder(context(), BleHeartRateMonitor(context(), clock), settings, heart, backgroundScope)

        clock.advance(Duration.ofDays(1))
        val id = sessions.start(SeedData.templateC.id)
        recorder.start(id)
        assertEquals(LiveHeartStatus.NO_DEVICE, recorder.state.first { it.status != LiveHeartStatus.OFF }.status)

        // Measurements stored before a process kill are still used at the end of the session.
        val start = clock.instant()
        val store = LiveSampleStore(File(context().filesDir, "heart-live"))
        (0 until 120).forEach { store.append(id, start.plusSeconds(it.toLong()).toEpochMilli(), 110 + it / 10) }
        clock.advance(Duration.ofMinutes(2))
        sessions.finish(id, 4, null)
        recorder.stop(id, save = true)

        val saved = heart.get(id)!!
        assertEquals(HeartSource.BLE, saved.source)
        assertEquals(24, saved.series.bpm.size)
        recorder.state.first { it.status == LiveHeartStatus.OFF }
        assertTrue(store.read(id).isEmpty())

        // Abandoned session: nothing saved, raw file removed.
        val other = sessions.start(SeedData.templateA.id)
        store.append(other, clock.millis(), 100)
        recorder.stop(other, save = false)
        assertNull(heart.get(other))
        assertTrue(store.read(other).isEmpty())
        db.close()
    }
}
