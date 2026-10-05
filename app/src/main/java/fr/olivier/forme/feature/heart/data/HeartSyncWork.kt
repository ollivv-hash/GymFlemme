package fr.olivier.forme.feature.heart.data

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Background catch-up of Health Connect data for one finished session (needs the background read permission). */
class HeartSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun heartSync(): HeartSync
    }

    override suspend fun doWork(): Result {
        val sessionId = inputData.getLong(KEY_SESSION, -1)
        if (sessionId < 0) return Result.failure()
        val sync = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).heartSync()
        val outcome = runCatching { sync.sync(sessionId) }
            .onFailure { Log.w("Forme", "Heart rate sync failed (attempt $runAttemptCount)", it) }
            .getOrDefault(SyncOutcome.RETRY)
        return when {
            outcome == SyncOutcome.DONE -> Result.success()
            runAttemptCount >= MAX_ATTEMPTS -> Result.success()
            else -> Result.retry()
        }
    }

    companion object {
        const val KEY_SESSION = "sessionId"
        /** With a linear 30 min backoff, 7 attempts span about 14 h after the first one. */
        const val MAX_ATTEMPTS = 7
    }
}

@Singleton
class HeartSyncScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    /** First attempt a few minutes after the session (Mi Fitness syncs the watch late), then every 30, 60, 90… min. */
    fun schedule(sessionId: Long) {
        val request = OneTimeWorkRequestBuilder<HeartSyncWorker>()
            .setInputData(workDataOf(HeartSyncWorker.KEY_SESSION to sessionId))
            .setInitialDelay(5, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.MINUTES)
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork("heart-sync-$sessionId", ExistingWorkPolicy.REPLACE, request) }
            .onFailure { Log.e("Forme", "Heart rate sync scheduling failed", it) }
    }
}
