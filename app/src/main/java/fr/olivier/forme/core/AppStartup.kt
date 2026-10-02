package fr.olivier.forme.core

import android.util.Log
import fr.olivier.forme.core.di.ApplicationScope
import fr.olivier.forme.feature.nutrition.data.CiqualLoader
import fr.olivier.forme.feature.nutrition.reminder.CreatineReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Background work run once per process start. */
@Singleton
class AppStartup @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val ciqual: CiqualLoader,
    private val creatineReminder: CreatineReminder,
) {
    fun run() {
        scope.launch {
            runCatching { creatineReminder.reschedule() }.onFailure { Log.e("Forme", "Reminder scheduling failed", it) }
            runCatching { ciqual.ensureLoaded() }.onFailure { Log.e("Forme", "Ciqual load failed", it) }
        }
    }
}
