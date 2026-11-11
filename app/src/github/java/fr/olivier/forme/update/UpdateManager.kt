package fr.olivier.forme.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: UpdateManifest, val dismissed: Boolean) : UpdateState
    data class Downloading(val update: UpdateManifest, val progress: Float?) : UpdateState
    data class Ready(val update: UpdateManifest, val apk: File) : UpdateState
    data class Installing(val update: UpdateManifest) : UpdateState
    data class Failed(val error: UpdateError, val update: UpdateManifest?) : UpdateState
}

/**
 * The update of the "github" variant (V2.28): the check (at launch, once a day, or on demand), the download into the
 * app's cache with its SHA-256 checked, then the installation by Android's PackageInstaller, with its confirmation.
 * Nothing installs without the user's go; an error never blocks the app, the next launch checks again.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: UpdateClient,
    private val dataStore: DataStore<Preferences>,
    private val clock: Clock,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state = _state.asStateFlow()
    private var job: Job? = null

    val wifiOnly: Flow<Boolean> = dataStore.data.map { it[WIFI_ONLY] ?: true }

    fun setWifiOnly(enabled: Boolean) = scope.launch { dataStore.edit { it[WIFI_ONLY] = enabled } }

    val installedVersionCode: Long
        get() = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    private val dir get() = File(context.cacheDir, "updates").apply { mkdirs() }

    /** At launch: at most once every 24 hours. */
    fun checkIfDue() {
        if (System.getProperty("gymflemme.skipUpdateCheck") == "true") return
        scope.launch {
            // A previous download is of no use once installed (or replaced by a newer one).
            dir.listFiles()?.forEach { it.delete() }
            val last = dataStore.data.first()[LAST_CHECK]?.let(Instant::ofEpochMilli)
            if (UpdatePolicy.checkDue(last, clock.instant())) check()
        }
    }

    /** "Rechercher une mise à jour". */
    fun checkNow() {
        if (job?.isActive == true) return
        job = scope.launch { check() }
    }

    private suspend fun check() {
        _state.value = UpdateState.Checking
        _state.value = try {
            val manifest = client.latest()
            dataStore.edit { it[LAST_CHECK] = clock.instant().toEpochMilli() }
            when {
                !UpdatePolicy.isNewer(manifest, installedVersionCode) -> UpdateState.UpToDate
                !UpdatePolicy.compatible(manifest, Build.VERSION.SDK_INT) -> UpdateState.Failed(UpdateError.INCOMPATIBLE, manifest)
                else -> UpdateState.Available(manifest, dismissed = dataStore.data.first()[DISMISSED] == manifest.versionCode)
            }
        } catch (e: UpdateException) {
            UpdateState.Failed(e.error, null)
        } catch (e: Exception) {
            UpdateState.Failed(UpdateError.INVALID, null)
        }
    }

    /** "Plus tard" on the home card: hidden until a newer version. */
    fun dismiss() {
        val s = _state.value as? UpdateState.Available ?: return
        _state.value = s.copy(dismissed = true)
        scope.launch { dataStore.edit { it[DISMISSED] = s.update.versionCode } }
    }

    /** True on an unmetered network (Wi-Fi, Ethernet). */
    fun onUnmeteredNetwork(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    /** Downloads the update ([forced]: "Télécharger quand même" on mobile data), then checks its SHA-256. */
    fun download(update: UpdateManifest, forced: Boolean) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!UpdatePolicy.mayDownload(wifiOnly.first(), onUnmeteredNetwork(), forced)) return@launch
            val apk = File(dir, "gymflemme-${update.versionName}.apk")
            _state.value = UpdateState.Downloading(update, 0f)
            _state.value = try {
                client.download(update.apkUrl, apk) { p -> _state.value = UpdateState.Downloading(update, p) }
                if (UpdatePolicy.checksumMatches(apk, update.sha256)) {
                    UpdateState.Ready(update, apk)
                } else {
                    apk.delete()
                    UpdateState.Failed(UpdateError.CHECKSUM, update)
                }
            } catch (e: UpdateException) {
                apk.delete()
                UpdateState.Failed(e.error, update)
            }
        }
    }

    /** Android 8+: "Installer des applis inconnues" granted to GymFlemme. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Hands the checked APK to Android's installer; the system asks for confirmation. */
    fun install() {
        val s = _state.value as? UpdateState.Ready ?: return
        _state.value = UpdateState.Installing(s.update)
        scope.launch {
            try {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("gymflemme.apk", 0, s.apk.length()).use { out ->
                        s.apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val intent = Intent(context, InstallReceiver::class.java).setPackage(context.packageName)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
                }
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(UpdateError.INSTALL, s.update)
            }
        }
    }

    /** For the screen tests only: a state as the check or the download would leave it. */
    internal fun showForTest(state: UpdateState) {
        _state.value = state
    }

    /** From [InstallReceiver]: the user cancelled, or Android refused (another signature, not enough space…). */
    fun installFailed() {
        val s = _state.value
        val update = (s as? UpdateState.Installing)?.update
        _state.value = UpdateState.Failed(UpdateError.INSTALL, update)
    }

    private companion object {
        val LAST_CHECK = longPreferencesKey("update_last_check")
        val DISMISSED = intPreferencesKey("update_dismissed_version")
        val WIFI_ONLY = booleanPreferencesKey("update_wifi_only")
    }
}
