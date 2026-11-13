package fr.olivier.forme.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.olivier.forme.R

/**
 * "Bento" design of the home screen (V2.22): tokens of the validated mock-up (light) and of its dark variant. Flat
 * colours only, no shadow, no gradient.
 */
@Immutable
data class BentoColors(
    val background: Color,
    /** Ordinary tile, and the square buttons of the header. */
    val tile: Color,
    val text: Color,
    val textSecondary: Color,
    /** The one contrasting tile (calories): dark in the light theme, light in the dark theme. */
    val contrastTile: Color,
    val onContrastTile: Color,
    val onContrastTileSecondary: Color,
    val contrastTrack: Color,
    val caloriesBar: Color,
    /** Session tile, active tab, goal reached: the accent of Réglages → Apparence (V2.24), see [withAccent]. */
    val accent: Color,
    val onAccent: Color,
    /** Chip on the accent tile ("Sem. 2 / 3"). */
    val accentChip: Color,
    val track: Color,
    /** Filled segments of the challenge, level square and bar. */
    val strong: Color,
    val onStrong: Color,
    val levelBar: Color,
    val check: Color,
    val onCheck: Color,
    val nav: Color,
    val navInactive: Color,
)

val LightBento = BentoColors(
    background = Color(0xFFEEEEE9),
    tile = Color(0xFFFFFFFF),
    text = Color(0xFF16191B),
    textSecondary = Color(0xFF5E6468),
    contrastTile = Color(0xFF16191B),
    onContrastTile = Color(0xFFF2F3F0),
    onContrastTileSecondary = Color(0xFFA9AFB2),
    contrastTrack = Color(0xFF2C3134),
    caloriesBar = Color(0xFF8DBDF0),
    accent = Color(0xFF8BD48F),
    onAccent = Color(0xFF0F2412),
    accentChip = Color(0x8CFFFFFF),
    track = Color(0xFFDCDDD7),
    strong = Color(0xFF16191B),
    onStrong = Color(0xFFFFFFFF),
    levelBar = Color(0xFF16191B),
    check = Color(0xFF2E7D32),
    onCheck = Color(0xFFFFFFFF),
    nav = Color(0xFF16191B),
    navInactive = Color(0xFFC4C9CB),
)

/** Same structure on a dark background; the calories tile turns light, so that one tile still stands out. */
val DarkBento = BentoColors(
    background = Color(0xFF0D1012),
    tile = Color(0xFF1A1F22),
    text = Color(0xFFF2F3F0),
    textSecondary = Color(0xFFA9AFB2),
    contrastTile = Color(0xFFF2F3F0),
    onContrastTile = Color(0xFF16191B),
    onContrastTileSecondary = Color(0xFF5E6468),
    contrastTrack = Color(0xFFDCDDD7),
    caloriesBar = Color(0xFF3B7BC0),
    accent = Color(0xFF8BD48F),
    onAccent = Color(0xFF0F2412),
    accentChip = Color(0x8CFFFFFF),
    track = Color(0xFF2C3134),
    strong = Color(0xFFF2F3F0),
    onStrong = Color(0xFF0F2412),
    levelBar = Color(0xFF8BD48F),
    check = Color(0xFF8BD48F),
    onCheck = Color(0xFF0F2412),
    nav = Color(0xFF22282C),
    navInactive = Color(0xFFC4C9CB),
)

/**
 * The bento colours with [pair] as accent (V2.24). In the dark theme the accent also fills the level square and bar
 * and the ticked boxes; in the light theme those stay dark (an accent is too pale on a white tile for a box).
 */
fun BentoColors.withAccent(pair: AccentPair, dark: Boolean): BentoColors =
    if (dark) {
        copy(accent = pair.accent, onAccent = pair.onAccent, levelBar = pair.accent, onStrong = pair.onAccent, check = pair.accent, onCheck = pair.onAccent)
    } else {
        copy(accent = pair.accent, onAccent = pair.onAccent, check = text, onCheck = tile)
    }

val LocalBento = staticCompositionLocalOf { DarkBento }

/** Space Grotesk, embedded (variable font, OFL): one instance per weight used. */
val SpaceGrotesk = FontFamily(
    listOf(400, 500, 600, 700).map { w ->
        Font(R.font.space_grotesk, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

/** Text styles of the mock-up (sizes in sp, letter spacing as in the mock-up). */
object BentoType {
    private fun style(size: Int, weight: Int, spacing: Double = 0.0) =
        TextStyle(fontFamily = SpaceGrotesk, fontSize = size.sp, fontWeight = FontWeight(weight), letterSpacing = spacing.sp)

    val session = style(40, 700, -1.2).copy(lineHeight = 42.sp)
    val number = style(30, 600, -0.8)
    val greeting = style(24, 600, -0.5)
    val tileTitle = style(16, 600)
    val button = style(15, 600)
    val body = style(13, 500)
    val bodyStrong = style(14, 600)
    val label = style(12, 400)
    val labelStrong = style(12, 600)
    val labelCaps = style(12, 600, 1.0)
    val levelNumber = style(18, 700)
}

object BentoDims {
    val tileRadius = 26.dp
    val navRadius = 22.dp
    val squareRadius = 14.dp
    val screenMargin = 18.dp
    val gap = 10.dp
    val tilePadding = 16.dp
    val sessionPadding = 20.dp
    val touch = 44.dp
    /** Touch zone of the controls added since V2.29 (CLAUDE.md: 48 dp at least). */
    val touchMin = 48.dp
    val navHeight = 64.dp
    val navBottom = 16.dp
    val tileHeight = 170.dp
}
