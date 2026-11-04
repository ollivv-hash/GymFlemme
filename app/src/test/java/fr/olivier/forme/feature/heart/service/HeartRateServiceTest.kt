package fr.olivier.forme.feature.heart.service

import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.R
import fr.olivier.forme.core.db.context
import app.cash.turbine.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    /** 2.19.3: nothing is posted while the screen is off (each update lit it up), the latest text comes back on. */
    @Test
    fun theHeartRateIsNotPostedWhileTheScreenIsOff() = runTest {
        val texts = MutableStateFlow("♥ 120 bpm")
        val screenOn = MutableStateFlow(true)
        HeartRateService.shownTexts(texts, screenOn).test {
            assertEquals("♥ 120 bpm", awaitItem())
            screenOn.value = false
            texts.value = "♥ 130 bpm"
            texts.value = "♥ 140 bpm"
            expectNoEvents()
            screenOn.value = true
            assertEquals("♥ 140 bpm", awaitItem())
            // Off and on again without any change: nothing to re-post.
            screenOn.value = false
            screenOn.value = true
            expectNoEvents()
        }
    }
}
