package fr.olivier.forme.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ChartPoint(val x: Double, val y: Double)

data class ChartSeries(val points: List<ChartPoint>, val color: Color, val showDots: Boolean = true)

/**
 * Minimal line chart drawn with Compose Canvas (no charting library needed for two curves).
 * [xLabel] formats the first and last x values; [yLabel] the min and max y values.
 */
@Composable
fun LineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    xLabel: (Double) -> String = { it.toString() },
    yLabel: (Double) -> String = { it.toString() },
) {
    val all = series.flatMap { it.points }
    if (all.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(200.dp)) {
        val minX = all.minOf { it.x }
        val maxX = all.maxOf { it.x }
        val rawMinY = all.minOf { it.y }
        val rawMaxY = all.maxOf { it.y }
        val pad = ((rawMaxY - rawMinY) * 0.1).takeIf { it > 0 } ?: 1.0
        val minY = rawMinY - pad
        val maxY = rawMaxY + pad
        val left = 44.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        val top = 8.dp.toPx()
        val right = size.width - 8.dp.toPx()

        fun px(p: ChartPoint) = Offset(
            x = if (maxX == minX) (left + right) / 2 else (left + ((p.x - minX) / (maxX - minX) * (right - left))).toFloat(),
            y = (bottom - ((p.y - minY) / (maxY - minY) * (bottom - top))).toFloat(),
        )

        listOf(rawMinY, rawMaxY).distinct().forEach { y ->
            val yPx = px(ChartPoint(minX, y)).y
            drawLine(gridColor, Offset(left, yPx), Offset(right, yPx), strokeWidth = 1f)
            val layout = measurer.measure(yLabel(y), labelStyle)
            drawText(layout, topLeft = Offset(0f, yPx - layout.size.height / 2))
        }
        val first = measurer.measure(xLabel(minX), labelStyle)
        val firstX = if (maxX == minX) (left + right - first.size.width) / 2 else left
        drawText(first, topLeft = Offset(firstX, bottom + 4.dp.toPx()))
        if (maxX != minX) {
            val last = measurer.measure(xLabel(maxX), labelStyle)
            drawText(last, topLeft = Offset(right - last.size.width, bottom + 4.dp.toPx()))
        }

        series.forEach { s ->
            val sorted = s.points.sortedBy { it.x }
            if (sorted.size > 1) {
                val path = Path()
                sorted.forEachIndexed { i, p -> px(p).let { if (i == 0) path.moveTo(it.x, it.y) else path.lineTo(it.x, it.y) } }
                drawPath(path, s.color, style = Stroke(width = 3.dp.toPx()))
            }
            if (s.showDots || sorted.size == 1) sorted.forEach { drawCircle(s.color, 4.dp.toPx(), px(it)) }
        }
    }
}
