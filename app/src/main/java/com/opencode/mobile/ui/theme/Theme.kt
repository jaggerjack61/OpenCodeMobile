package com.opencode.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.opencode.mobile.data.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF1ECEC),
    onPrimary = Color(0xFF0D0D0D),
    primaryContainer = Color(0xFF1F1F1F),
    onPrimaryContainer = Color(0xFFF1ECEC),
    secondary = Color(0xFFB7B1B1),
    onSecondary = Color(0xFF141212),
    secondaryContainer = Color(0xFF2A2727),
    onSecondaryContainer = Color(0xFFE8E3E3),
    tertiary = Color(0xFFCFCECD),
    onTertiary = Color(0xFF1A1919),
    tertiaryContainer = Color(0xFF333030),
    onTertiaryContainer = Color(0xFFEDEAEA),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFF1ECEC),
    surface = Color(0xFF0D0D0D),
    onSurface = Color(0xFFF1ECEC),
    surfaceVariant = Color(0xFF2A2727),
    onSurfaceVariant = Color(0xFFB7B1B1),
    surfaceContainerLowest = Color(0xFF080808),
    surfaceContainerLow = Color(0xFF141414),
    surfaceContainer = Color(0xFF181818),
    surfaceContainerHigh = Color(0xFF1F1F1F),
    surfaceContainerHighest = Color(0xFF262626),
    outline = Color(0xFF4B4646),
    outlineVariant = Color(0xFF2A2727),
    error = Color(0xFFFF6B63),
    onError = Color(0xFF2A0000),
    errorContainer = Color(0xFF3D0B0A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF1ECEC),
    inverseOnSurface = Color(0xFF1A1919),
    inversePrimary = Color(0xFF211E1E),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF211E1E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5E1E1),
    onPrimaryContainer = Color(0xFF211E1E),
    secondary = Color(0xFF656363),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2DEDE),
    onSecondaryContainer = Color(0xFF211E1E),
    tertiary = Color(0xFF4B4646),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE9E9),
    onTertiaryContainer = Color(0xFF211E1E),
    background = Color(0xFFF8F6F6),
    onBackground = Color(0xFF211E1E),
    surface = Color(0xFFF8F6F6),
    onSurface = Color(0xFF211E1E),
    surfaceVariant = Color(0xFFE5E1E1),
    onSurfaceVariant = Color(0xFF656363),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2EFEF),
    surfaceContainer = Color(0xFFECE9E9),
    surfaceContainerHigh = Color(0xFFE6E3E3),
    surfaceContainerHighest = Color(0xFFE0DDDD),
    outline = Color(0xFF8E8B8B),
    outlineVariant = Color(0xFFCFCECD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF211E1E),
    inverseOnSurface = Color(0xFFF1ECEC),
    inversePrimary = Color(0xFFF1ECEC),
)

@Composable
fun OpenCodeTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = OpenCodeTypography,
        content = content,
    )
}
