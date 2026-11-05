package fr.olivier.forme.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import fr.olivier.forme.core.settings.ThemeMode

/*
 * Electric blue + orange.
 * Blue (primary) = actions: buttons, steppers, gauges, proposed session.
 * Orange (tertiary) = what needs attention: running session, records, progression hint, rest over, goal reached.
 * Neutrals are slightly blue-tinted so that no other hue competes.
 */

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DA8FF),
    onPrimary = Color(0xFF00213F),
    primaryContainer = Color(0xFF0B3A66),
    onPrimaryContainer = Color(0xFFD3E6FF),
    inversePrimary = Color(0xFF0A7CFF),
    secondary = Color(0xFFAFC4DD),
    onSecondary = Color(0xFF15273B),
    secondaryContainer = Color(0xFF22324A),
    onSecondaryContainer = Color(0xFFD6E5F7),
    tertiary = Color(0xFFFF8C1A),
    onTertiary = Color(0xFF2E1500),
    tertiaryContainer = Color(0xFF5A2D00),
    onTertiaryContainer = Color(0xFFFFDCC2),
    background = Color(0xFF0E1217),
    onBackground = Color(0xFFE1E6ED),
    surface = Color(0xFF0E1217),
    onSurface = Color(0xFFE1E6ED),
    surfaceVariant = Color(0xFF2A3340),
    onSurfaceVariant = Color(0xFFB8C3D1),
    surfaceContainerLowest = Color(0xFF0A0D11),
    surfaceContainerLow = Color(0xFF151A21),
    surfaceContainer = Color(0xFF19202A),
    surfaceContainerHigh = Color(0xFF222A35),
    surfaceContainerHighest = Color(0xFF2C3541),
    outline = Color(0xFF7D8896),
    outlineVariant = Color(0xFF3A4452),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0A7CFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E8FF),
    onPrimaryContainer = Color(0xFF001C38),
    inversePrimary = Color(0xFF4DA8FF),
    secondary = Color(0xFF4F6176),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE6F2),
    onSecondaryContainer = Color(0xFF0B1D2E),
    tertiary = Color(0xFFF07400),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0C7),
    onTertiaryContainer = Color(0xFF331400),
    background = Color(0xFFF8FAFD),
    onBackground = Color(0xFF181C21),
    surface = Color(0xFFF8FAFD),
    onSurface = Color(0xFF181C21),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F8),
    surfaceContainer = Color(0xFFEBEFF4),
    surfaceContainerHigh = Color(0xFFE4E9EF),
    surfaceContainerHighest = Color(0xFFDDE3EA),
    outline = Color(0xFF717880),
    outlineVariant = Color(0xFFC1C7CE),
)

@Composable
fun FormeTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = Typography(),
    ) {
        // V2.22: the bento tokens of the home screen follow the same theme.
        androidx.compose.runtime.CompositionLocalProvider(LocalBento provides if (dark) DarkBento else LightBento, content = content)
    }
}

/** Vivid orange button for the main call to action (start / resume a session). */
@Composable
fun accentButtonColors() = androidx.compose.material3.ButtonDefaults.buttonColors(
    containerColor = MaterialTheme.colorScheme.tertiary,
    contentColor = MaterialTheme.colorScheme.onTertiary,
)
