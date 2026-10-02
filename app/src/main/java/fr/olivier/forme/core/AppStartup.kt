package fr.olivier.forme.core

import android.util.Log
import fr.olivier.forme.core.di.ApplicationScope
import fr.olivier.forme.feature.nutrition.data.CiqualLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** Background work run once per process start. */
@Singleton
class AppStartup @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val ciqual: CiqualLoader,
) {
    fun run() {
        scope.launch {
            runCatching { ciqual.ensureLoaded() }.onFailure { Log.e("Forme", "Ciqual load failed", it) }
        }
    }
}
