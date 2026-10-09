package fr.olivier.forme.feature.badges.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.R
import fr.olivier.forme.core.di.ApplicationScope
import fr.olivier.forme.core.humor.QuipRepository
import fr.olivier.forme.feature.badges.data.BadgeRepository
import fr.olivier.forme.feature.badges.ui.ChallengeWording
import fr.olivier.forme.feature.sport.timer.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Announcement of the monthly challenge (V2.19): an inexact alarm on the 1st of the month around 9:00 draws the
 * month's challenge and announces it. Can be switched off in the settings; no notification for the badges themselves.
 * Rescheduled after each firing, at startup, after a reboot or a clock change, and when the setting changes.
 */
@Singleton
class ChallengeReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val badges: BadgeRepository,
    private val quips: QuipRepository,
    private val clock: Clock,
) {
    suspend fun reschedule() {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(context)
        alarm.cancel(intent)
        if (!badges.challengeNotification.first()) return
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger(ZonedDateTime.now(clock)).toInstant().toEpochMilli(), intent)
    }

    suspend fun fire() {
        if (badges.challengeNotification.first()) {
            val month = YearMonth.now(clock)
            badges.drawChallenge(month)?.let { challenge ->
                val resources = context.resources
                val text = ChallengeWording.announce(resources, challenge, quips.style.first())
                val notification = NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("🎯 ${ChallengeWording.ofMonth(resources, month)} · ${ChallengeWording.name(resources, challenge)}")
                    .setContentText(text)
                    // The longest announcements do not fit on one line: shown whole once expanded.
                    .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(RestTimer.openAppIntent(context))
                    .build()
                RestTimer.notify(context, NOTIF_ID, notification)
            }
        }
        reschedule()
    }

    companion object {
        const val CHANNEL = "challenge"
        const val NOTIF_ID = 12
        const val ACTION_FIRE = "fr.olivier.forme.CHALLENGE_FIRE"
        val TIME: LocalTime = LocalTime.of(9, 0)

        /** Next 1st of a month at [TIME], strictly after [now]. */
        fun nextTrigger(now: ZonedDateTime): ZonedDateTime {
            val thisMonth = now.toLocalDate().withDayOfMonth(1).atTime(TIME).atZone(now.zone)
            return if (thisMonth.isAfter(now)) thisMonth else thisMonth.plusMonths(1)
        }

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.challenge_notification_channel), NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, ChallengeReminderReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Alarm, and system events (boot, time or time zone change). */
@AndroidEntryPoint
class ChallengeReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var reminder: ChallengeReminder
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                if (intent.action == ChallengeReminder.ACTION_FIRE) reminder.fire() else reminder.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
