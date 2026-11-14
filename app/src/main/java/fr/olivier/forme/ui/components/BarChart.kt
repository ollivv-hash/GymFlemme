package fr.olivier.forme.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One bar: [value] against its own [goal] (dashed mark; none when null). */
data class Bar(val label: String, val value: Double, val goal: Double?)

/**
 * Bar chart drawn with Compose Canvas, like [LineChart] (V2.29: hydration of the last 7 days). Each bar has its
 * goal as a dashed line across it; [yLabel] formats the top value. [description] is read by TalkBack.
 */
@Composable
fun BarChart(
    bars: List<Bar>,
    color: Color,
    description: String,
    modifier: Modifier = Modifier,
    yLabel: (Double) -> String = { it.toString() },
) {
    if (bars.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = description }) {
        val max = (bars.maxOf { maxOf(it.value, it.goal ?: 0.0) }).takeIf { it > 0 } ?: 1.0
        val left = 44.dp.toPx()
        val right = size.width - 8.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 20.dp.toPx()
        fun y(v: Double) = (bottom - v / max * (bottom - top)).toFloat()

        drawLine(gridColor, Offset(left, bottom), Offset(right, bottom), strokeWidth = 1f)
        drawLine(gridColor, Offset(left, y(max)), Offset(right, y(max)), strokeWidth = 1f)
        val topLabel = measurer.measure(yLabel(max), labelStyle)
        drawText(topLabel, topLeft = Offset(0f, y(max) - topLabel.size.height / 2))

        val slot = (right - left) / bars.size
        val width = slot * 0.56f
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
        bars.forEachIndexed { i, bar ->
            val x = left + slot * i + (slot - width) / 2
            if (bar.value > 0) {
                val barTop = y(bar.value)
                drawRoundRect(color, Offset(x, barTop), Size(width, bottom - barTop), CornerRadius(4.dp.toPx()))
            }
            bar.goal?.let { goal ->
                val gy = y(goal)
                drawLine(goalColor, Offset(x - slot * 0.18f, gy), Offset(x + width + slot * 0.18f, gy), strokeWidth = 2.dp.toPx(), pathEffect = dash)
            }
            val label = measurer.measure(bar.label, labelStyle)
            drawText(label, topLeft = Offset(x + (width - label.size.width) / 2, bottom + 4.dp.toPx()))
        }
    }
}
