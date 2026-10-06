package fr.olivier.forme.core

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.core.backup.AutoBackupScheduler
import fr.olivier.forme.core.di.ApplicationScope
import fr.olivier.forme.core.launch.QuickAction
import fr.olivier.forme.feature.nutrition.data.CiqualLoader
import fr.olivier.forme.feature.nutrition.reminder.CreatineReminder
import fr.olivier.forme.feature.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Background work run once per process start. */
@Singleton
class AppStartup @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    private val ciqual: CiqualLoader,
    private val creatineReminder: CreatineReminder,
    private val autoBackup: AutoBackupScheduler,
    private val widget: WidgetRefresher,
) {
    fun run() {
        autoBackup.scheduleDaily()
        widget.start()
        runCatching { QuickAction.publishShortcuts(context) }.onFailure { Log.e("Forme", "Shortcuts publishing failed", it) }
        scope.launch {
            runCatching { creatineReminder.reschedule() }.onFailure { Log.e("Forme", "Reminder scheduling failed", it) }
            runCatching { ciqual.ensureLoaded() }.onFailure { Log.e("Forme", "Ciqual load failed", it) }
        }
    }
}
