package com.spendwise.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The first letter of the name (or email), like the web's avatar without a photo (page-shell.js › updateUserAvatar). */
@Composable
fun Avatar(name: String, size: Dp = 40.dp) = Box(
    Modifier.size(size).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
    contentAlignment = Alignment.Center,
) {
    Text(name.ifBlank { "U" }.take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
}
