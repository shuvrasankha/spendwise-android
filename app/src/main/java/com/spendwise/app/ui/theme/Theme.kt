package com.spendwise.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.spendwise.app.data.prefs.ThemeMode

/** Meanings Material 3 has no slot for. Owed/over uses `colorScheme.error`; money and selected use `primary`. */
@Immutable
data class MoneyColors(val moneyIn: Color)

private val LocalMoneyColors = staticCompositionLocalOf { MoneyColors(LightMoneyIn) }

private fun scheme(dark: Boolean): ColorScheme {
    val bg = if (dark) DarkBackground else LightBackground
    val container = if (dark) DarkContainer else LightContainer
    val containerHigh = if (dark) DarkContainerHigh else LightContainerHigh
    val text = if (dark) DarkText else LightText
    val muted = if (dark) DarkTextMuted else LightTextMuted
    val accent = if (dark) DarkAccent else LightAccent
    val accentContainer = if (dark) DarkAccentContainer else LightAccentContainer
    val owed = if (dark) DarkOwed else LightOwed
    val onAccent = if (dark) DarkBackground else LightBackground
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent, onPrimary = onAccent,
        primaryContainer = accentContainer, onPrimaryContainer = text,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = accentContainer, onSecondaryContainer = text,
        tertiary = accent, onTertiary = onAccent,
        tertiaryContainer = accentContainer, onTertiaryContainer = text,
        background = bg, onBackground = text,
        surface = bg, onSurface = text,
        surfaceVariant = container, onSurfaceVariant = muted,
        surfaceTint = bg, // no tonal tint: surfaces stay flat
        surfaceBright = bg, surfaceDim = container,
        surfaceContainerLowest = bg, surfaceContainerLow = container, surfaceContainer = container,
        surfaceContainerHigh = containerHigh, surfaceContainerHighest = containerHigh,
        inverseSurface = text, inverseOnSurface = bg, inversePrimary = if (dark) LightAccent else DarkAccent,
        error = owed, onError = onAccent,
        errorContainer = container, onErrorContainer = owed,
        outline = if (dark) DarkOutline else LightOutline,
        outlineVariant = if (dark) DarkDivider else LightDivider,
        scrim = Color.Black,
    )
}

/** No dynamic colour (CLAUDE.md). [mode] comes from DevicePrefs. */
@Composable
fun SpendWiseTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    CompositionLocalProvider(LocalMoneyColors provides MoneyColors(if (dark) DarkMoneyIn else LightMoneyIn)) {
        MaterialTheme(colorScheme = scheme(dark), typography = Typography, content = content)
    }
}

object SpendWiseTheme {
    val moneyColors: MoneyColors
        @Composable @ReadOnlyComposable get() = LocalMoneyColors.current
}
