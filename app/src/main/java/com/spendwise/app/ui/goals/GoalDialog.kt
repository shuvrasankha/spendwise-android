package com.spendwise.app.ui.goals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.spendwise.app.ui.budget.MoneyField
import com.spendwise.app.ui.dashboard.GoalEditing
import com.spendwise.app.ui.dashboard.Picker
import com.spendwise.app.ui.invest.plain
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** The goal dialog (goals.js): "Add to …", "Edit goal" or "New savings goal", and delete with confirmation. */
@Composable
fun GoalDialog(
    editing: GoalEditing,
    currency: Currency,
    error: String?,
    onSave: (name: String, target: String, by: String, saved: String, amount: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val goal = editing.goal
    var name by remember { mutableStateOf(goal?.name.orEmpty()) }
    var target by remember { mutableStateOf(goal?.target?.let(::plain).orEmpty()) }
    var saved by remember { mutableStateOf(goal?.saved?.takeIf { it > 0 }?.let(::plain).orEmpty()) }
    var amount by remember { mutableStateOf("") }
    val now = YearMonth.now()
    var by by remember { mutableStateOf(goal?.by?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: now) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete && goal != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete \"${goal.name}\"?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete goal", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing.adding) "Add to ${goal?.name}" else if (goal != null) "Edit goal" else "New savings goal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editing.adding) {
                    MoneyField("Amount put aside", amount, currency) { amount = it }
                } else {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("What it's for") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    MoneyField("Amount", target, currency) { target = it }
                    Text("By", style = MaterialTheme.typography.titleSmall)
                    // The web's month input starts at this month; an older goal keeps its own year in the list
                    val years = (minOf(now.year, by.year)..now.year + 30).toList()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Picker(by.month.getDisplayName(TextStyle.SHORT, Locale.US), Month.entries.map { it to it.getDisplayName(TextStyle.FULL, Locale.US) }) {
                            by = by.withMonth(it.value)
                        }
                        Picker(by.year.toString(), years.map { it to it.toString() }) { by = by.withYear(it) }
                    }
                    MoneyField("Saved so far", saved, currency, placeholder = "0") { saved = it }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, target, by.toString(), saved, amount) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (!editing.adding && goal != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete goal") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
