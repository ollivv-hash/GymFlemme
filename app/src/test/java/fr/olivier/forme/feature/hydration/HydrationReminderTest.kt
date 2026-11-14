package fr.olivier.forme.feature.hydration

import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import fr.olivier.forme.core.db.context
import fr.olivier.forme.feature.hydration.domain.ReminderSkip
import fr.olivier.forme.feature.hydration.reminder.HydrationReminder
import fr.olivier.forme.feature.hydration.reminder.HydrationReminderReceiver
import fr.olivier.forme.feature.hydration.reminder.HydrationReminderWorker
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/**
 * V2.29: the "+250 ml" action of the reminder records the water without opening the app, and the notification says
 * so; no reminder before the first-use card was answered.
 */
@RunWith(AndroidJUnit4::class)
class HydrationReminderTest {
    private val reminder: HydrationReminder =
        EntryPointAccessors.fromApplication(context(), HydrationReminderWorker.Deps::class.java).hydrationReminder()

    private fun notificationText(): String? = shadowOf(context().getSystemService(NotificationManager::class.java))
        .allNotifications
        .firstOrNull()
        ?.extras
        ?.getCharSequence(android.app.Notification.EXTRA_TEXT)
        ?.toString()

    @Test
    fun theActionRecordsTheWaterWithoutTheApp() {
        shadowOf(context().getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        context().sendBroadcast(
            Intent(context(), HydrationReminderReceiver::class.java)
                .setAction(HydrationReminder.ACTION_ADD)
                .putExtra(HydrationReminder.EXTRA_ML, 250),
        )
        shadowOf(Looper.getMainLooper()).idle()
        // The receiver works on the application scope: wait for the notification to be updated.
        val deadline = System.currentTimeMillis() + 15_000
        while (notificationText() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
        }
        val text = notificationText()
        // "Noté ! 250 ml / 2 L" or "Glou glou, c'est noté. 250 ml / 2 L" (no weight yet: 2 L): read back from the
        // database after the intake was recorded.
        assertTrue("$text", text != null && text.endsWith("250 ml / 2 L"))
    }

    @Test
    fun noReminderBeforeTheFirstUseCard() {
        assertEquals(ReminderSkip.INTRO_NOT_SEEN, runBlocking { reminder.fire() })
    }
}
