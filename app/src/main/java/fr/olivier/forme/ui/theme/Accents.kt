package fr.olivier.forme.ui.theme

import androidx.compose.ui.graphics.Color
import fr.olivier.forme.R
import fr.olivier.forme.core.settings.AccentColor

/** An accent and the text drawn on it. */
data class AccentPair(val accent: Color, val onAccent: Color)

/** The 6 accents of Réglages → Apparence (V2.24); each pair is ≥ 4.5:1 (ThemeContrastTest). */
fun AccentColor.pair(): AccentPair = when (this) {
    AccentColor.GREEN -> AccentPair(Color(0xFF8BD48F), Color(0xFF0F2412))
    AccentColor.BLUE -> AccentPair(Color(0xFF8DBDF0), Color(0xFF0E1F33))
    AccentColor.ORANGE -> AccentPair(Color(0xFFF4B678), Color(0xFF2E1A07))
    AccentColor.LIME -> AccentPair(Color(0xFFB7E36A), Color(0xFF1A2608))
    AccentColor.LAVENDER -> AccentPair(Color(0xFFC3A6F2), Color(0xFF1F1236))
    AccentColor.CORAL -> AccentPair(Color(0xFFF29C8F), Color(0xFF330F0A))
}

val AccentColor.labelRes: Int
    get() = when (this) {
        AccentColor.GREEN -> R.string.accent_green
        AccentColor.BLUE -> R.string.accent_blue
        AccentColor.ORANGE -> R.string.accent_orange
        AccentColor.LIME -> R.string.accent_lime
        AccentColor.LAVENDER -> R.string.accent_lavender
        AccentColor.CORAL -> R.string.accent_coral
    }

/** WCAG 2 contrast ratio of two opaque colours (1 to 21). */
fun contrast(a: Color, b: Color): Double {
    fun channel(c: Float): Double = if (c <= 0.03928f) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    fun luminance(c: Color) = 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
    val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
    return (hi + 0.05) / (lo + 0.05)
}
