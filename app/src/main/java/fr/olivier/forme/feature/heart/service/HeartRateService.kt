package fr.olivier.forme.feature.heart.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service (type "connected device") running while a session records the watch's heart rate, so the
 * Bluetooth link and the recording survive the screen being off. Its notification shows the live heart rate.
 */
@AndroidEntryPoint
class HeartRateService : Service() {
    @Inject lateinit var recorder: HeartSessionRecorder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updates: Job? = null

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
            updates = scope.launch {
                // Throttled: the notification text changes at most every 5 s.
                recorder.state.map { text(it) }.distinctUntilChanged().collect {
                    RestTimer.notify(this@HeartRateService, NOTIF_ID, notification(this@HeartRateService, recorder.state.value))
                    delay(UPDATE_EVERY_MS)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "heart_live"
        private const val NOTIF_ID = 3
        private const val UPDATE_EVERY_MS = 5_000L

        fun createChannel(context: Context) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Fréquence cardiaque en séance", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
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
        }

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
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(RestTimer.openAppIntent(context))
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()
    }
}
