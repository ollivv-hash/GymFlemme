package fr.olivier.forme.feature.heart.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import fr.olivier.forme.R
import fr.olivier.forme.feature.heart.data.HeartSessionRecorder
import fr.olivier.forme.feature.heart.data.LiveHeart
import fr.olivier.forme.feature.heart.data.LiveHeartStatus
import fr.olivier.forme.feature.heart.domain.HeartZones
import fr.olivier.forme.feature.sport.timer.RestTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service (type "connected device") running while a session records the watch's heart rate, so the
 * Bluetooth link and the recording survive the screen being off. Its notification shows the live heart rate.
 *
 * 2.19.3: the notification is not updated while the screen is off. On some phones each update lit the screen up
 * like a new notification; it is refreshed as soon as the screen turns back on.
 */
@AndroidEntryPoint
class HeartRateService : Service() {
    @Inject lateinit var recorder: HeartSessionRecorder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updates: Job? = null
    private val screenOn = MutableStateFlow(true)
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> screenOn.value = true
                Intent.ACTION_SCREEN_OFF -> screenOn.value = false
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification(this, recorder.state.value),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0,
            )
        }
        if (started.isFailure) {
            Log.e("Forme", "Heart rate service could not start in foreground", started.exceptionOrNull())
            stopSelf()
            return START_NOT_STICKY
        }
        if (updates == null) {
            screenOn.value = getSystemService(PowerManager::class.java).isInteractive
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                IntentFilter().apply {
                    addAction(Intent.ACTION_SCREEN_ON)
                    addAction(Intent.ACTION_SCREEN_OFF)
                },
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            updates = scope.launch {
                // Throttled: the notification text changes at most every 5 s, and only while the screen is on.
                shownTexts(recorder.state.map { text(it) }, screenOn).collect {
                    RestTimer.notify(this@HeartRateService, NOTIF_ID, notification(this@HeartRateService, recorder.state.value))
                    delay(UPDATE_EVERY_MS)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        if (updates != null) runCatching { unregisterReceiver(screenReceiver) }
        // Stopping the recording resets its state, which the collector above may have re-posted as a plain
        // notification between stopService() and here: that one would outlive the service ("Séance en cours"
        // left after the session was abandoned). Both run on the main thread, so nothing is posted after this.
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        super.onDestroy()
    }

    companion object {
        /** v2: default importance without sound, so it shows on the lock screen (see RestTimer.CHANNEL_RUNNING). */
        const val CHANNEL = "heart_live_v2"
        private const val CHANNEL_OLD = "heart_live"
        internal const val NOTIF_ID = 3
        private const val UPDATE_EVERY_MS = 5_000L

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.deleteNotificationChannel(CHANNEL_OLD)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Fréquence cardiaque en séance", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                },
            )
        }

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, HeartRateService::class.java)) }
                .onFailure { Log.e("Forme", "Heart rate service start refused", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HeartRateService::class.java))
            // Also when the service is already gone (killed with the app): a notification left behind is removed.
            context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        }

        /**
         * The texts to post: none while the screen is off, the latest one when it turns back on (only if it changed
         * meanwhile), the latest one after each pause of the throttle.
         */
        internal fun shownTexts(texts: Flow<String>, screenOn: Flow<Boolean>): Flow<String> =
            combine(texts, screenOn) { text, on -> text.takeIf { on } }
                .filterNotNull()
                .distinctUntilChanged()
                .conflate()

        fun text(live: LiveHeart): String = when (live.status) {
            LiveHeartStatus.CONNECTED -> live.bpm?.let { bpm ->
                val zone = live.hrMax?.let { HeartZones.zoneOf(bpm, it) }?.takeIf { it > 0 }
                "♥ $bpm bpm" + (zone?.let { " · zone $it" } ?: "")
            } ?: "♥ en attente de mesure"
            LiveHeartStatus.WAITING -> "Active la diffusion FC sur la montre"
            LiveHeartStatus.RECONNECTING -> "Montre déconnectée, reconnexion…"
            else -> "Séance en cours"
        }

        private fun notification(context: Context, live: LiveHeart) =
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Séance en cours")
                .setContentText(text(live))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(RestTimer.openAppIntent(context))
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                // Android 16 "Live Update": heart rate chip in the status bar and card on the lock screen.
                .setRequestPromotedOngoing(true)
                .apply { live.bpm?.takeIf { live.status == LiveHeartStatus.CONNECTED }?.let { setShortCriticalText("$it ♥") } }
                .build()
    }
}
