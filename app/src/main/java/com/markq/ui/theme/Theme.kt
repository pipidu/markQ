package com.markq.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF0B6E4F)
private val GreenLight = Color(0xFF14916A)
private val Cream = Color(0xFFF4FBF7)
private val Ink = Color(0xFF14221C)
private val GrayItem = Color(0xFF6B7C74)

private val LightColors = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    secondary = GreenLight,
    onSecondary = Color.White,
    background = Cream,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE3EFE8),
    onSurfaceVariant = GrayItem,
    error = Color(0xFFB42318),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FCBAA),
    onPrimary = Color(0xFF003828),
    secondary = Color(0xFF9AD4BE),
    background = Color(0xFF101714),
    onBackground = Color(0xFFE6F2EC),
    surface = Color(0xFF18211C),
    onSurface = Color(0xFFE6F2EC),
    surfaceVariant = Color(0xFF2A3832),
    onSurfaceVariant = Color(0xFFB7C7BF),
    error = Color(0xFFFFB4AB),
)

@Composable
fun MarkQTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
