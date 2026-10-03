package fr.olivier.forme.feature.heart.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.olivier.forme.core.di.ApplicationScope
import fr.olivier.forme.core.settings.SettingsRepository
import fr.olivier.forme.feature.heart.domain.HeartSource
import fr.olivier.forme.feature.heart.service.HeartRateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class LiveHeartStatus {
    /** No session is being recorded. */
    OFF,
    /** No watch chosen yet (Config → Montre cardio). */
    NO_DEVICE,
    NO_PERMISSION,
    BLUETOOTH_OFF,
    /** Waiting for the watch to broadcast its heart rate. */
    WAITING,
    CONNECTED,
    /** The link dropped; reconnects as soon as the watch broadcasts again. */
    RECONNECTING,
}

/** Live heart rate for the session screen and the notification. */
data class LiveHeart(
    val status: LiveHeartStatus = LiveHeartStatus.OFF,
    val bpm: Int? = null,
    val atMillis: Long? = null,
    val hrMax: Int? = null,
) {
    /** The last value if it is recent enough to be shown as live (the watch may have paused its broadcast). */
    fun freshBpm(nowMillis: Long): Int? = bpm?.takeIf { atMillis != null && nowMillis - atMillis < HeartSessionRecorder.STALE_AFTER_MS }
}

/** Raw measurements of the session being recorded, appended to a small file so a process kill loses nothing. */
class LiveSampleStore(private val dir: File) {
    private fun file(sessionId: Long) = File(dir.apply { mkdirs() }, "$sessionId.csv")

    fun append(sessionId: Long, epochMillis: Long, bpm: Int) = file(sessionId).appendText("$epochMillis,$bpm\n")

    fun read(sessionId: Long): List<Pair<Instant, Int>> {
        val f = file(sessionId)
        if (!f.exists()) return emptyList()
        return f.readLines().mapNotNull { line ->
            val parts = line.split(',')
            val millis = parts.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val bpm = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            Instant.ofEpochMilli(millis) to bpm
        }
    }

    fun delete(sessionId: Long) {
        file(sessionId).delete()
    }
}

/**
 * Records the heart rate broadcast by the watch during a session (V2.1 lot 2): keeps the Bluetooth link in session
 * mode, runs a foreground service so recording goes on screen off, stores every measurement, and builds the
 * session's curve and summary when the session ends.
 */
@Singleton
class HeartSessionRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val ble: BleHeartRateMonitor,
    private val settings: SettingsRepository,
    private val heart: HeartRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val store = LiveSampleStore(File(context.filesDir, "heart-live"))
    private val mutex = Mutex()
    private val mode = MutableStateFlow(LiveHeart())
    private var recordingSessionId: Long? = null
    private var collector: Job? = null

    val state: StateFlow<LiveHeart> = combine(mode, ble.state) { m, b ->
        if (m.status != LiveHeartStatus.CONNECTED) return@combine m
        when (val link = b.link) {
            is BleLink.Connected -> m.copy(status = LiveHeartStatus.CONNECTED, bpm = link.last?.bpm, atMillis = link.lastAtMillis)
            is BleLink.Reconnecting, is BleLink.Failed -> m.copy(status = LiveHeartStatus.RECONNECTING, bpm = null, atMillis = null)
            is BleLink.Connecting, BleLink.Idle -> m.copy(status = LiveHeartStatus.WAITING, bpm = null, atMillis = null)
        }
    }.stateIn(scope, SharingStarted.Eagerly, LiveHeart())

    /** Starts recording [sessionId]; does nothing if it is already recorded. */
    suspend fun start(sessionId: Long) = mutex.withLock {
        if (recordingSessionId == sessionId && mode.value.status !in RETRYABLE) return@withLock
        recordingSessionId = sessionId
        val hrMax = heart.heartProfile().hrMax
        val device = settings.hrDevice.first()
        val status = when {
            device == null -> LiveHeartStatus.NO_DEVICE
            !ble.hasPermissions() -> LiveHeartStatus.NO_PERMISSION
            !ble.state.value.available || !ble.isEnabled() -> LiveHeartStatus.BLUETOOTH_OFF
            else -> null
        }
        if (status != null || device == null) {
            mode.value = LiveHeart(status ?: LiveHeartStatus.NO_DEVICE, hrMax = hrMax)
            return@withLock
        }
        // "CONNECTED" here means "live mode": the actual link state comes from the Bluetooth monitor.
        mode.value = LiveHeart(LiveHeartStatus.CONNECTED, hrMax = hrMax)
        collector?.cancel()
        collector = scope.launch(Dispatchers.IO) {
            ble.measurements.collect { (millis, bpm) -> runCatching { store.append(sessionId, millis, bpm) } }
        }
        ble.connect(BleDevice(device.address, device.name, null, advertisesHeartRate = true, bonded = false), persistent = true)
        HeartRateService.start(context)
    }

    /** Retries after the user granted the permission or turned Bluetooth on. */
    suspend fun retry() {
        val id = recordingSessionId ?: return
        start(id)
    }

    /**
     * Stops recording; with [save] the session's heart rate is built from the stored measurements (the session must
     * already be finished, so its end time bounds the curve). The raw file is deleted either way.
     */
    suspend fun stop(sessionId: Long, save: Boolean) = mutex.withLock {
        collector?.cancel()
        collector = null
        ble.disconnect()
        HeartRateService.stop(context)
        recordingSessionId = null
        mode.value = LiveHeart()
        withContext(Dispatchers.IO) {
            if (save) {
                val samples = store.read(sessionId)
                if (samples.isNotEmpty()) {
                    runCatching { heart.saveSession(sessionId, samples, HeartSource.BLE) }
                        .onFailure { Log.e("Forme", "Heart rate save failed", it) }
                }
            }
            store.delete(sessionId)
        }
    }

    companion object {
        /** A value older than this is not shown as live (watch out of range, broadcast paused). */
        const val STALE_AFTER_MS = 10_000L
        private val RETRYABLE = setOf(LiveHeartStatus.NO_DEVICE, LiveHeartStatus.NO_PERMISSION, LiveHeartStatus.BLUETOOTH_OFF)
    }
}
