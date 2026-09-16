package com.markq.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF0B6E4F)
private val GreenLight = Color(0xFF14916A)
private val Cream = Color(0xFFDCE8E1)
private val Ink = Color(0xFF14221C)
private val GrayItem = Color(0xFF4D5F56)
private val CardLight = Color(0xFFFFFFFF)
private val CardDark = Color(0xFF2A3C34)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = GreenLight,
    onSecondary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = CardLight,
    onSurface = Ink,
    surfaceVariant = Color(0xFFC5D5CC),
    onSurfaceVariant = GrayItem,
    error = Color(0xFFB42318),
    outline = Color(0xFF8AA396),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FCBAA),
    onPrimary = Color(0xFF003828),
    secondary = Color(0xFF9AD4BE),
    background = Color(0xFF0B100E),
    onBackground = Color(0xFFE6F2EC),
    surface = CardDark,
    onSurface = Color(0xFFE6F2EC),
    surfaceVariant = Color(0xFF1C2A24),
    onSurfaceVariant = Color(0xFFB7C7BF),
    error = Color(0xFFFFB4AB),
    outline = Color(0xFF5E7368),
)

@Composable
fun MarkQTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
