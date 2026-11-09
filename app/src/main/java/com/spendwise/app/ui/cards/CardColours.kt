package com.spendwise.app.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.spendwise.app.ui.theme.SpendWiseTheme

// Light theme uses the darker shade and dark theme the lighter one, so each keeps 3:1 against the background
private val SHADES = mapOf(
    "Blue" to (Color(0xFF2563EB) to Color(0xFF60A5FA)),
    "Violet" to (Color(0xFF7C3AED) to Color(0xFFA78BFA)),
    "Teal" to (Color(0xFF0F766E) to Color(0xFF2DD4BF)),
    "Pink" to (Color(0xFFDB2777) to Color(0xFFF472B6)),
    "Brown" to (Color(0xFF92400E) to Color(0xFFD6A26B)),
    "Slate" to (Color(0xFF475569) to Color(0xFF94A3B8)),
    "Orange" to (Color(0xFFC2410C) to Color(0xFFFB923C)),
)

/** A card colour's shade in the current theme; Unspecified for an unknown name. */
@Composable
fun cardColor(name: String?): Color {
    val (light, dark) = SHADES[name] ?: return Color.Unspecified
    return if (SpendWiseTheme.colors.bg.luminance() < 0.5f) dark else light
}
