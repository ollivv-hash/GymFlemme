package fr.olivier.forme.feature.hydration.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.olivier.forme.R
import fr.olivier.forme.core.di.ApplicationScope
import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.core.humor.QuipRepository
import fr.olivier.forme.feature.badges.data.BadgeRepository
import fr.olivier.forme.feature.hydration.data.HydrationDao
import fr.olivier.forme.feature.hydration.data.HydrationRepository
import fr.olivier.forme.feature.hydration.data.HydrationSettingsRepository
import fr.olivier.forme.feature.hydration.data.IntakeSource
import fr.olivier.forme.feature.hydration.domain.HydrationMath
import fr.olivier.forme.feature.hydration.domain.ReminderRules
import fr.olivier.forme.feature.hydration.domain.ReminderSkip
import fr.olivier.forme.feature.hydration.domain.ReminderState
import fr.olivier.forme.feature.sport.timer.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hydration reminders (V2.29): a chain of one-off WorkManager jobs (no exact alarm), each one scheduling the next.
 * WorkManager keeps them across a reboot and an update of the app. Not sent before the first-use card was answered,
 * nor outside the hours, right after a drink, during a session or once the goal is reached ([ReminderRules]).
 */
@Singleton
class HydrationReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hydration: HydrationRepository,
    private val dao: HydrationDao,
    private val settings: HydrationSettingsRepository,
    private val quips: QuipRepository,
    private val badges: BadgeRepository,
    private val clock: Clock,
) {
    /** Settings changed: the next reminder is set again from now (or cancelled when off). */
    suspend fun reschedule() = schedule(ExistingWorkPolicy.REPLACE)

    /** App started: a reminder already due is kept (opening the app must not push it back). */
    suspend fun ensureScheduled() = schedule(ExistingWorkPolicy.KEEP)

    private suspend fun schedule(policy: ExistingWorkPolicy) {
        val s = settings.current()
        runCatching {
            val work = WorkManager.getInstance(context)
            if (!s.reminders.enabled || !s.introSeen) {
                work.cancelUniqueWork(WORK)
                return
            }
            val now = ZonedDateTime.now(clock)
            val next = ReminderRules.next(now, s.reminders.startMinutes, s.reminders.endMinutes, s.reminders.intervalMinutes)
            val delay = Duration.between(now, next).toMillis().coerceAtLeast(0)
            work.enqueueUniqueWork(WORK, policy, OneTimeWorkRequestBuilder<HydrationReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build())
        }.onFailure { Log.e("Forme", "Hydration reminder scheduling failed", it) }
    }

    /** What decides the reminder now. */
    suspend fun state(): ReminderState {
        val s = settings.current()
        val today = hydration.todayState()
        return ReminderState(
            now = LocalDateTime.now(clock),
            enabled = s.reminders.enabled,
            introSeen = s.introSeen,
            notificationsAllowed = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            startMinutes = s.reminders.startMinutes,
            endMinutes = s.reminders.endMinutes,
            skipAfterDrink = s.reminders.skipAfterDrink,
            skipDuringSession = s.reminders.skipDuringSession,
            lastIntakeAt = hydration.lastIntakeAt()?.let { LocalDateTime.ofInstant(it, clock.zone) },
            goalReached = today.reached,
            sessionRunning = dao.countRunningSessions() > 0,
        )
    }

    /** Run by the worker: the reminder if due, then the next one (after this job ends). */
    suspend fun fire(): ReminderSkip? {
        val skip = ReminderRules.skip(state())
        if (skip == null) show()
        schedule(ExistingWorkPolicy.APPEND_OR_REPLACE)
        return skip
    }

    private suspend fun show() {
        val s = settings.current().reminders
        val today = hydration.todayState()
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.water_notification_title))
            .setContentText(quips.text(Quip.WATER_REMINDER, HydrationMath.liters(today.remainingMl)))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(RestTimer.openAppIntent(context))
            .addAction(0, context.getString(R.string.water_notification_action, s.action1Ml), addIntent(context, s.action1Ml, 1))
            .addAction(0, context.getString(R.string.water_notification_action, s.action2Ml), addIntent(context, s.action2Ml, 2))
            .build()
        RestTimer.notify(context, NOTIFICATION_ID, notification)
    }

    /**
     * An action of the notification: the water is recorded without opening the app, the notification says so
     * ("Noté ! 1,8 / 3,4 L") and goes after a few seconds.
     */
    suspend fun addFromNotification(volumeMl: Int) {
        val added = runCatching { hydration.addWater(volumeMl, IntakeSource.NOTIFICATION) }
            .onFailure { Log.e("Forme", "Water from the notification failed", it) }
            .getOrNull()
        if (added == null) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return
        }
        runCatching { badges.evaluate() } // announced at the next opening
        val text = quips.text(Quip.WATER_NOTED, HydrationMath.liters(added.today.countedMl), HydrationMath.liters(added.today.goal.totalMl))
        val done = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.water_notification_title))
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setTimeoutAfter(NOTED_MILLIS)
            .setContentIntent(RestTimer.openAppIntent(context))
            .build()
        RestTimer.notify(context, NOTIFICATION_ID, done)
    }

    companion object {
        const val CHANNEL = "water"
        const val WORK = "water-reminder"
        const val NOTIFICATION_ID = 2_000
        const val ACTION_ADD = "fr.olivier.forme.WATER_ADD"
        const val EXTRA_ML = "volumeMl"
        private const val NOTED_MILLIS = 4_000L

        /** Its own channel, apart from the supplements' and the rest timer's, default priority. */
        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.water_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        fun addIntent(context: Context, volumeMl: Int, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_BASE + requestCode,
            Intent(context, HydrationReminderReceiver::class.java).setAction(ACTION_ADD).putExtra(EXTRA_ML, volumeMl),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private const val REQUEST_BASE = 200_000
    }
}

/** One reminder of the chain. */
class HydrationReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun hydrationReminder(): HydrationReminder
    }

    override suspend fun doWork(): Result {
        val reminder = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).hydrationReminder()
        runCatching { reminder.fire() }.onFailure { Log.e("Forme", "Hydration reminder failed", it) }
        return Result.success()
    }
}

/** "+250 ml" / "+500 ml" of the reminder: recorded at once, the app stays closed. */
@AndroidEntryPoint
class HydrationReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var reminder: HydrationReminder
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HydrationReminder.ACTION_ADD) return
        val ml = intent.getIntExtra(HydrationReminder.EXTRA_ML, 0).takeIf { it > 0 } ?: return
        val pending = goAsync()
        scope.launch {
            try {
                reminder.addFromNotification(ml)
            } finally {
                pending.finish()
            }
        }
    }
}
