package fr.olivier.forme.feature.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.olivier.forme.core.db.context
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws the widget as the launcher would (RemoteViews applied to a real view) into build/smoke-screens. */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetRenderTest {
    @Test
    fun renderWidget() = runTest {
        val data = WidgetData("JAMBES-30 · Jambes – 30 min", false, 2, 3, 357.0, 1860, 6.0, 148.0)
        // Size measured on the phone (4 × 2 cells).
        val size = DpSize(343.dp, 168.dp)
        val views = GlanceRemoteViews().compose(context(), size) { WidgetContent(context(), data) }.remoteViews
        val density = context().resources.displayMetrics.density
        val w = (size.width.value * density).toInt()
        val h = (size.height.value * density).toInt()
        val parent = FrameLayout(context())
        val view = views.apply(context(), parent)
        parent.addView(view)
        parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        parent.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply { drawColor(0xFF3A5068.toInt()) }.also { parent.draw(it) }
        val dir = File("build/smoke-screens").apply { mkdirs() }
        File(dir, "widget.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
