package com.gonzaroman.acornix.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.gonzaroman.acornix.data.ThemeMode

// Acorn palette: forest green, warm nut and a golden accent.
private val Forest = Color(0xFF2E6B45)
private val ForestLight = Color(0xFF8FD8A6)
private val Nut = Color(0xFF8A5A2B)
private val NutLight = Color(0xFFF0BE84)
private val Gold = Color(0xFFB8860B)
private val GoldLight = Color(0xFFFFD27A)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4EFCF),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Nut,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCBC),
    onSecondaryContainer = Color(0xFF2D1600),
    tertiary = Gold,
    tertiaryContainer = Color(0xFFFFE7A8),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFFBF7F2),
    surface = Color(0xFFFBF7F2),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F1EA),
    surfaceContainer = Color(0xFFF0EBE3),
    surfaceContainerHigh = Color(0xFFEAE5DD),
    surfaceContainerHighest = Color(0xFFE4DFD7),
)

private val DarkColors = darkColorScheme(
    primary = ForestLight,
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF13502D),
    onPrimaryContainer = Color(0xFFC4EFCF),
    secondary = NutLight,
    onSecondary = Color(0xFF4A2800),
    secondaryContainer = Color(0xFF6A3C10),
    onSecondaryContainer = Color(0xFFFFDCBC),
    tertiary = GoldLight,
    tertiaryContainer = Color(0xFF5A4300),
    onTertiaryContainer = Color(0xFFFFE7A8),
    background = Color(0xFF14130F),
    surface = Color(0xFF14130F),
    surfaceContainerLowest = Color(0xFF0F0E0B),
    surfaceContainerLow = Color(0xFF1C1B17),
    surfaceContainer = Color(0xFF211F1B),
    surfaceContainerHigh = Color(0xFF2B2A25),
    surfaceContainerHighest = Color(0xFF363530),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Composable
fun AcornixTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
