package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AegisDarkPrimary,
    onPrimary = AegisDarkOnPrimary,
    primaryContainer = AegisDarkPrimaryContainer,
    onPrimaryContainer = AegisDarkOnPrimaryContainer,
    secondary = AegisDarkSecondary,
    onSecondary = AegisDarkOnSecondary,
    secondaryContainer = AegisDarkSecondaryContainer,
    onSecondaryContainer = AegisDarkOnSecondaryContainer,
    tertiary = AegisDarkTertiary,
    onTertiary = AegisDarkOnTertiary,
    tertiaryContainer = AegisDarkTertiaryContainer,
    onTertiaryContainer = AegisDarkOnTertiaryContainer,
    background = AegisDarkBackground,
    onBackground = AegisDarkOnBackground,
    surface = AegisDarkSurface,
    onSurface = AegisDarkOnSurface,
    surfaceVariant = AegisDarkSurfaceVariant,
    onSurfaceVariant = AegisDarkOnSurfaceVariant,
    outline = AegisDarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = AegisLightPrimary,
    onPrimary = AegisLightOnPrimary,
    primaryContainer = AegisLightPrimaryContainer,
    onPrimaryContainer = AegisLightOnPrimaryContainer,
    secondary = AegisLightSecondary,
    onSecondary = AegisLightOnSecondary,
    secondaryContainer = AegisLightSecondaryContainer,
    onSecondaryContainer = AegisLightOnSecondaryContainer,
    tertiary = AegisLightTertiary,
    onTertiary = AegisLightOnTertiary,
    tertiaryContainer = AegisLightTertiaryContainer,
    onTertiaryContainer = AegisLightOnTertiaryContainer,
    background = AegisLightBackground,
    onBackground = AegisLightOnBackground,
    surface = AegisLightSurface,
    onSurface = AegisLightOnSurface,
    surfaceVariant = AegisLightSurfaceVariant,
    onSurfaceVariant = AegisLightOnSurfaceVariant,
    outline = AegisLightOutline
)

@Composable
fun AegisBrowserTheme(
    darkTheme: Boolean = false, // Light theme by default per user instructions
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
