package com.callgalli.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callgalli.data.ThemeMode

// Deep teal brand colour.
private val Teal = Color(0xFF0B6E69)
private val TealLight = Color(0xFF7FD3CB)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDEA),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A6361),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E5),
    onSecondaryContainer = Color(0xFF051F1E),
    background = Color(0xFFF6FAF9),
    onBackground = Color(0xFF161D1C),
    surface = Color(0xFFF6FAF9),
    onSurface = Color(0xFF161D1C),
    surfaceVariant = Color(0xFFDAE5E3),
    onSurfaceVariant = Color(0xFF3F4948),
    surfaceContainerLow = Color(0xFFEFF5F4),
    surfaceContainer = Color(0xFFE9EFEE),
    surfaceContainerHigh = Color(0xFFE3EAE9),
    outline = Color(0xFF6F7978),
    outlineVariant = Color(0xFFBEC9C7),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF003734),
    primaryContainer = Color(0xFF00504C),
    onPrimaryContainer = Color(0xFF9EF2EA),
    secondary = Color(0xFFB0CCC9),
    onSecondary = Color(0xFF1C3533),
    secondaryContainer = Color(0xFF324B49),
    onSecondaryContainer = Color(0xFFCCE8E5),
    background = Color(0xFF0F1514),
    onBackground = Color(0xFFDEE4E2),
    surface = Color(0xFF0F1514),
    onSurface = Color(0xFFDEE4E2),
    surfaceVariant = Color(0xFF3F4948),
    onSurfaceVariant = Color(0xFFBEC9C7),
    surfaceContainerLow = Color(0xFF171D1C),
    surfaceContainer = Color(0xFF1B2120),
    surfaceContainerHigh = Color(0xFF252B2A),
    outline = Color(0xFF889391),
    outlineVariant = Color(0xFF3F4948),
    error = Color(0xFFFFB4AB),
)

/** Green / amber / red used for chips, the timer pill and progress bars. */
@Immutable
data class StatusColors(
    val green: Color, val onGreen: Color, val greenContainer: Color, val onGreenContainer: Color,
    val amber: Color, val onAmber: Color, val amberContainer: Color, val onAmberContainer: Color,
    val red: Color, val onRed: Color, val redContainer: Color, val onRedContainer: Color,
)

private val LightStatus = StatusColors(
    green = Color(0xFF2E7D32), onGreen = Color.White, greenContainer = Color(0xFFD7F0D8), onGreenContainer = Color(0xFF0B3A0E),
    amber = Color(0xFFF2A600), onAmber = Color(0xFF2B1D00), amberContainer = Color(0xFFFFE7B0), onAmberContainer = Color(0xFF3D2A00),
    red = Color(0xFFC62828), onRed = Color.White, redContainer = Color(0xFFFFDAD6), onRedContainer = Color(0xFF5C0A0A),
)

private val DarkStatus = StatusColors(
    green = Color(0xFF7BC67E), onGreen = Color(0xFF00390A), greenContainer = Color(0xFF1E4D22), onGreenContainer = Color(0xFFC5EBC6),
    amber = Color(0xFFFFC94D), onAmber = Color(0xFF3D2A00), amberContainer = Color(0xFF5A4300), onAmberContainer = Color(0xFFFFE7B0),
    red = Color(0xFFFF8A80), onRed = Color(0xFF5C0A0A), redContainer = Color(0xFF7A1D1D), onRedContainer = Color(0xFFFFDAD6),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

// Body text never below 16sp so it is easy to read.
private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

/** 8dp spacing grid. */
object Spacing {
    val xs = 4.dp
    val s = 8.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
}

@Composable
fun CallGalliTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
