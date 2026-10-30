package com.spendwise.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Radius
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType

/** Selected is always inverted: `text` fill, `bg` content. */
@Composable
fun SwChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, leading: ImageVector? = null) {
    val c = SpendWiseTheme.colors
    val content = if (selected) c.bg else c.text
    Row(
        modifier.heightIn(min = 36.dp).clip(Radius.pill)
            .then(if (selected) Modifier.background(c.text) else Modifier.border(1.dp, c.line, Radius.pill))
            .clickable(role = Role.Button, onClick = onClick).semantics { this.selected = selected }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leading != null) Icon(leading, null, Modifier.size(16.dp), tint = content)
        Text(label, style = SwType.labelStrong, color = content)
    }
}

@Composable
fun SegmentedControl(items: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = SpendWiseTheme.colors
    Row(modifier.fillMaxWidth().clip(Radius.control).background(c.surface).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                Modifier.weight(1f).heightIn(min = 36.dp).clip(Radius.segment)
                    .then(if (on) Modifier.background(c.text) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(i) }.semantics { selected = on },
                contentAlignment = Alignment.Center,
            ) { Text(label, style = SwType.labelStrong, color = if (on) c.bg else c.textMuted) }
        }
    }
}

enum class ButtonStyle { Primary, Secondary, Text, Danger }

@Composable
fun SwButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    enabled: Boolean = true,
    /** 40dp instead of 52dp, for actions inside a row. */
    compact: Boolean = false,
    leading: ImageVector? = null,
) {
    val c = SpendWiseTheme.colors
    val size = Modifier.heightIn(min = if (compact) 40.dp else 52.dp)
    val padding = PaddingValues(horizontal = if (compact) 14.dp else 20.dp)
    val label: @Composable (androidx.compose.ui.graphics.Color) -> Unit = { color ->
        if (leading != null) {
            Icon(leading, null, Modifier.size(18.dp), tint = color)
            Box(Modifier.size(8.dp))
        }
        Text(text, style = SwType.bodyStrong, color = color)
    }
    when (style) {
        ButtonStyle.Primary -> Button(
            onClick, modifier.then(size), enabled, Radius.control,
            ButtonDefaults.buttonColors(c.accent, c.onAccent, c.line, c.textFaint), contentPadding = padding,
        ) { label(if (enabled) c.onAccent else c.textFaint) }
        ButtonStyle.Secondary -> OutlinedButton(
            onClick, modifier.then(size), enabled, Radius.control,
            ButtonDefaults.outlinedButtonColors(contentColor = c.text, disabledContentColor = c.textFaint),
            border = BorderStroke(1.dp, c.line), contentPadding = padding,
        ) { label(if (enabled) c.text else c.textFaint) }
        ButtonStyle.Text -> TextButton(onClick, modifier.then(size), enabled, Radius.control, contentPadding = padding) {
            label(if (enabled) c.text else c.textFaint)
        }
        ButtonStyle.Danger -> TextButton(onClick, modifier.then(size), enabled, Radius.control, contentPadding = padding) {
            label(if (enabled) c.negative else c.textFaint)
        }
    }
}

@Composable
fun SwField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    supportingText: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    prefix: String? = null,
) {
    val c = SpendWiseTheme.colors
    OutlinedTextField(
        value, onValueChange, modifier.fillMaxWidth(),
        textStyle = SwType.body.copy(color = c.text),
        placeholder = { Text(placeholder, style = SwType.body, color = c.textFaint) },
        leadingIcon = leading?.let { { Icon(it, null, Modifier.size(18.dp), tint = c.textMuted) } },
        trailingIcon = trailing,
        prefix = prefix?.let { { Text("$it ", style = SwType.body, color = c.textMuted) } },
        singleLine = singleLine, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions, visualTransformation = visualTransformation,
        isError = isError,
        supportingText = supportingText?.let { { Text(it, style = SwType.caption, color = if (isError) c.negative else c.textMuted) } },
        shape = Radius.control,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = c.text, unfocusedTextColor = c.text,
            focusedBorderColor = c.text, unfocusedBorderColor = c.line, errorBorderColor = c.negative,
            cursorColor = c.text, errorCursorColor = c.negative,
        ),
    )
}

@Composable
fun SwSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val c = SpendWiseTheme.colors
    Switch(
        checked, onCheckedChange, modifier,
        colors = SwitchDefaults.colors(
            checkedTrackColor = c.text, checkedThumbColor = c.bg, checkedBorderColor = c.text,
            uncheckedTrackColor = c.line, uncheckedThumbColor = c.textMuted, uncheckedBorderColor = c.line,
        ),
    )
}

/** A tappable date box that looks like a [SwField]. */
@Composable
fun DateField(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    val c = SpendWiseTheme.colors
    Row(
        modifier.heightIn(min = 56.dp).clip(Radius.control).border(1.dp, c.line, Radius.control)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(SwIcons.Calendar, null, Modifier.size(18.dp), tint = c.textMuted)
        Column {
            Text(label, style = SwType.caption, color = c.textMuted)
            Text(value, style = SwType.body, color = c.text)
        }
    }
}

/** A labelled amount field with the currency symbol in front. */
@Composable
fun MoneyField(label: String, value: String, symbol: String, placeholder: String = "0", onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = SwType.label, color = SpendWiseTheme.colors.textMuted)
        SwField(value, onChange, placeholder, prefix = symbol, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    }
}

/** A chip showing [current] that opens a menu of [options] (value to label). */
@Composable
fun <T> MenuChip(current: String, options: List<Pair<T, String>>, onPick: (T) -> Unit, leading: ImageVector? = SwIcons.ChevronDown) {
    var open by remember { mutableStateOf(false) }
    Box {
        SwChip(current, false, { open = true }, leading = leading)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, label) -> DropdownMenuItem(text = { Text(label) }, onClick = { open = false; onPick(value) }) }
        }
    }
}

@DarkLight
@Composable
private fun ControlsPreview() = PreviewSurface {
    var seg = 0
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SwChip("Filters", false, {}, leading = SwIcons.Filters)
            SwChip("Food", true, {})
            SwChip("This month", false, {})
        }
        SegmentedControl(listOf("Expense", "Income", "Debt", "Invest"), seg, { seg = it })
        SwButton("Save expense", {})
        SwButton("Mark as paid", {}, style = ButtonStyle.Secondary)
        Row {
            SwButton("Remind", {}, style = ButtonStyle.Text, compact = true)
            SwButton("Delete", {}, style = ButtonStyle.Danger, compact = true)
        }
        SwField("", {}, "Search", leading = SwIcons.Search)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SwSwitch(true, {})
            SwSwitch(false, {})
        }
    }
}
