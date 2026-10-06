package fr.olivier.forme.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import fr.olivier.forme.core.launch.QuickAction
import fr.olivier.forme.feature.sport.domain.Fmt
import kotlinx.coroutines.flow.first

/** Colours of the app's dark theme: the widget sits on the wallpaper, a fixed dark card reads everywhere. */
private object WidgetColors {
    val background = Color(0xFF19202A)
    val text = Color(0xFFE1E6ED)
    val muted = Color(0xFFB8C3D1)
    val track = Color(0x33E1E6ED)
    val primary = Color(0xFF4DA8FF)
    val over = Color(0xFFFF8C1A)
    val reached = Color(0xFF43A047)
}

/**
 * Home-screen widget, 4 × 2 (V2.6): next session (or the running one), sessions of the week and a start button
 * on the left; today's calorie and protein rings on the right, which open the nutrition journal.
 */
class FormeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun widgetData(): WidgetDataSource
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = EntryPointAccessors.fromApplication(context, Deps::class.java).widgetData().observe().first()
        provideContent { WidgetContent(context, data) }
    }
}

class FormeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FormeWidget()
}

@Composable
fun WidgetContent(context: Context, data: WidgetData) {
    val start = actionStartActivity(QuickAction.START_SESSION.intent(context))
    Row(
        modifier = GlanceModifier.fillMaxSize().background(WidgetColors.background).cornerRadius(20.dp).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(start)) {
            Text(
                if (data.sessionRunning) "Séance en cours" else "Prochaine séance",
                style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = 12.sp),
            )
            Text(
                data.sessionTitle ?: "Séance libre",
                style = TextStyle(color = ColorProvider(WidgetColors.text), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            Text(
                "Semaine : ${data.weekDone} / ${data.weekGoal}",
                style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = 12.sp),
            )
            Spacer(GlanceModifier.defaultWeight())
            Button(
                text = if (data.sessionRunning) "Reprendre" else "Démarrer",
                onClick = start,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = ColorProvider(WidgetColors.primary),
                    contentColor = ColorProvider(Color(0xFF00213F)),
                ),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Row(
            modifier = GlanceModifier.clickable(actionStartActivity(QuickAction.OPEN_NUTRITION.intent(context))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val kcalTarget = data.kcalTarget?.toDouble()
            WidgetRing(
                value = data.kcal,
                target = kcalTarget,
                unit = "kcal",
                color = if (kcalTarget != null && data.kcal > kcalTarget * 1.1) WidgetColors.over else WidgetColors.primary,
            )
            Spacer(GlanceModifier.width(6.dp))
            val proteinTarget = data.proteinTargetG
            WidgetRing(
                value = data.proteinG,
                target = proteinTarget,
                unit = "g prot.",
                color = if (proteinTarget != null && data.proteinG >= proteinTarget) WidgetColors.reached else WidgetColors.primary,
            )
        }
    }
}

@Composable
private fun WidgetRing(value: Double, target: Double?, unit: String, color: Color) {
    val fraction = if (target != null && target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
    Box(modifier = GlanceModifier.size(RING_DP.dp), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(ringBitmap(fraction, color)),
            contentDescription = null,
            modifier = GlanceModifier.size(RING_DP.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Fmt.number(value, 0),
                style = TextStyle(color = ColorProvider(WidgetColors.text), fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                target?.let { "/ ${Fmt.number(it, 0)}" } ?: unit,
                style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = 10.sp),
            )
            if (target != null) Text(unit, style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = 10.sp))
        }
    }
}

private const val RING_DP = 76
private const val RING_PX = 228 // 3× the ring size: sharp on xxhdpi screens

/** Glance has no Canvas: the ring is drawn into a bitmap shown as an image, text on top. */
internal fun ringBitmap(fraction: Float, color: Color): Bitmap {
    val bitmap = createBitmap(RING_PX, RING_PX)
    val canvas = Canvas(bitmap)
    val stroke = RING_PX * 0.09f
    val bounds = RectF(stroke / 2, stroke / 2, RING_PX - stroke / 2, RING_PX - stroke / 2)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }
    paint.color = WidgetColors.track.toArgb()
    canvas.drawArc(bounds, 0f, 360f, false, paint)
    if (fraction > 0f) {
        paint.color = color.toArgb()
        canvas.drawArc(bounds, -90f, 360f * fraction, false, paint)
    }
    return bitmap
}
