package fr.olivier.forme.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ColorFilter
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import fr.olivier.forme.R
import fr.olivier.forme.core.launch.QuickAction
import fr.olivier.forme.feature.sport.domain.Fmt
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    override val sizeMode = SizeMode.Exact

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun widgetData(): WidgetDataSource
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = EntryPointAccessors.fromApplication(context, Deps::class.java).widgetData().observe().first()
        val updatedAt = LocalDateTime.now()
        provideContent { WidgetContent(context, data, updatedAt) }
    }
}

/** The ↻ in the corner: redraws the widget from the database now. */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        FormeWidget().update(context, glanceId)
    }
}

class FormeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FormeWidget()
}

@Composable
fun WidgetContent(context: Context, data: WidgetData, updatedAt: LocalDateTime) {
    val start = actionStartActivity(QuickAction.START_SESSION.intent(context))
    // Rings and text as large as the widget allows (see ringSize); the start button keeps its size.
    val size = LocalSize.current
    val ring = ringSize(size.width, size.height)
    // Logo and update time sit in the corners left free by the round rings: they take no room from them.
    Box(modifier = GlanceModifier.fillMaxSize().background(WidgetColors.background).cornerRadius(20.dp)) {
        WidgetMain(context, data, ring, start)
        Box(modifier = GlanceModifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.TopEnd) {
            Image(
                provider = ImageProvider(R.drawable.ic_logo_g),
                contentDescription = "GymBro",
                colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.primary)),
                modifier = GlanceModifier.size(18.dp),
            )
        }
        Box(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.BottomEnd) {
            Row(
                modifier = GlanceModifier.clickable(actionRunCallback<RefreshWidgetAction>()).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(updatedLabel(updatedAt), style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = 10.sp))
                Spacer(GlanceModifier.width(3.dp))
                Image(
                    provider = ImageProvider(R.drawable.ic_refresh),
                    contentDescription = "Actualiser",
                    colorFilter = ColorFilter.tint(ColorProvider(WidgetColors.muted)),
                    modifier = GlanceModifier.size(12.dp),
                )
            }
        }
    }
}

/** "Màj 10:35" today, "Màj 6 oct. 22:06" when the widget was last drawn on an earlier day. */
internal fun updatedLabel(at: LocalDateTime, today: LocalDate = LocalDate.now()): String =
    "Màj " + if (at.toLocalDate() == today) at.format(TIME) else at.format(DAY_TIME)

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Fmt.locale)
private val DAY_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Fmt.locale)

@Composable
private fun WidgetMain(context: Context, data: WidgetData, ring: Float, start: Action) {
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(PADDING_DP.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(start)) {
            Text(
                if (data.sessionRunning) "Séance en cours" else "Prochaine séance",
                style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = 13.sp),
            )
            Text(
                data.sessionTitle ?: "Séance libre",
                style = TextStyle(color = ColorProvider(WidgetColors.text), fontSize = 17.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
            )
            Text(
                "Semaine : ${data.weekDone} / ${data.weekGoal}",
                style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = 14.sp),
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
                sizeDp = ring,
                value = data.kcal,
                target = kcalTarget,
                unit = "kcal",
                color = if (kcalTarget != null && data.kcal > kcalTarget * 1.1) WidgetColors.over else WidgetColors.primary,
            )
            Spacer(GlanceModifier.width(6.dp))
            val proteinTarget = data.proteinTargetG
            WidgetRing(
                sizeDp = ring,
                value = data.proteinG,
                target = proteinTarget,
                unit = "g prot.",
                color = if (proteinTarget != null && data.proteinG >= proteinTarget) WidgetColors.reached else WidgetColors.primary,
            )
        }
    }
}

@Composable
private fun WidgetRing(sizeDp: Float, value: Double, target: Double?, unit: String, color: Color) {
    // Text grows with the ring (sizes chosen for the 76 dp ring of the first version).
    val k = sizeDp / 76f
    val fraction = if (target != null && target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f
    Box(modifier = GlanceModifier.size(sizeDp.dp), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(ringBitmap(fraction, color)),
            contentDescription = null,
            modifier = GlanceModifier.size(sizeDp.dp),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                Fmt.number(value, 0),
                style = TextStyle(color = ColorProvider(WidgetColors.text), fontSize = (15 * k).sp, fontWeight = FontWeight.Bold),
            )
            Text(
                target?.let { "/ ${Fmt.number(it, 0)}" } ?: unit,
                style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = (10.5f * k).sp),
            )
            if (target != null) Text(unit, style = TextStyle(color = ColorProvider(WidgetColors.muted), fontSize = (10.5f * k).sp))
        }
    }
}

/**
 * Ring diameter (dp) for a widget of [width] × [height]: as tall as the widget allows, the two rings sharing the
 * width left by the session column ([TEXT_MIN_DP]) and the spacers.
 */
internal fun ringSize(width: Dp, height: Dp): Float {
    val byHeight = height.value - 2 * PADDING_DP
    val byWidth = (width.value - 2 * PADDING_DP - GAPS_DP - TEXT_MIN_DP) / 2
    return minOf(byHeight, byWidth).coerceIn(MIN_RING_DP, MAX_RING_DP)
}

private const val PADDING_DP = 12f
private const val GAPS_DP = 14f
private const val TEXT_MIN_DP = 135f
private const val MIN_RING_DP = 76f
private const val MAX_RING_DP = 150f
private const val RING_PX = 450 // 3× the largest ring: sharp on xxhdpi screens

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
