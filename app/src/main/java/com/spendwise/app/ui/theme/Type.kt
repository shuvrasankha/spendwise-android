package com.spendwise.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// One typeface (the system sans), two weights: Normal for reading, SemiBold for titles, labels and figures.
private fun TextStyle.weight(weight: FontWeight) = copy(fontFamily = FontFamily.Default, fontWeight = weight)

val Typography = Typography().run {
    copy(
        displayLarge = displayLarge.weight(FontWeight.Normal),
        displayMedium = displayMedium.weight(FontWeight.Normal),
        displaySmall = displaySmall.weight(FontWeight.Normal),
        headlineLarge = headlineLarge.weight(FontWeight.SemiBold),
        headlineMedium = headlineMedium.weight(FontWeight.SemiBold),
        headlineSmall = headlineSmall.weight(FontWeight.SemiBold),
        titleLarge = titleLarge.weight(FontWeight.SemiBold),
        titleMedium = titleMedium.weight(FontWeight.SemiBold),
        titleSmall = titleSmall.weight(FontWeight.SemiBold),
        bodyLarge = bodyLarge.weight(FontWeight.Normal),
        bodyMedium = bodyMedium.weight(FontWeight.Normal),
        bodySmall = bodySmall.weight(FontWeight.Normal),
        labelLarge = labelLarge.weight(FontWeight.SemiBold),
        labelMedium = labelMedium.weight(FontWeight.SemiBold),
        labelSmall = labelSmall.weight(FontWeight.SemiBold),
    )
}
