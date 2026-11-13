package com.gammagamma.lax.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.gammagamma.lax.app.Appearance
import com.gammagamma.lax.app.Settings

@Immutable
data class LaxColors(
    val background: Color,
    val surface: Color,
    val sidebar: Color,
    val rail: Color,
    val hover: Color,
    val text: Color,
    val muted: Color,
    val border: Color,
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val accent: Color,
    val danger: Color,
    val success: Color,
    val avatars: List<Color>,
)

val LocalLaxColors = staticCompositionLocalOf { lightLaxColors() }

fun lightLaxColors(
    background: Color = Color(0xFFFEF7FF),
    surface: Color = Color(0xFFF3EDF7),
    sidebar: Color = Color(0xFFF3EDF7),
    rail: Color = Color(0xFFECE6F0),
    hover: Color = Color(0xFFECE6F0),
    text: Color = Color(0xFF1D1B20),
    muted: Color = Color(0xFF49454F),
    border: Color = Color(0xFFCAC4D0),
    primary: Color = Color(0xFF6750A4),
    onPrimary: Color = Color(0xFFFFFFFF),
    secondary: Color = Color(0xFF625B71),
    secondaryContainer: Color = Color(0xFFE8DEF8),
    accent: Color = Color(0xFF6750A4),
    danger: Color = Color(0xFFB3261E),
    success: Color = Color(0xFF386A20),
    avatars: List<Color> = listOf(
        Color(0xFF6750A4), Color(0xFF625B71), Color(0xFF7D5260), Color(0xFF386A20),
        Color(0xFF006A6A), Color(0xFF8C4A60), Color(0xFF00639B), Color(0xFF8F4C38),
    ),
) = LaxColors(
    background, surface, sidebar, rail, hover, text, muted, border,
    primary, onPrimary, secondary, secondaryContainer, accent, danger, success, avatars,
)

fun darkLaxColors(
    background: Color = Color(0xFF141218),
    surface: Color = Color(0xFF211F26),
    sidebar: Color = Color(0xFF211F26),
    rail: Color = Color(0xFF2B2930),
    hover: Color = Color(0xFF2B2930),
    text: Color = Color(0xFFE6E0E9),
    muted: Color = Color(0xFFCAC4D0),
    border: Color = Color(0xFF49454F),
    primary: Color = Color(0xFFD0BCFF),
    onPrimary: Color = Color(0xFF381E72),
    secondary: Color = Color(0xFFCCC2DC),
    secondaryContainer: Color = Color(0xFF4A4458),
    accent: Color = Color(0xFFD0BCFF),
    danger: Color = Color(0xFFFFB4AB),
    success: Color = Color(0xFF6FA35A),
    avatars: List<Color> = listOf(
        Color(0xFF9A82DB), Color(0xFF938F99), Color(0xFFB58392), Color(0xFF6FA35A),
        Color(0xFF4FB3B3), Color(0xFFC27D93), Color(0xFF5AA9E0), Color(0xFFC7826D),
    ),
) = LaxColors(
    background, surface, sidebar, rail, hover, text, muted, border,
    primary, onPrimary, secondary, secondaryContainer, accent, danger, success, avatars,
)

@Composable
fun LaxTheme(
    settings: Settings,
    content: @Composable () -> Unit,
) {
    val dark = when (settings.appearance) {
        Appearance.System -> isSystemInDarkTheme()
        Appearance.Dark -> true
        Appearance.Light -> false
    }
    val colors = if (dark) darkLaxColors() else lightLaxColors()
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.secondary,
            secondaryContainer = colors.secondaryContainer,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.text,
            onSurface = colors.text,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.primary,
            onPrimary = colors.onPrimary,
            secondary = colors.secondary,
            secondaryContainer = colors.secondaryContainer,
            background = colors.background,
            surface = colors.surface,
            onBackground = colors.text,
            onSurface = colors.text,
            error = colors.danger,
        )
    }
    CompositionLocalProvider(LocalLaxColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
