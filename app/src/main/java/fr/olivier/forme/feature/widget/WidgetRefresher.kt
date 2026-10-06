package fr.olivier.forme.feature.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.olivier.forme.core.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the widget up to date without waking the phone periodically: redrawn when its data changes while the app
 * runs (meal added, session finished…), and once just after midnight for the new day.
 */
@Singleton
class WidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val source: WidgetDataSource,
    private val clock: Clock,
) {
    private var observer: Job? = null

    fun start() {
        scheduleMidnight()
        observeToday()
    }

    /** Watches today's data; restarted at midnight, since "today" is fixed when the observation starts. */
    @OptIn(FlowPreview::class)
    fun observeToday() {
        observer?.cancel()
        observer = scope.launch {
            // The first value is what the widget already shows; a burst of writes (session end) redraws once.
            source.observe().distinctUntilChanged().drop(1).debounce(1_000).collect { refresh() }
        }
    }

    suspend fun refresh() {
        runCatching { FormeWidget().updateAll(context) }.onFailure { Log.w("Forme", "Widget update failed", it) }
    }

    fun scheduleMidnight() {
        val now = clock.instant()
        val midnight = LocalDate.now(clock).plusDays(1).atStartOfDay(clock.zone).toInstant().plusSeconds(MIDNIGHT_MARGIN_SEC)
        val request = OneTimeWorkRequestBuilder<WidgetMidnightWorker>()
            .setInitialDelay(Duration.between(now, midnight).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniqueWork(MIDNIGHT, ExistingWorkPolicy.REPLACE, request) }
            .onFailure { Log.w("Forme", "Widget midnight scheduling failed", it) }
    }

    private companion object {
        const val MIDNIGHT = "widget-midnight"
        const val MIDNIGHT_MARGIN_SEC = 60L
    }
}

/** Redraws the widget for the new day, then schedules the next midnight. */
class WidgetMidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun widgetRefresher(): WidgetRefresher
    }

    override suspend fun doWork(): Result {
        val refresher = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).widgetRefresher()
        refresher.refresh()
        refresher.observeToday()
        refresher.scheduleMidnight()
        return Result.success()
    }
}
