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
    primary = Color(0xFF22A447),      // Zengin Gold
    onPrimary = Color.White,
    secondary = Color(0xFF1B2631),    // Lacivert
    background = Color(0xFFE8ECEF),   // Daha belirgin gri-mavi zemin (Fark edilsin diye)
    surface = Color.White,            // Kar beyazı kartlar
    onBackground = Color(0xFF000000), // Tam siyah yazı
    onSurface = Color(0xFF000000),    // Tam siyah yazı
    surfaceVariant = Color(0xFFD1D5DB) // Giriş alanları için daha koyu gri
)

@Composable
fun SesliportfoyaraTheme(
    darkTheme: Boolean = false, // Default to light theme now
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
