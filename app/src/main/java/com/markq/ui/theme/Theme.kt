package com.markq.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.markq.core.MarkColor
import com.markq.core.UiThemeDefaults

data class MarkQUiColors(
    val bar: Color,
    val onBar: Color,
    val background: Color,
    val onBackground: Color,
    val fab: Color,
    val onFab: Color,
    val cardBorder: Color,
)

val LocalMarkQUiColors = staticCompositionLocalOf {
    MarkQUiColors(
        bar = hexToColor(UiThemeDefaults.BAR, UiThemeDefaults.BAR),
        onBar = onColorFor(UiThemeDefaults.BAR),
        background = hexToColor(UiThemeDefaults.BACKGROUND, UiThemeDefaults.BACKGROUND),
        onBackground = onColorFor(UiThemeDefaults.BACKGROUND),
        fab = hexToColor(UiThemeDefaults.FAB, UiThemeDefaults.FAB),
        onFab = onColorFor(UiThemeDefaults.FAB),
        cardBorder = hexToColor(UiThemeDefaults.CARD_BORDER, UiThemeDefaults.CARD_BORDER),
    )
}

private val Ink = Color(0xFF14221C)
/** Off-white so white cards/FAB keep a real shadow instead of a green tonal overlay. */
private val SurfaceAnchor = Color(0xFFF3F5F4)

internal fun hexToColor(hex: String?, fallback: String): Color {
    val argb = MarkColor.parseArgb(hex) ?: MarkColor.parseArgb(fallback)!!
    return Color(argb.toInt())
}

internal fun onColorFor(hex: String?, fallback: String = UiThemeDefaults.BACKGROUND): Color {
    val resolved = MarkColor.normalize(hex) ?: fallback
    return if (MarkColor.isLight(resolved)) Ink else Color.White
}

@Composable
fun MarkQTheme(
    barHex: String = UiThemeDefaults.BAR,
    backgroundHex: String = UiThemeDefaults.BACKGROUND,
    fabHex: String = UiThemeDefaults.FAB,
    content: @Composable () -> Unit,
) {
    val bar = hexToColor(barHex, UiThemeDefaults.BAR)
    val background = hexToColor(backgroundHex, UiThemeDefaults.BACKGROUND)
    val fab = hexToColor(fabHex, UiThemeDefaults.FAB)
    val onBar = onColorFor(barHex, UiThemeDefaults.BAR)
    val onBackground = onColorFor(backgroundHex, UiThemeDefaults.BACKGROUND)
    val onFab = onColorFor(fabHex, UiThemeDefaults.FAB)
    val cardBorder = hexToColor(UiThemeDefaults.CARD_BORDER, UiThemeDefaults.CARD_BORDER)
    val ui = MarkQUiColors(
        bar = bar,
        onBar = onBar,
        background = background,
        onBackground = onBackground,
        fab = fab,
        onFab = onFab,
        cardBorder = cardBorder,
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = MarkColor.isLight(
                    MarkColor.normalize(barHex) ?: UiThemeDefaults.BAR,
                )
                isAppearanceLightNavigationBars = MarkColor.isLight(
                    MarkColor.normalize(backgroundHex) ?: UiThemeDefaults.BACKGROUND,
                )
            }
        }
    }

    val colors = lightColorScheme(
        primary = bar,
        onPrimary = onBar,
        secondary = bar,
        onSecondary = onBar,
        background = background,
        onBackground = onBackground,
        surface = SurfaceAnchor,
        onSurface = Ink,
        surfaceVariant = background,
        onSurfaceVariant = Color(0xFF4D5F56),
        error = Color(0xFFB42318),
        outline = cardBorder,
    )

    CompositionLocalProvider(LocalMarkQUiColors provides ui) {
        MaterialTheme(
            colorScheme = colors,
            content = content,
        )
    }
}
