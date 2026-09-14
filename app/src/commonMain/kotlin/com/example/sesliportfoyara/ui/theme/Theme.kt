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
    primary = Color(0xFFC5A059),      // Şık Premium Altın/Bronz rengi
    onPrimary = Color.White,
    secondary = Color(0xFF2C3E50),    // Koyu Lacivert/Mavi tonu (Sofistike kontrast)
    background = Color(0xFFF8F9FA),   // Tertemiz, ferah mat beyaz arka plan
    surface = Color.White,            // Kartlar tamamen beyaz, arkası hafif gri gölgeli duracak
    onBackground = Color(0xFF1A1A1A), // Yazılar koyu gri/siyah (okunabilir)
    onSurface = Color(0xFF222222)     // Kart üstü yazılar
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
