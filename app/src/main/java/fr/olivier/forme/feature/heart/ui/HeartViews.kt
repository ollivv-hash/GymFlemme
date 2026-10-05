package fr.olivier.forme.feature.heart.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.olivier.forme.feature.heart.data.SessionHeartEntity
import fr.olivier.forme.feature.heart.domain.HeartSeries
import fr.olivier.forme.feature.heart.domain.HeartZones
import fr.olivier.forme.feature.heart.domain.highZoneSeconds
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/** Heart rate peaks of one exercise's sets, in order ("Chest press" → 138, 142, 145). */
data class ExercisePeaks(val name: String, val peaks: List<Int>)

/**
 * Session heart rate curve: zone bands behind (when the maximum is known), the curve broken at gaps, and an orange
 * tick at the top for each validated set.
 */
@Composable
fun HeartChart(series: HeartSeries, hrMax: Int?, setTimes: List<Instant>, modifier: Modifier = Modifier) {
    val values = series.bpm.filter { it > 0 }
    if (values.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    val lineColor = MaterialTheme.colorScheme.onSurface
    val setColor = MaterialTheme.colorScheme.tertiary
    val totalSec = (series.bpm.size * series.intervalSec).coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        val minY = (values.min() - 10).coerceAtLeast(40).toFloat()
        val maxY = (maxOf(values.max(), hrMax ?: 0).coerceAtMost(values.max() + 25) + 5).toFloat()
        val left = 36.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 18.dp.toPx()
        fun x(sec: Double) = left + (sec / totalSec * (right - left)).toFloat()
        fun y(bpm: Float) = bottom - (bpm - minY) / (maxY - minY) * (bottom - top)

        if (hrMax != null) {
            (1..5).forEach { zone ->
                val bounds = HeartZones.bounds(zone, hrMax)
                val lo = bounds.first.toFloat().coerceIn(minY, maxY)
                val hi = (bounds.last + 1).toFloat().coerceIn(minY, maxY)
                if (hi > lo) {
                    drawRect(zoneColor(zone).copy(alpha = 0.14f), Offset(left, y(hi)), Size(right - left, y(lo) - y(hi)))
                }
            }
        }
        // Curve, broken where there is no measurement.
        var path: Path? = null
        series.bpm.forEachIndexed { i, bpm ->
            if (bpm <= 0) {
                path?.let { drawPath(it, lineColor, style = Stroke(width = 2.dp.toPx())) }
                path = null
                return@forEachIndexed
            }
            val p = Offset(x(i * series.intervalSec + series.intervalSec / 2.0), y(bpm.toFloat()))
            path = (path ?: Path().apply { moveTo(p.x, p.y) }).apply { lineTo(p.x, p.y) }
        }
        path?.let { drawPath(it, lineColor, style = Stroke(width = 2.dp.toPx())) }
        // Validated sets.
        setTimes.forEach { t ->
            val sec = Duration.between(series.startedAt, t).seconds.toDouble()
            if (sec in 0.0..totalSec.toDouble()) {
                val px = x(sec)
                drawLine(setColor, Offset(px, top - 6.dp.toPx()), Offset(px, top + 4.dp.toPx()), strokeWidth = 3.dp.toPx())
            }
        }
        // Labels: min / max heart rate, start / end time.
        listOf(values.min(), values.max()).distinct().forEach { v ->
            val layout = measurer.measure("$v", labelStyle)
            drawText(layout, topLeft = Offset(0f, y(v.toFloat()) - layout.size.height / 2))
        }
        val end = measurer.measure("${totalSec / 60} min", labelStyle)
        drawText(measurer.measure("0", labelStyle), topLeft = Offset(left, bottom + 2.dp.toPx()))
        drawText(end, topLeft = Offset(right - end.size.width, bottom + 2.dp.toPx()))
    }
}

/** Time per zone as one stacked bar, then a line per zone with time spent. */
@Composable
fun ZoneBreakdown(zoneSeconds: List<Int>, hrMax: Int) {
    val total = zoneSeconds.sum()
    if (total == 0) return
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
        zoneSeconds.forEachIndexed { i, sec ->
            if (sec > 0) Box(Modifier.weight(sec.toFloat()).height(14.dp).background(zoneColor(i + 1)))
        }
    }
    zoneSeconds.withIndex().reversed().forEach { (i, sec) ->
        if (sec == 0) return@forEach
        val zone = i + 1
        val bounds = HeartZones.bounds(zone, hrMax)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            Box(Modifier.size(10.dp).background(zoneColor(zone), CircleShape))
            Text(
                "  ${HeartZones.LABELS[i]} (${bounds.first}-${bounds.last})",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text("${minutes(sec)} · ${(sec * 100.0 / total).roundToInt()} %", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The "Fréquence cardiaque" section of a session summary. */
@Composable
fun SessionHeartSection(
    heart: SessionHeartEntity,
    setTimes: List<Instant>,
    peaks: List<ExercisePeaks>,
    suggestedHrMax: Int?,
    onAcceptHrMax: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${heart.avgBpm}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(" bpm moy.", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp, end = 16.dp))
        Text("${heart.maxBpm}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(" max", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp, end = 16.dp))
        heart.kcal?.let {
            Text("${it.roundToInt()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(" kcal", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
    Text(
        listOfNotNull(
            heart.kcalSource?.let { "calories ${it.label}" },
            "source : ${heart.source.label}",
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    HeartChart(heart.series, heart.hrMax, setTimes, Modifier.padding(top = 8.dp))
    if (setTimes.isNotEmpty()) {
        Text("▮ = série validée", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
    val hrMax = heart.hrMax
    if (hrMax == null) {
        Text(
            "Renseigne ton année de naissance (Config → Profil) pour les zones cardio.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    } else {
        Text(
            "Zones (FC max $hrMax) · intensité de travail (zone 3 et +) : ${minutes(highZoneSeconds(heart.zoneSeconds))}",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        ZoneBreakdown(heart.zoneSeconds, hrMax)
    }
    if (peaks.isNotEmpty()) {
        Text("Pic de FC par série", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
        peaks.forEach { ex ->
            Text("${ex.name} : ${ex.peaks.joinToString(" · ")}", style = MaterialTheme.typography.bodyMedium)
        }
    }
    suggestedHrMax?.let { value ->
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    "Tu as tenu $value bpm, au-dessus de ta FC max actuelle (${hrMax ?: "-"}). " +
                        "Ta FC max réelle est sans doute plus haute : l'ajuster rend les zones plus justes.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(Modifier.padding(top = 8.dp)) {
                    Button(onClick = { onAcceptHrMax(value) }) { Text("Passer à $value bpm") }
                    Box(Modifier.width(8.dp))
                    TextButton(onClick = { onAcceptHrMax(-1) }) { Text("Ignorer") }
                }
            }
        }
    }
}

private fun minutes(seconds: Int): String = if (seconds < 60) "$seconds s" else "${(seconds / 60.0).roundToInt()} min"
