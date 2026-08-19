package com.example.musicplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = Violet40,
    secondary = Teal40,
    tertiary = Magenta40,
)

private val DarkColorScheme = darkColorScheme(
    primary = Violet80,
    secondary = Teal80,
    tertiary = Magenta80,
)

/**
 * Tema global de Material Design 3. Aplica el esquema de color (claro/oscuro)
 * y la tipografía a toda la composición.
 */
@Composable
fun MusicPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}