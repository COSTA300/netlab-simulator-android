package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CosmicDarkColorScheme = darkColorScheme(
    primary = NetCyan,
    onPrimary = PremiumBlack,
    secondary = TextMuted,
    onSecondary = TextWhite,
    tertiary = WarningAmber,
    onTertiary = PremiumBlack,
    background = PremiumBlack,
    onBackground = TextWhite,
    surface = CardDark,
    onSurface = TextWhite,
    surfaceVariant = SlateDark,
    onSurfaceVariant = TextWhite
)

private val CosmicLightColorScheme = lightColorScheme(
    primary = NetCyan,
    onPrimary = PremiumBlack,
    secondary = TextMuted,
    onSecondary = PremiumBlack,
    tertiary = WarningAmber,
    background = PremiumBlack, // Force gorgeous dark theme universally for the grid canvas
    onBackground = TextWhite,
    surface = CardDark,
    onSurface = TextWhite,
    surfaceVariant = SlateDark,
    onSurfaceVariant = TextWhite
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable to preserve cohesive networking colors
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) CosmicDarkColorScheme else CosmicLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
