package com.example.sesliportfoyara.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGold,
    onPrimary = OnSurfaceGold,
    secondary = Gold,
    background = DarkBackground,
    surface = SurfaceColor,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGold,
    onPrimary = OnSurfaceGold,
    secondary = Gold,
    background = Color.White,
    surface = Color(0xFFF5F5F5),
    onBackground = DarkBackground,
    onSurface = DarkBackground
)

@Composable
fun SesliportfoyaraTheme(
    darkTheme: Boolean = true, // Force dark theme to match screenshot
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
