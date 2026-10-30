package com.spendwise.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.spendwise.app.R

// shortcut: no Inter Medium file is bundled (Android Studio ships only Regular and SemiBold), so Medium renders as
// Regular. Drop inter_medium.otf in res/font and point the Medium entry at it.
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_regular, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, weight: FontWeight, line: Int? = null, tracking: Double = 0.0) = TextStyle(
    fontFamily = Inter,
    fontSize = size.sp,
    lineHeight = (line ?: (size * 1.4).toInt()).sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
    fontFeatureSettings = "tnum",
)

/** The redesign's type scale. Overline is drawn uppercase by its caller (`text.uppercase()`). */
object SwType {
    val display = style(44, FontWeight.SemiBold, 48, -0.03)
    val title = style(28, FontWeight.SemiBold, 32, -0.02)
    val stat = style(20, FontWeight.SemiBold, tracking = -0.01)
    val heading = style(17, FontWeight.SemiBold, tracking = -0.01)
    val body = style(15, FontWeight.Normal, 21)
    val bodyStrong = style(15, FontWeight.Medium, 21)
    val label = style(13, FontWeight.Normal)
    val labelStrong = style(13, FontWeight.Medium)
    val caption = style(12, FontWeight.Normal)
    val overline = style(11, FontWeight.Medium, tracking = 0.06)
    val micro = style(11, FontWeight.Normal)
}

// Material components still on screen read this
val Typography = Typography(
    displayLarge = SwType.display, displayMedium = SwType.display, displaySmall = SwType.title,
    headlineLarge = SwType.title, headlineMedium = SwType.title, headlineSmall = SwType.stat,
    titleLarge = SwType.heading, titleMedium = SwType.heading, titleSmall = SwType.bodyStrong,
    bodyLarge = SwType.body, bodyMedium = SwType.body, bodySmall = SwType.label,
    labelLarge = SwType.bodyStrong, labelMedium = SwType.labelStrong, labelSmall = SwType.caption,
)
