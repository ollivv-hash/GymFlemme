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
import fr.olivier.forme.feature.nutrition.data.NutritionRepository
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
 * Daily creatine reminder. An inexact alarm fires at the chosen time; the notification is shown only
 * if the dose is not recorded yet, and offers a "C'est pris" action. Rescheduled after each firing,
 * after a reboot or a clock change, and whenever the profile changes.
 */
@Singleton
class CreatineReminder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: NutritionRepository,
    private val clock: Clock,
) {
    suspend fun reschedule() {
        val profile = repository.currentProfile()
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = alarmIntent(context)
        alarm.cancel(intent)
        if (!profile.creatineReminderEnabled) return
        val next = nextTrigger(ZonedDateTime.now(clock), profile.creatineReminderMinutes)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), intent)
    }

    fun dismissNotification() = NotificationManagerCompat.from(context).cancel(NOTIF_ID)

    /** Shows the reminder unless today's dose is already recorded. */
    suspend fun fire() {
        val today = repository.today()
        if (!repository.isCreatineTaken(today)) {
            val dose = repository.currentProfile().creatineDoseG
            val taken = PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, CreatineReminderReceiver::class.java).setAction(ACTION_TAKEN),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Créatine")
                .setContentText("Pense à ta dose de ${Fmt.number(dose)} g")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(RestTimer.openAppIntent(context))
                .addAction(0, "C'est pris", taken)
                .build()
            RestTimer.notify(context, NOTIF_ID, notification)
        }
        reschedule()
    }

    suspend fun markTaken() {
        repository.setCreatineTaken(repository.today(), true)
        dismissNotification()
    }

    companion object {
        const val CHANNEL = "creatine"
        const val NOTIF_ID = 10
        const val ACTION_FIRE = "fr.olivier.forme.CREATINE_FIRE"
        const val ACTION_TAKEN = "fr.olivier.forme.CREATINE_TAKEN"

        /** Next occurrence of [minutesOfDay] strictly after [now]. */
        fun nextTrigger(now: ZonedDateTime, minutesOfDay: Int): ZonedDateTime {
            val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
            val today = LocalDateTime.of(now.toLocalDate(), time).atZone(now.zone)
            return if (today.isAfter(now)) today else today.plusDays(1)
        }

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Rappel créatine", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }

        private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, CreatineReminderReceiver::class.java).setAction(ACTION_FIRE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Alarm, "C'est pris" action, and system events (boot, time or time zone change). */
@AndroidEntryPoint
class CreatineReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var reminder: CreatineReminder
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    CreatineReminder.ACTION_FIRE -> reminder.fire()
                    CreatineReminder.ACTION_TAKEN -> reminder.markTaken()
                    else -> reminder.reschedule()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
