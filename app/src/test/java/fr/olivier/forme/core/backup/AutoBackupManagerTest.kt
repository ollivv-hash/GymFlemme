package fr.olivier.forme.core.backup

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import fr.olivier.forme.core.db.TestClock
import fr.olivier.forme.core.db.context
import fr.olivier.forme.core.db.testDatabase
import fr.olivier.forme.core.settings.AutoBackupState
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.bodyweight.data.BodyWeightRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.time.Duration
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class AutoBackupManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = TestClock()
    private val db = testDatabase(clock)

    private fun TestScope.manager(): Pair<AutoBackupManager, SettingsRepository> {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "settings.preferences_pb") }
        val settings = SettingsRepository(store)
        return AutoBackupManager(context(), BackupRepository(db, context(), clock), settings, clock) to settings
    }

    @Test
    fun withoutTargetOnlyTheLastThreeLocalCopiesAreKept() = runTest {
        val (manager, settings) = manager()
        repeat(5) {
            val result = manager.runNow()
            assertFalse(result.targetWritten)
            assertNull(result.targetError)
            clock.advance(Duration.ofHours(1))
        }
        val copies = manager.localCopies()
        assertEquals(AutoBackupManager.LOCAL_KEEP, copies.size)
        assertTrue(copies.zipWithNext().all { (newer, older) -> newer.name > older.name })
        BackupCodec.decode(copies.first().readText())
        assertEquals(AutoBackupState(), settings.autoBackup.first())
    }

    @Test
    fun targetIsRewrittenInPlaceAndStatusRecorded() = runTest {
        val (manager, settings) = manager()
        val target = tmp.newFile("forme-sauvegarde.json")
        target.writeText("x".repeat(500_000)) // longer than a backup: the rewrite must truncate it

        val first = manager.setTarget(Uri.fromFile(target))
        assertTrue(first.targetWritten)
        assertTrue(BackupCodec.decode(target.readText()).bodyWeight.isEmpty())
        assertEquals(clock.millis(), settings.autoBackup.first().lastSuccessAt)

        BodyWeightRepository(db.bodyWeightDao()).save(LocalDate.of(2026, 10, 2), 81.7)
        clock.advance(Duration.ofDays(1))
        manager.runNow()
        val state = settings.autoBackup.first()
        assertEquals(81.7, BackupCodec.decode(target.readText()).bodyWeight.single().weightKg, 0.0)
        assertEquals(clock.millis(), state.lastSuccessAt)
        assertNull(state.lastError)
    }

    @Test
    fun unreachableTargetRecordsAnErrorButKeepsTheLocalCopy() = runTest {
        val (manager, settings) = manager()
        val missing = File(tmp.root, "deleted-folder/forme-sauvegarde.json")

        val result = manager.setTarget(Uri.fromFile(missing))

        assertFalse(result.targetWritten)
        assertTrue(result.localCopy.exists())
        val state = settings.autoBackup.first()
        assertTrue(state.lastError!!.contains("choisis à nouveau"))
        assertNull(state.lastSuccessAt)
        assertEquals(clock.millis(), state.lastAttemptAt)

        manager.clearTarget()
        assertEquals(AutoBackupState(), settings.autoBackup.first())
    }

    @Test
    fun readBackIsCheckedBeforeCallingABackupGood() = runTest {
        val (manager, _) = manager()
        val good = BackupCodec.encode(BackupRepository(db, context(), clock).snapshot()).toByteArray()
        assertNull(BackupVerifier.check(good, good.copyOf()))
        assertTrue(BackupVerifier.check(good, null)!!.contains("vide"))
        // Truncated upload, or a provider that kept an older version.
        assertTrue(BackupVerifier.check(good, good.copyOf(good.size / 2))!!.contains("différent"))
        // Bytes faithfully copied, but not a backup this version can restore.
        val newer = good.decodeToString().replaceFirst("\"schemaVersion\": 5", "\"schemaVersion\": 99").toByteArray()
        assertTrue(BackupVerifier.check(newer, newer)!!.contains("plus récent"))

        // A successful run reads its copies back: no error.
        assertNull(manager.setTarget(Uri.fromFile(tmp.newFile("ok.json"))).targetError)
    }

    @Test
    fun failureIsNotifiedThenCleared() {
        val manager = context().getSystemService(android.app.NotificationManager::class.java)
        val shadow = org.robolectric.Shadows.shadowOf(manager)
        BackupNotifier.createChannel(context())
        BackupNotifier.failed(context(), "Fichier introuvable")
        assertEquals("Sauvegarde automatique en échec", shadow.allNotifications.single().extras.getString("android.title"))
        BackupNotifier.clear(context())
        assertTrue(shadow.allNotifications.isEmpty())
    }

    @Test
    fun workerRunsTheBackup() = runTest {
        val worker = TestListenableWorkerBuilder<AutoBackupWorker>(context()).build()
        assertTrue(worker.doWork() is ListenableWorker.Result.Success)
        assertTrue(File(context().filesDir, "auto-backups").listFiles().orEmpty().isNotEmpty())
    }
}
