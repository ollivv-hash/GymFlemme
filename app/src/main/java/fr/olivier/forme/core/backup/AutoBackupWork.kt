package fr.olivier.forme.core.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs [AutoBackupManager.runNow] in the background. No network constraint: the file is handed to the document
 * provider (the Drive app), which uploads it when it can.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun autoBackupManager(): AutoBackupManager
    }

    override suspend fun doWork(): Result {
        val manager = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).autoBackupManager()
        return runCatching { manager.runNow() }.fold(
            // A target failure is shown in the settings; retrying would not fix a lost grant.
            onSuccess = { Result.success() },
            onFailure = {
                Log.e("Forme", "Auto backup failed", it)
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
            },
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}

/** Schedules the daily backup and the one run after each finished session. */
@Singleton
class AutoBackupScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    fun scheduleDaily() = enqueue {
        it.enqueueUniquePeriodicWork(
            DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    fun requestNow() = enqueue {
        it.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
    }

    private fun enqueue(block: (WorkManager) -> Unit) {
        runCatching { block(WorkManager.getInstance(context)) }
            .onFailure { Log.e("Forme", "Auto backup scheduling failed", it) }
    }

    companion object {
        const val DAILY = "auto-backup-daily"
        const val NOW = "auto-backup-now"
    }
}
