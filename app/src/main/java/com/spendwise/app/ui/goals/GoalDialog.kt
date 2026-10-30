package com.spendwise.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.spendwise.app.domain.Currency
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.FieldLabel
import com.spendwise.app.ui.components.MenuChip
import com.spendwise.app.ui.components.MoneyField
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.components.SwSheet
import com.spendwise.app.ui.dashboard.GoalEditing
import com.spendwise.app.ui.invest.plain
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** The goal sheet (goals.js): "Add to …", "Edit goal" or "New savings goal", and delete with confirmation. */
@Composable
fun GoalDialog(
    editing: GoalEditing,
    currency: Currency,
    error: String?,
    onSave: (name: String, target: String, by: String, saved: String, amount: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = SpendWiseTheme.colors
    val goal = editing.goal
    var name by remember { mutableStateOf(goal?.name.orEmpty()) }
    var target by remember { mutableStateOf(goal?.target?.let(::plain).orEmpty()) }
    var saved by remember { mutableStateOf(goal?.saved?.takeIf { it > 0 }?.let(::plain).orEmpty()) }
    var amount by remember { mutableStateOf("") }
    val now = YearMonth.now()
    var by by remember { mutableStateOf(goal?.by?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: now) }
    var confirmDelete by remember { mutableStateOf(false) }
    val gutter = Modifier.padding(horizontal = Spacing.gutter)

    if (confirmDelete && goal != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${goal.name}\"?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete goal", color = c.negative) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }

    SwSheet(
        if (editing.adding) "Add to ${goal?.name}" else if (goal != null) "Edit goal" else "New savings goal", onDismiss,
        footer = {
            error?.let { Text(it, style = SwType.label, color = c.negative, modifier = gutter) }
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!editing.adding && goal != null) SwButton("Delete", { confirmDelete = true }, style = ButtonStyle.Danger)
                SwButton("Save", { onSave(name, target, by.toString(), saved, amount) }, Modifier.weight(1f))
            }
        },
    ) {
        if (editing.adding) {
            Box(gutter) { MoneyField("Amount put aside", amount, currency.symbol) { amount = it } }
        } else {
            Box(gutter) { SwField(name, { name = it.take(40) }, "What it's for") }
            Box(gutter) { MoneyField("Amount", target, currency.symbol) { target = it } }
            FieldLabel("By")
            // The web's month input starts at this month; an older goal keeps its own year in the list
            val years = (minOf(now.year, by.year)..now.year + 30).toList()
            Row(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MenuChip(by.month.getDisplayName(TextStyle.FULL, Locale.US), Month.entries.map { it to it.getDisplayName(TextStyle.FULL, Locale.US) }, { by = by.withMonth(it.value) })
                MenuChip(by.year.toString(), years.map { it to it.toString() }, { by = by.withYear(it) })
            }
            Box(gutter) { MoneyField("Saved so far", saved, currency.symbol, placeholder = "0") { saved = it } }
        }
    }
}
