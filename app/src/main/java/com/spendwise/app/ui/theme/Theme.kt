package com.spendwise.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.spendwise.app.data.prefs.ThemeMode

/** Kept for screens not yet moved to [SpendWiseColors]; money in is `positive`. */
@Immutable
data class MoneyColors(val moneyIn: Color)

private fun scheme(c: SpendWiseColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent, onPrimary = c.onAccent,
        primaryContainer = c.surface, onPrimaryContainer = c.text,
        secondary = c.accent, onSecondary = c.onAccent,
        secondaryContainer = c.surface, onSecondaryContainer = c.text,
        tertiary = c.accent, onTertiary = c.onAccent,
        tertiaryContainer = c.surface, onTertiaryContainer = c.text,
        background = c.bg, onBackground = c.text,
        surface = c.bg, onSurface = c.text,
        surfaceVariant = c.line, onSurfaceVariant = c.textMuted,
        surfaceTint = c.bg, // no tonal tint: surfaces stay flat
        surfaceBright = c.bg, surfaceDim = c.surface,
        surfaceContainerLowest = c.bg, surfaceContainerLow = c.surface, surfaceContainer = c.surface,
        surfaceContainerHigh = c.line, surfaceContainerHighest = c.line,
        inverseSurface = c.text, inverseOnSurface = c.bg, inversePrimary = c.accent,
        error = c.negative, onError = c.bg,
        errorContainer = c.surface, onErrorContainer = c.negative,
        outline = c.line, outlineVariant = c.line,
        scrim = Color.Black,
    )
}

// Text fields (and menus, snackbars) take extraSmall: the 12dp control radius
private val AppShapes = Shapes(extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp))

/** No dynamic colour (CLAUDE.md). [mode] comes from DevicePrefs. */
@Composable
fun SpendWiseTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkSpendWiseColors else LightSpendWiseColors
    CompositionLocalProvider(LocalSpendWiseColors provides colors) {
        MaterialTheme(colorScheme = scheme(colors, dark), typography = Typography, shapes = AppShapes, content = content)
    }
}

object SpendWiseTheme {
    val colors: SpendWiseColors
        @Composable @ReadOnlyComposable get() = LocalSpendWiseColors.current

    val moneyColors: MoneyColors
        @Composable @ReadOnlyComposable get() = MoneyColors(colors.positive)
}
