package fr.olivier.forme

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fr.olivier.forme.core.AppStartup
import fr.olivier.forme.core.backup.BackupNotifier
import fr.olivier.forme.feature.heart.service.HeartRateService
import fr.olivier.forme.feature.hydration.reminder.HydrationReminder
import fr.olivier.forme.feature.badges.reminder.ChallengeReminder
import fr.olivier.forme.feature.nutrition.reminder.SupplementReminder
import fr.olivier.forme.feature.sport.timer.RestTimer
import javax.inject.Inject

@HiltAndroidApp
class FormeApp : Application() {
    @Inject lateinit var startup: AppStartup

    override fun onCreate() {
        super.onCreate()
        RestTimer.createChannels(this)
        SupplementReminder.createChannel(this)
        ChallengeReminder.createChannel(this)
        HeartRateService.createChannel(this)
        BackupNotifier.createChannel(this)
        HydrationReminder.createChannel(this)
        startup.run()
    }
}
