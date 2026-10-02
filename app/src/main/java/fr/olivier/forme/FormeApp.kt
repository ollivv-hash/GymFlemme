package fr.olivier.forme

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fr.olivier.forme.core.AppStartup
import fr.olivier.forme.feature.nutrition.reminder.CreatineReminder
import fr.olivier.forme.feature.sport.timer.RestTimer
import javax.inject.Inject

@HiltAndroidApp
class FormeApp : Application() {
    @Inject lateinit var startup: AppStartup

    override fun onCreate() {
        super.onCreate()
        RestTimer.createChannels(this)
        CreatineReminder.createChannel(this)
        startup.run()
    }
}
