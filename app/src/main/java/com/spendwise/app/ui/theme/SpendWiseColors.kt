package com.spendwise.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** The redesign's tokens. Accent only on the primary button, the + button, budget progress and the current period. */
@Immutable
data class SpendWiseColors(
    val bg: Color,
    val surface: Color,
    val line: Color,
    val text: Color,
    val textMuted: Color,
    val textFaint: Color,
    val accent: Color,
    val onAccent: Color,
    val positive: Color,
    val negative: Color,
)

val DarkSpendWiseColors = SpendWiseColors(
    bg = Color(0xFF0A0A0B), surface = Color(0xFF161618), line = Color(0xFF26262A),
    text = Color(0xFFF5F5F6), textMuted = Color(0xFF8E8E96), textFaint = Color(0xFF66666E),
    accent = Color(0xFFF2B33D), onAccent = Color(0xFF111111),
    positive = Color(0xFF4ADE80), negative = Color(0xFFF87171),
)

val LightSpendWiseColors = SpendWiseColors(
    bg = Color(0xFFFFFFFF), surface = Color(0xFFF4F4F6), line = Color(0xFFE4E4E8),
    text = Color(0xFF0E0E10), textMuted = Color(0xFF62626A), textFaint = Color(0xFF9A9AA2),
    accent = Color(0xFFE0A11B), onAccent = Color(0xFF111111),
    positive = Color(0xFF15803D), negative = Color(0xFFDC2626),
)

val LocalSpendWiseColors = staticCompositionLocalOf { LightSpendWiseColors }
