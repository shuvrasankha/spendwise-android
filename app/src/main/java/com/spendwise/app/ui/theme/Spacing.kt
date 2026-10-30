package com.spendwise.app.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** 4dp grid. */
object Spacing {
    val gutter = 20.dp
    val section = 32.dp
    val rowV = 12.dp
}

object Radius {
    val block = RoundedCornerShape(16.dp)
    val control = RoundedCornerShape(12.dp)
    val segment = RoundedCornerShape(9.dp)
    val tile = RoundedCornerShape(10.dp)
    val pill = CircleShape
}
