package fr.olivier.forme.ui.theme

import androidx.compose.ui.graphics.Color
import fr.olivier.forme.core.settings.AccentColor
import org.junit.Assert.assertTrue
import org.junit.Test

/** V2.24: every accent, and the text of each theme, readable at WCAG AA (4.5:1). */
class ThemeContrastTest {
    private fun assertReadable(what: String, a: Color, b: Color) {
        val ratio = contrast(a, b)
        assertTrue("$what: %.2f:1, below 4.5:1".format(ratio), ratio >= 4.5)
    }

    @Test
    fun eachAccentAndItsText() {
        AccentColor.entries.forEach { accent ->
            val p = accent.pair()
            assertReadable("$accent: text on the accent", p.accent, p.onAccent)
            listOf(false, true).forEach { dark ->
                val c = (if (dark) DarkBento else LightBento).withAccent(p, dark)
                val theme = if (dark) "dark" else "light"
                assertReadable("$accent $theme: active tab icon", c.accent, c.onAccent)
                assertReadable("$accent $theme: level number", c.levelBar, c.onStrong)
                assertReadable("$accent $theme: ticked box", c.check, c.onCheck)
            }
        }
    }

    @Test
    fun textOnBackgroundInBothThemes() {
        listOf("light" to LightBento, "dark" to DarkBento).forEach { (theme, c) ->
            assertReadable("$theme: text on background", c.text, c.background)
            assertReadable("$theme: text on tile", c.text, c.tile)
            assertReadable("$theme: secondary text on tile", c.textSecondary, c.tile)
            assertReadable("$theme: secondary text on background", c.textSecondary, c.background)
            assertReadable("$theme: text on the contrasting tile", c.onContrastTile, c.contrastTile)
        }
    }

    @Test
    fun theFormulaMatchesKnownValues() {
        assertTrue(contrast(Color.Black, Color.White) in 20.99..21.01)
        assertTrue(contrast(Color.White, Color.White) in 0.99..1.01)
    }
}
