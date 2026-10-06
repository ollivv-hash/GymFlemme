package fr.olivier.forme.core.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import fr.olivier.forme.R
import fr.olivier.forme.feature.sport.timer.RestTimer

/** Warns when a background backup failed (write or verification): otherwise it only shows in the settings. */
object BackupNotifier {
    private const val CHANNEL = "backup"
    private const val NOTIF_ID = 20

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Sauvegarde", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    fun failed(context: Context, error: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sauvegarde automatique en échec")
            .setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$error\nDétails dans Config → Réglages."))
            .setAutoCancel(true)
            .setContentIntent(RestTimer.openAppIntent(context))
            .build()
        RestTimer.notify(context, NOTIF_ID, notification)
    }

    fun clear(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIF_ID)
}
