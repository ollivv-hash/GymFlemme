package fr.olivier.forme

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import fr.olivier.forme.feature.sport.timer.RestTimer

@HiltAndroidApp
class FormeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RestTimer.createChannels(this)
    }
}
