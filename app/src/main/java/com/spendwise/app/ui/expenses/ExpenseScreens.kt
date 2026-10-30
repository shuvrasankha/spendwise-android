package com.spendwise.app.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spendwise.app.domain.PAYMENT_METHODS
import com.spendwise.app.domain.isCard
import com.spendwise.app.ui.components.ButtonStyle
import com.spendwise.app.ui.components.DateChips
import com.spendwise.app.ui.components.FieldLabel
import com.spendwise.app.ui.components.SettingsGroup
import com.spendwise.app.ui.components.SettingsRow
import com.spendwise.app.ui.components.SubPageTopBar
import com.spendwise.app.ui.components.SwButton
import com.spendwise.app.ui.components.SwChip
import com.spendwise.app.ui.components.SwField
import com.spendwise.app.ui.components.Trailing
import com.spendwise.app.ui.icons.SwIcons
import com.spendwise.app.ui.theme.Spacing
import com.spendwise.app.ui.theme.SpendWiseTheme
import com.spendwise.app.ui.theme.SwType
import java.time.LocalDate

/** Edit an existing expense (spec §2.3): the same fields as the New entry sheet, Save, and Delete with confirmation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditExpenseScreen(onDone: () -> Unit, editor: ExpenseEditorViewModel = hiltViewModel()) {
    val state by editor.state.collectAsStateWithLifecycle()
    val currency by editor.currency.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    val c = SpendWiseTheme.colors
    val gutter = Modifier.padding(horizontal = Spacing.gutter)
    val f = state.form

    Scaffold(contentWindowInsets = WindowInsets(0), containerColor = c.bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            SubPageTopBar("Edit expense", onDone)
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = c.text) }
                state.missing -> Text("This expense was deleted or couldn't be loaded.", style = SwType.body, color = c.textMuted, modifier = Modifier.padding(Spacing.gutter))
                else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(gutter) {
                        SwField(
                            f.amount, editor::setAmount, "Amount", prefix = currency.symbol,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        )
                    }
                    FieldLabel("Date")
                    DateChips(f.date, LocalDate.now(), editor::setDate)
                    FieldLabel("Category")
                    FlowRow(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.tiles.forEach { (category, label) -> SwChip(label, f.category == category, { editor.setCategory(category) }, leading = SwIcons.forCategory(category)) }
                    }
                    Box(gutter) {
                        SwField(
                            f.description, editor::setDescription, "Description",
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                        )
                    }
                    FieldLabel("Paid with")
                    FlowRow(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PAYMENT_METHODS.forEach { SwChip(it, f.payment == it, { editor.setPayment(it) }) }
                    }
                    if (isCard(f.payment)) Box(gutter) { SwField(f.cardName, editor::setCardName, "Card name (optional)") }
                    Box(gutter) {
                        SwField(
                            f.notes, editor::setNotes, "Notes (optional)", singleLine = false,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                    }
                    SettingsGroup(null) { SettingsRow("Repeats every month", trailing = Trailing.Switch(f.repeat, editor::setRepeat)) }
                    Text("Rent, subscriptions, SIPs: Home offers it again when it's due.", style = SwType.label, color = c.textMuted, modifier = gutter)
                    Box(gutter) {
                        SwField(
                            f.tagDraft, editor::setTagDraft, "Tags (optional): comma or space",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { editor.commitTagDraft() }),
                        )
                    }
                    if (f.tags.isNotEmpty()) {
                        FlowRow(gutter, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            f.tags.forEach { tag -> SwChip("#$tag", true, { editor.removeTag(tag) }, leading = SwIcons.Close) }
                        }
                    }
                    state.message?.let { Text(it.text, style = SwType.label, color = if (it.success) c.positive else c.negative, modifier = gutter) }
                    Row(gutter, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SwButton("Delete", editor::askDelete, style = ButtonStyle.Danger)
                        SwButton("Save", editor::save, Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = editor::cancelDelete,
            title = { Text("Delete this expense?") },
            text = { Text("It will be removed from SpendWise on all your devices.") },
            confirmButton = { TextButton(onClick = editor::confirmDelete) { Text("Delete", color = c.negative) } },
            dismissButton = { TextButton(onClick = editor::cancelDelete) { Text("Cancel") } },
        )
    }
}
