package fr.olivier.forme.feature.heart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.olivier.forme.feature.heart.data.LiveHeart
import fr.olivier.forme.feature.heart.data.LiveHeartStatus
import fr.olivier.forme.feature.heart.domain.HeartZones
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Zone colors: 1 grey, 2 blue, 3 green, 4 orange, 5 red. */
fun zoneColor(zone: Int): Color = when (zone) {
    1 -> Color(0xFF9AA4B2)
    2 -> Color(0xFF4DA8FF)
    3 -> Color(0xFF3DBE6B)
    4 -> Color(0xFFFF8C1A)
    5 -> Color(0xFFE5484D)
    else -> Color(0xFF6B7280)
}

/** Rest is "recovered" once the heart rate is back under 60 % of the maximum (top of zone 1). */
fun recoveredBelow(hrMax: Int): Int = (hrMax * 0.6).roundToInt()

/** Current time, ticking every second (freshness of the live value). */
@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    return now
}

/**
 * Thin strip under the session title: live heart rate and zone, or what to do (enable the watch broadcast, grant
 * the Bluetooth permission). Hidden when no watch is configured.
 */
@Composable
fun LiveHeartStrip(live: LiveHeart, onGrantPermission: () -> Unit, onRetry: () -> Unit) {
    if (live.status == LiveHeartStatus.OFF || live.status == LiveHeartStatus.NO_DEVICE) return
    val now = rememberNow()
    val bpm = live.freshBpm(now)
    val zone = if (bpm != null && live.hrMax != null) HeartZones.zoneOf(bpm, live.hrMax) else 0
    val (text, action) = when (live.status) {
        LiveHeartStatus.CONNECTED -> (if (bpm != null) "$bpm bpm" + (if (zone > 0) " · zone $zone" else "") else "Montre connectée, en attente de mesure") to null
        LiveHeartStatus.WAITING -> "Active la diffusion FC sur la montre" to null
        LiveHeartStatus.RECONNECTING -> "Montre déconnectée, reconnexion…" to null
        LiveHeartStatus.NO_PERMISSION -> "FC : autoriser le Bluetooth (toucher)" to onGrantPermission
        LiveHeartStatus.BLUETOOTH_OFF -> "FC : active le Bluetooth puis touche ici" to onRetry
        else -> "" to null
    }
    val highlight = bpm == null
    Surface(
        color = if (highlight) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (highlight) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().let { m -> action?.let { m.clickable(onClick = it) } ?: m },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("♥", color = if (bpm != null) zoneColor(zone) else MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
            Box(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (bpm != null) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
            if (bpm != null && zone > 0) {
                Box(Modifier.size(10.dp).background(zoneColor(zone), CircleShape))
            }
        }
    }
}

/** Line shown in the rest bar: heart rate coming down, then "recovered" under 60 % of the maximum. */
@Composable
fun RecoveryLine(live: LiveHeart) {
    val now = rememberNow()
    val bpm = live.freshBpm(now) ?: return
    val hrMax = live.hrMax
    val text = when {
        hrMax == null -> "♥ $bpm bpm"
        bpm <= recoveredBelow(hrMax) -> "♥ $bpm bpm · ✓ récupéré"
        else -> "♥ $bpm bpm · récupéré sous ${recoveredBelow(hrMax)}"
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}
