package fr.olivier.forme.feature.heart.service

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.R
import fr.olivier.forme.core.db.context
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class HeartRateServiceTest {
    @Test
    fun stoppingRemovesALeftoverSessionNotification() {
        val manager = context().getSystemService(NotificationManager::class.java)
        HeartRateService.createChannel(context())
        // Re-posted by the service's updater just as the session was abandoned: it must not outlive the session.
        manager.notify(
            HeartRateService.NOTIF_ID,
            NotificationCompat.Builder(context(), HeartRateService.CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Séance en cours")
                .setOngoing(true)
                .build(),
        )

        HeartRateService.stop(context())

        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
