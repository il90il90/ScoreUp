package com.scoreup.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.scoreup.app.data.ThemeMode

object Arcade {
    val Night = Color(0xFF070A14)
    val Panel = Color(0xFF111729)
    val PanelHi = Color(0xFF1A2240)
    val Neon = Color(0xFFFFD60A)
    val Hot = Color(0xFFFF3D57)
    val Cool = Color(0xFF00C2FF)
    val Lime = Color(0xFFB4FF39)
    val Muted = Color(0xFF8A93B2)
}

private val DarkScheme = darkColorScheme(
    primary = Arcade.Neon, onPrimary = Color.Black,
    secondary = Arcade.Cool, onSecondary = Color.Black,
    tertiary = Arcade.Hot,
    background = Arcade.Night, onBackground = Color.White,
    surface = Arcade.Panel, onSurface = Color.White,
    surfaceVariant = Arcade.PanelHi, onSurfaceVariant = Color(0xFFC9D0E8),
    surfaceContainer = Arcade.Panel, surfaceContainerHigh = Arcade.PanelHi,
    outline = Color(0xFF2E3A63),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1B2BFF), onPrimary = Color.White,
    secondary = Color(0xFF0091C2), tertiary = Color(0xFFE5173A),
    background = Color(0xFFF3F5FC), onBackground = Color(0xFF0B1020),
    surface = Color.White, onSurface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFFE4E8F6), onSurfaceVariant = Color(0xFF39425E),
    outline = Color(0xFFB9C1DA),
)

/** Score digits: heavy, tabular so numbers don't jitter while counting. */
val ScoreDigits = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Black, fontFeatureSettings = "tnum", letterSpacing = (-2).sp)
val ClockDigits = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Black),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
    )
}

@Composable
fun ScoreUpTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.DARK -> true; ThemeMode.LIGHT -> false }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, content = content)
}

fun Long.toColor() = Color(this.toInt())

/** Readable text color on top of a team color. */
fun Color.onColor(): Color {
    val l = 0.299 * red + 0.587 * green + 0.114 * blue
    return if (l > 0.62) Color(0xFF0B1020) else Color.White
}
