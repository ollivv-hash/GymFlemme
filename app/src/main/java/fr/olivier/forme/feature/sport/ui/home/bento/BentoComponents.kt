package fr.olivier.forme.feature.sport.ui.home.bento

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.olivier.forme.ui.theme.BentoDims
import fr.olivier.forme.ui.theme.LocalBento

/**
 * A bento tile: flat colour, 26 dp corners, read by TalkBack as one element ([description] when given, else its
 * texts in order).
 */
@Composable
fun BentoTile(
    color: Color,
    modifier: Modifier = Modifier,
    description: String? = null,
    padding: Dp = BentoDims.tilePadding,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(BentoDims.tileRadius))
            .background(color)
            .let { m -> onClick?.let { m.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = it) } ?: m }
            .semantics(mergeDescendants = true) { description?.let { contentDescription = it } }
            .padding(padding),
        content = content,
    )
}

/** Whether the system "Remove animations" setting (animator duration scale 0) is on. */
@Composable
private fun animationsOff(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/** A progress bar that fills in 300 ms when shown (at once with animations removed). */
@Composable
fun ProgressTrack(fraction: Float, track: Color, bar: Color, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val target = fraction.coerceIn(0f, 1f)
    val off = animationsOff()
    val shown = remember { Animatable(if (off) target else 0f) }
    LaunchedEffect(target, off) { if (off) shown.snapTo(target) else shown.animateTo(target, tween(300)) }
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(track)) {
        if (shown.value > 0f) {
            Box(Modifier.fillMaxWidth(shown.value).fillMaxHeight().clip(RoundedCornerShape(height)).background(bar))
        }
    }
}

/** One segment per unit (challenge of the month, goal ≤ 15). */
@Composable
fun SegmentedProgress(done: Int, total: Int, on: Color, off: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        repeat(total) { i ->
            if (i > 0) Spacer(Modifier.width(3.dp))
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(3.dp)).background(if (i < done) on else off))
        }
    }
}

/** Square 44 dp button of the header (Réglages, Analyse IA). */
@Composable
fun SquareIconButton(icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    Box(
        modifier
            .size(BentoDims.touch)
            .clip(RoundedCornerShape(BentoDims.squareRadius))
            .background(c.tile)
            .clickable(onClickLabel = description, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = c.text, modifier = Modifier.size(20.dp))
    }
}

/** Grid row: two tiles side by side, or one over the whole width when the other is missing. */
@Composable
fun TileRow(left: (@Composable (Modifier) -> Unit)?, right: (@Composable (Modifier) -> Unit)?, modifier: Modifier = Modifier) {
    when {
        // Same height for both tiles of the row.
        left != null && right != null -> Row(modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            left(Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.width(BentoDims.gap))
            right(Modifier.weight(1f).fillMaxHeight())
        }
        left != null -> left(modifier.fillMaxWidth())
        right != null -> right(modifier.fillMaxWidth())
    }
}

/** Keeps a tile's padding inside a fixed width column (previews). */
internal val PreviewWidth = Modifier.width(390.dp).padding(BentoDims.screenMargin)
