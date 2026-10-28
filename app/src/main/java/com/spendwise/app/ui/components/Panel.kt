package com.spendwise.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** For rows drawn by a Material component, so they match a Panel. */
val PanelShape = RoundedCornerShape(16.dp)

/** A flat filled block that groups one section: no border, shadow or tint (CLAUDE.md design rules). */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tap = if (onClick == null) Modifier else Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
    Column(
        modifier.fillMaxWidth().clip(PanelShape).background(MaterialTheme.colorScheme.surfaceContainerLow).then(tap).padding(16.dp),
        content = content,
    )
}
