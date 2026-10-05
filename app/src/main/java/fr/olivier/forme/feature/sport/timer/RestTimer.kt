package fr.olivier.forme.feature.sport.timer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.MainActivity
import fr.olivier.forme.R
import fr.olivier.forme.core.settings.RestTimerState
import fr.olivier.forme.core.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rest timer between sets.
 *
 * No foreground service: a notification with a countdown chronometer is shown on the lock screen,
 * and an exact alarm fires [RestAlarmReceiver] at the end of the rest (sound + vibration), even if the
 * process has been killed. The end time is persisted so the in-app countdown resumes after a restart.
 */
@Singleton
class RestTimer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val clock: Clock,
) {
    val state: Flow<RestTimerState?> = settings.restTimer

    suspend fun start(seconds: Int, label: String) {
        val endsAt = clock.millis() + seconds * 1000L
        settings.setRestTimer(RestTimerState(endsAt, seconds))
        showRunningNotification(endsAt, label)
        scheduleAlarm(endsAt, label)
    }

    /** Adds (or removes, if negative) time to the running timer. */
    suspend fun extend(deltaSec: Int, label: String) {
        val current = settings.restTimer.first() ?: return
        val endsAt = maxOf(clock.millis(), current.endsAtMillis + deltaSec * 1000L)
        settings.setRestTimer(current.copy(endsAtMillis = endsAt, totalSec = maxOf(0, current.totalSec + deltaSec)))
        showRunningNotification(endsAt, label)
        scheduleAlarm(endsAt, label)
    }

    suspend fun cancel() {
        settings.setRestTimer(null)
        alarmManager().cancel(alarmIntent(""))
        NotificationManagerCompat.from(context).cancel(NOTIF_RUNNING)
    }

    private fun alarmManager() = context.getSystemService(AlarmManager::class.java)

    private fun alarmIntent(label: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RestAlarmReceiver::class.java).putExtra(RestAlarmReceiver.EXTRA_LABEL, label),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleAlarm(endsAt: Long, label: String) {
        val am = alarmManager()
        val intent = alarmIntent(label)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, intent)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAt, intent)
        }
    }

    private fun showRunningNotification(endsAt: Long, label: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Repos")
            .setContentText(label)
            .setWhen(endsAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAppIntent(context))
            // Android 16 "Live Update": countdown chip in the status bar and card on the lock screen.
            .setRequestPromotedOngoing(true)
            .build()
        notify(context, NOTIF_RUNNING, notification)
    }

    companion object {
        /**
         * v2: default importance (no sound, no vibration). The first channel was "low" importance, which Android and
         * HyperOS treat as silent and hide on the lock screen; a channel's importance cannot be raised once created.
         */
        const val CHANNEL_RUNNING = "rest_running_v2"
        private const val CHANNEL_RUNNING_OLD = "rest_running"
        const val CHANNEL_DONE = "rest_done"
        const val NOTIF_RUNNING = 1
        const val NOTIF_DONE = 2

        fun createChannels(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel(CHANNEL_RUNNING_OLD)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, "Repos en cours", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_DONE, "Fin du repos", NotificationManager.IMPORTANCE_HIGH).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                    )
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
            )
        }

        fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        @Suppress("MissingPermission")
        fun notify(context: Context, id: Int, notification: android.app.Notification) {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) manager.notify(id, notification)
        }
    }
}

/** Fired by the exact alarm at the end of the rest. */
class RestAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationManagerCompat.from(context).cancel(RestTimer.NOTIF_RUNNING)
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        val notification = NotificationCompat.Builder(context, RestTimer.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Repos terminé")
            .setContentText(label.ifEmpty { "Série suivante !" })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setTimeoutAfter(TIMEOUT_MS)
            .setContentIntent(RestTimer.openAppIntent(context))
            .build()
        RestTimer.notify(context, RestTimer.NOTIF_DONE, notification)
    }

    companion object {
        const val EXTRA_LABEL = "label"
        private const val TIMEOUT_MS = 60_000L
    }
}
