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
    primary = Color(0xFFB8860B),      // Daha doygun, zengin Gold (DarkGoldenrod)
    onPrimary = Color.White,
    secondary = Color(0xFF1B2631),    // Çok koyu lacivert (Kontrast için ideal)
    background = Color(0xFFF0F2F5),   // Facebook/Twitter tarzı hafif gri-mavi arka plan
    surface = Color.White,            // Kar beyazı kartlar (Arka plandan ayrışır)
    onBackground = Color(0xFF121212), // Kömür siyahı yazı (Yüksek okunabilirlik)
    onSurface = Color(0xFF1A1A1A),    // Kart üstü net yazılar
    surfaceVariant = Color(0xFFE5E7EB) // Giriş alanları için belirgin gri
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
