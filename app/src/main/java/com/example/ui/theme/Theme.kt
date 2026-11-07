package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberDarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF8CF4FF),

    secondary = CyberEmerald,
    onSecondary = Color(0xFF003919),
    secondaryContainer = Color(0xFF005327),
    onSecondaryContainer = Color(0xFF66FFA3),

    tertiary = CyberBlue,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF004494),
    onTertiaryContainer = Color(0xFFD8E2FF),

    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkSurfaceBorder,
    error = CyberRed
)

private val CyberLightColorScheme = lightColorScheme(
    primary = Color(0xFF006877),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF97F0FF),
    onPrimaryContainer = Color(0xFF001F24),

    secondary = Color(0xFF006D35),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF75FFAE),
    onSecondaryContainer = Color(0xFF00210C),

    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1),
    error = CyberRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to high-contrast Dark theme for remote desktop clarity
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CyberDarkColorScheme else CyberLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
