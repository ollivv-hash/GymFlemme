package fr.olivier.forme.feature.nutrition.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.R
import fr.olivier.forme.core.di.ApplicationScope
import fr.olivier.forme.core.humor.Quip
import fr.olivier.forme.core.humor.QuipRepository
import fr.olivier.forme.feature.badges.data.BadgeRepository
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
import fr.olivier.forme.feature.nutrition.data.SupplementEntity
import fr.olivier.forme.feature.nutrition.data.dose
import fr.olivier.forme.feature.sport.domain.Fmt
import fr.olivier.forme.feature.sport.timer.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Daily reminder of each active supplement whose reminder is on (V2.20; creatine alone until 2.19). An inexact alarm
 * fires at the chosen time; the notification is shown only if the supplement is not recorded yet, and offers a
 * "C'est pris" action. Rescheduled after each firing, after a reboot or a clock change, and whenever the
 * supplements change.
 */
@Singleton
class SupplementReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NutritionRepository,
    private val clock: Clock,
    private val quips: QuipRepository,
    private val badges: BadgeRepository,
) {
    /** Cancels the alarm of every supplement, then sets the ones that are due. */
    suspend fun reschedule() {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(legacyIntent(context))
        repository.supplementList().forEach { supplement ->
            val intent = alarmIntent(context, supplement.id)
            alarm.cancel(intent)
            if (supplement.active && supplement.reminderEnabled) {
                val next = nextTrigger(ZonedDateTime.now(clock), supplement.reminderMinutes)
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), intent)
            }
        }
    }

    /** A supplement deleted: its alarm and notification go too. */
    fun cancel(id: Long) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, id))
        dismissNotification(id)
    }

    fun dismissNotification(id: Long) = NotificationManagerCompat.from(context).cancel(notificationId(id))

    /** Shows the reminder of [id] unless today's intake is already recorded. */
    suspend fun fire(id: Long) {
        val supplement = repository.supplement(id)
        if (supplement != null && supplement.active && supplement.reminderEnabled && !repository.isSupplementTaken(id, repository.today())) {
            val taken = PendingIntent.getBroadcast(
                context,
                TAKEN_REQUEST_BASE + id.toInt(),
                Intent(context, SupplementReminderReceiver::class.java).setAction(ACTION_TAKEN).putExtra(EXTRA_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(supplement.name)
                .setContentText(text(supplement))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(RestTimer.openAppIntent(context))
                .addAction(0, "C'est pris", taken)
                .build()
            RestTimer.notify(context, notificationId(id), notification)
        }
        reschedule()
    }

    suspend fun markTaken(id: Long) {
        repository.setSupplementTaken(id, repository.today(), true)
        dismissNotification(id)
        runCatching { badges.evaluate() } // V2.17: creatine badges, announced at the next opening
    }

    private suspend fun text(supplement: SupplementEntity): String =
        if (supplement.isCreatine) quips.text(Quip.CREATINE, Fmt.number(supplement.amount)) else "Dose du jour : ${supplement.dose()}"

    companion object {
        /** Id kept from the creatine reminder (its settings stay), renamed for every supplement. */
        const val CHANNEL = "creatine"
        const val ACTION_FIRE = "fr.olivier.forme.SUPPLEMENT_FIRE"
        const val ACTION_TAKEN = "fr.olivier.forme.SUPPLEMENT_TAKEN"
        const val EXTRA_ID = "supplementId"
        private const val NOTIF_BASE = 1_000
        private const val TAKEN_REQUEST_BASE = 100_000

        fun notificationId(id: Long) = NOTIF_BASE + id.toInt()

        /** Next occurrence of [minutesOfDay] strictly after [now]. */
        fun nextTrigger(now: ZonedDateTime, minutesOfDay: Int): ZonedDateTime {
            val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
            val today = LocalDateTime.of(now.toLocalDate(), time).atZone(now.zone)
            return if (today.isAfter(now)) today else today.plusDays(1)
        }

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Rappels compléments", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        private fun alarmIntent(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
            context,
            id.toInt(),
            Intent(context, SupplementReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** The single creatine alarm of 2.19 and before (request code 0, old action), replaced by the ones above. */
        private fun legacyIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SupplementReminderReceiver::class.java).setAction("fr.olivier.forme.CREATINE_FIRE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Alarms, "C'est pris" actions, and system events (boot, time or time zone change). */
@AndroidEntryPoint
class SupplementReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var reminder: SupplementReminder
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val id = intent.getLongExtra(SupplementReminder.EXTRA_ID, -1L).takeIf { it >= 0 }
        scope.launch {
            try {
                when {
                    intent.action == SupplementReminder.ACTION_FIRE && id != null -> reminder.fire(id)
                    intent.action == SupplementReminder.ACTION_TAKEN && id != null -> reminder.markTaken(id)
                    else -> reminder.reschedule()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
