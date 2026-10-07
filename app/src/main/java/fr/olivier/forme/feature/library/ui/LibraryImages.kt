package fr.olivier.forme.feature.library.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.olivier.forme.feature.library.data.LibraryExerciseEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Library images, decoded from the APK assets (WebP, decoded natively) at the size they are shown and kept in
 * memory: a list of thumbnails never decodes the full 768×1024 frames.
 */
object LibraryImages {
    private val cache = object : LruCache<String, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** [path] in the assets, subsampled (power of 2) to about [targetWidthPx]; null if the file is missing. */
    fun load(context: Context, path: String, targetWidthPx: Int): ImageBitmap? {
        val key = "$path@$targetWidthPx"
        cache.get(key)?.let { return it }
        val assets = context.assets
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) } }.getOrNull()
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidthPx) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = runCatching { assets.open(path).use { BitmapFactory.decodeStream(it, null, options) } }.getOrNull()
            ?.asImageBitmap() ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private const val CACHE_BYTES = 24 * 1024 * 1024
}

/** The library's images sit on a dark studio background: always shown in a dark card, whatever the theme. */
val IllustrationBackground = Color(0xFF14181E)

/** Still first frame, for lists (no animation while scrolling). */
@Composable
fun LibraryThumb(entry: LibraryExerciseEntity, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    var bitmap by remember(entry.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(entry.id) { bitmap = withContext(Dispatchers.IO) { LibraryImages.load(context, entry.thumbnailPath, px) } }
    Box(modifier.size(size).clip(RoundedCornerShape(12)).background(IllustrationBackground)) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * The exercise in motion: cross-fade loop through the frames in their order (hold ~0.9 s, fade ~0.35 s); a "hold"
 * image stays still. Touching the image pauses or resumes it.
 */
@Composable
fun ExerciseIllustration(entry: LibraryExerciseEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val frames = entry.frameList
    var bitmaps by remember(entry.id) { mutableStateOf<List<ImageBitmap>?>(null) }
    var index by remember(entry.id) { mutableIntStateOf(0) }
    var paused by remember(entry.id) { mutableStateOf(false) }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(IllustrationBackground),
        contentAlignment = Alignment.Center,
    ) {
        val widthPx = constraints.maxWidth
        LaunchedEffect(entry.id, widthPx) {
            bitmaps = withContext(Dispatchers.IO) { frames.mapNotNull { LibraryImages.load(context, entry.framePath(it), widthPx) } }
        }
        LaunchedEffect(entry.id, paused, bitmaps) {
            val count = bitmaps?.size ?: 0
            if (paused || count < 2) return@LaunchedEffect
            while (true) {
                delay(HOLD_MS + FADE_MS)
                index = (index + 1) % count
            }
        }
        // Image ratio, but never taller than MAX_HEIGHT: name and muscles stay on the first screen.
        val height = minOf(maxWidth * entry.height.toFloat() / entry.width, MAX_HEIGHT)
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .clickable(enabled = frames.size > 1) { paused = !paused },
        ) {
            bitmaps?.let { list ->
                Crossfade(targetState = index.coerceIn(0, list.lastIndex), animationSpec = tween(FADE_MS.toInt()), label = "frames") { i ->
                    Image(list[i], contentDescription = entry.name, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                }
            }
            if (paused) {
                Text(
                    "Pause · touche pour reprendre",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB8C3D1),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                )
            }
        }
    }
}

private val MAX_HEIGHT = 340.dp
private const val HOLD_MS = 900L
private const val FADE_MS = 350L
